package com.pontocafe.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.tflite.java.TfLite
import com.pontocafe.app.data.FaceEmbeddingIntegrity
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.hypot
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tensorflow.lite.InterpreterApi
import org.tensorflow.lite.InterpreterApi.Options.TfLiteRuntime

/**
 * FaceNet compatível com todas as biometrias já cadastradas.
 *
 * Regra crítica: o embedding canônico usa exatamente o mesmo runtime CPU,
 * recorte, prewhitening e normalização das versões que geraram os templates
 * existentes. Otimizações nunca podem mudar esse espaço vetorial.
 */
class LiteRtFaceEmbeddingEngine(
    private val context: Context,
    private val assetName: String = MODEL_ASSET,
) : FaceEmbeddingEngine {

    private val initMutex = Mutex()
    private val inferenceMutex = Mutex()
    private val modelAssetAvailable by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        runCatching { context.assets.openFd(assetName).close() }.isSuccess
    }
    private val inferenceWorkspace by lazy(LazyThreadSafetyMode.NONE) { InferenceWorkspace() }

    @Volatile
    private var interpreter: InterpreterApi? = null

    @Volatile
    private var inferencePrimed: Boolean = false

    override val isReady: Boolean
        get() = modelAssetAvailable

    override val modelName: String = "FaceNet 128D · LiteRT"
    override val modelVersion: String = MODEL_VERSION

    override suspend fun warmUp() {
        if (!isReady || inferencePrimed) return
        withContext(Dispatchers.Default) {
            val runtime = getInterpreter()
            inferenceMutex.withLock {
                if (inferencePrimed) return@withLock
                val workspace = inferenceWorkspace
                workspace.input.clear()
                while (workspace.input.hasRemaining()) workspace.input.putFloat(0f)
                workspace.input.rewind()
                workspace.output[0].fill(0f)
                runtime.run(workspace.input, workspace.output)
                inferencePrimed = true
            }
        }
    }

    /**
     * Caminho canônico do cadastro e primeira tentativa do Ponto.
     * Mantido byte-a-byte equivalente na preparação de entrada ao pipeline
     * anterior, incluindo CPU/XNNPACK com 2 threads.
     */
    override suspend fun embed(frame: FaceFrame): FloatArray = withContext(Dispatchers.Default) {
        val source = frame.bitmap
        var cropped: Bitmap? = null
        try {
            validateFrame(frame, FaceCapturePurpose.ENROLLMENT)
            FaceImageQualityAnalyzer.requireAcceptableFrame(source, frame.faceBounds)
            val currentCrop = crop(source, canonicalRect(source, frame.faceBounds))
            cropped = currentCrop
            embedBitmap(currentCrop)
        } finally {
            cropped?.takeIf { it !== source && !it.isRecycled }?.recycle()
            if (!source.isRecycled) source.recycle()
        }
    }

    /**
     * Identificação adaptativa progressiva usando UMA única captura.
     *
     * 1. executa o embedding canônico compatível;
     * 2. se ele já reconheceu, termina imediatamente;
     * 3. somente em caso de miss calcula recortes alternativos do mesmo frame.
     *
     * Nenhum fallback altera limiar, margem, FaceNet ou liveness.
     */
    override suspend fun embedForIdentification(
        frame: FaceFrame,
        shouldContinue: (embedding: FloatArray, candidateIndex: Int) -> Boolean,
    ): List<FloatArray> = withContext(Dispatchers.Default) {
        val source = frame.bitmap
        val candidates = ArrayList<FloatArray>(MAX_IDENTIFICATION_CANDIDATES)
        val usedRects = LinkedHashSet<Rect>(MAX_IDENTIFICATION_CANDIDATES)

        try {
            validateFrame(frame, FaceCapturePurpose.IDENTIFICATION)
            FaceImageQualityAnalyzer.requireAcceptableFrame(source, frame.faceBounds)
            val primaryRect = canonicalRect(source, frame.faceBounds)
            usedRects += primaryRect
            val primary = requireNotNull(embedRect(source, primaryRect, required = true))
            candidates += primary
            if (!shouldContinue(primary, 0)) return@withContext candidates

            val tightRect = faceRect(
                bitmap = source,
                bounds = frame.faceBounds,
                horizontalMargin = 0.10f,
                topMargin = 0.02f,
                bottomMargin = 0.14f,
            )
            if (usedRects.add(tightRect)) {
                embedRect(source, tightRect, required = false)?.let { tight ->
                    candidates += tight
                    if (!shouldContinue(tight, candidates.lastIndex)) return@withContext candidates
                }
            }

            landmarkAnchoredRect(source, frame)?.let { landmarkRect ->
                if (candidates.size < MAX_IDENTIFICATION_CANDIDATES && usedRects.add(landmarkRect)) {
                    embedAlignedLandmarkRect(source, landmarkRect, frame)?.let { anchored ->
                        candidates += anchored
                        if (!shouldContinue(anchored, candidates.lastIndex)) return@withContext candidates
                    }
                }
            }

            candidates
        } finally {
            if (!source.isRecycled) source.recycle()
        }
    }

    private fun validateFrame(frame: FaceFrame, purpose: FaceCapturePurpose) {
        if (!isReady) throw FaceModelUnavailableException()
        check(!frame.bitmap.isRecycled) { "O frame facial já foi liberado." }
        require(FaceCapturePolicy.evaluate(frame.observation.toCaptureFacts(), purpose) == null) {
            "O frame nao contem um unico rosto integro e bem posicionado."
        }
        require(frame.faceBounds.width() > 0 && frame.faceBounds.height() > 0) { "Area facial invalida." }
    }

    private suspend fun embedRect(source: Bitmap, rect: Rect, required: Boolean): FloatArray? {
        var cropped: Bitmap? = null
        return try {
            val currentCrop = crop(source, rect)
            cropped = currentCrop
            if (required) {
                embedBitmap(currentCrop)
            } else {
                runCatching { embedBitmap(currentCrop) }.getOrNull()
            }
        } finally {
            cropped?.takeIf { it !== source && !it.isRecycled }?.recycle()
        }
    }

    /**
     * Optional identification fallback: the eye line is made horizontal after
     * landmark-anchored cropping. The canonical enrollment/identification crop
     * above is intentionally untouched for compatibility with existing faces.
     */
    private suspend fun embedAlignedLandmarkRect(
        source: Bitmap,
        rect: Rect,
        frame: FaceFrame,
    ): FloatArray? {
        val leftEye = frame.leftEye ?: return null
        val rightEye = frame.rightEye ?: return null
        val angleDegrees = Math.toDegrees(
            atan2(
                (rightEye.y - leftEye.y).toDouble(),
                (rightEye.x - leftEye.x).toDouble(),
            ),
        ).toFloat()
        if (!angleDegrees.isFinite() || kotlin.math.abs(angleDegrees) > 12f) return null

        var cropped: Bitmap? = null
        var aligned: Bitmap? = null
        return try {
            cropped = crop(source, rect)
            val matrix = Matrix().apply { postRotate(-angleDegrees) }
            val currentCrop = requireNotNull(cropped)
            aligned = Bitmap.createBitmap(
                currentCrop,
                0,
                0,
                currentCrop.width,
                currentCrop.height,
                matrix,
                true,
            )
            runCatching { embedBitmap(requireNotNull(aligned)) }.getOrNull()
        } finally {
            aligned?.takeIf { it !== cropped && !it.isRecycled }?.recycle()
            cropped?.takeIf { !it.isRecycled }?.recycle()
        }
    }

    private suspend fun embedBitmap(face: Bitmap): FloatArray {
        var resized: Bitmap? = null
        try {
            // Mantemos sempre a mesma operação de resize usada na versão estável.
            val scaled = Bitmap.createScaledBitmap(face, INPUT_SIZE, INPUT_SIZE, true)
            resized = scaled
            FaceImageQualityAnalyzer.requireAcceptable(scaled)

            val runtime = getInterpreter()
            val output = inferenceMutex.withLock {
                val workspace = inferenceWorkspace
                val input = toStandardizedBuffer(scaled, workspace)
                workspace.output[0].fill(0f)
                input.rewind()
                runtime.run(input, workspace.output)
                inferencePrimed = true
                workspace.output[0].copyOf()
            }
            return l2Normalize(output)
        } finally {
            resized?.takeIf { it !== face && !it.isRecycled }?.recycle()
        }
    }

    private suspend fun getInterpreter(): InterpreterApi {
        interpreter?.let { return it }
        return initMutex.withLock {
            interpreter?.let { return@withLock it }
            withContext(Dispatchers.IO) {
                Tasks.await(TfLite.initialize(context.applicationContext))
                val options = InterpreterApi.Options()
                    .setRuntime(TfLiteRuntime.FROM_SYSTEM_ONLY)
                    .setNumThreads(CPU_THREADS)
                InterpreterApi.create(loadModelBuffer(), options).also { interpreter = it }
            }
        }
    }

    private fun loadModelBuffer(): ByteBuffer {
        val descriptor = context.assets.openFd(assetName)
        return descriptor.use { asset ->
            FileInputStream(asset.fileDescriptor).use { stream ->
                stream.channel.map(
                    java.nio.channels.FileChannel.MapMode.READ_ONLY,
                    asset.startOffset,
                    asset.declaredLength,
                )
            }
        }
    }

    private fun canonicalRect(bitmap: Bitmap, bounds: Rect): Rect = faceRect(
        bitmap = bitmap,
        bounds = bounds,
        horizontalMargin = FACE_MARGIN,
        topMargin = FACE_MARGIN,
        bottomMargin = FACE_MARGIN,
    )

    private fun faceRect(
        bitmap: Bitmap,
        bounds: Rect,
        horizontalMargin: Float,
        topMargin: Float,
        bottomMargin: Float,
    ): Rect {
        val extraX = (bounds.width() * horizontalMargin).toInt()
        val extraTop = (bounds.height() * topMargin).toInt()
        val extraBottom = (bounds.height() * bottomMargin).toInt()
        val left = max(0, bounds.left - extraX)
        val top = max(0, bounds.top - extraTop)
        val right = min(bitmap.width, bounds.right + extraX)
        val bottom = min(bitmap.height, bounds.bottom + extraBottom)
        require(right > left && bottom > top) { "Área facial inválida." }
        return Rect(left, top, right, bottom)
    }

    /**
     * Fallback ancorado nos olhos. Não substitui o recorte canônico.
     */
    private fun landmarkAnchoredRect(bitmap: Bitmap, frame: FaceFrame): Rect? {
        val leftEye = frame.leftEye ?: return null
        val rightEye = frame.rightEye ?: return null
        val eyeDistance = hypot(
            (rightEye.x - leftEye.x).toDouble(),
            (rightEye.y - leftEye.y).toDouble(),
        ).toFloat()
        if (!eyeDistance.isFinite() || eyeDistance < 12f) return null

        val centerX = (leftEye.x + rightEye.x) / 2f
        val eyeY = (leftEye.y + rightEye.y) / 2f
        val halfWidth = eyeDistance * 1.42f
        val top = eyeY - eyeDistance * 0.92f
        val bottom = eyeY + eyeDistance * 2.08f

        val rect = Rect(
            max(0, (centerX - halfWidth).toInt()),
            max(0, top.toInt()),
            min(bitmap.width, (centerX + halfWidth).toInt()),
            min(bitmap.height, bottom.toInt()),
        )
        if (rect.width() < 32 || rect.height() < 32) return null
        return rect
    }

    private fun crop(bitmap: Bitmap, rect: Rect): Bitmap =
        Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height())

    /**
     * Prewhitening original do projeto. Não "otimizar" esta matemática sem uma
     * migração biométrica explícita: pequenas diferenças podem alterar scores.
     */
    private fun toStandardizedBuffer(bitmap: Bitmap, workspace: InferenceWorkspace): ByteBuffer {
        val pixels = workspace.pixels
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        val raw = workspace.raw
        var offset = 0
        var sum = 0.0
        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF).toFloat()
            val g = ((pixel shr 8) and 0xFF).toFloat()
            val b = (pixel and 0xFF).toFloat()
            raw[offset++] = r
            raw[offset++] = g
            raw[offset++] = b
            sum += r + g + b
        }

        val mean = (sum / raw.size).toFloat()
        var squared = 0.0
        for (value in raw) {
            val delta = value - mean
            squared += delta * delta
        }
        val calculatedStd = sqrt(squared / raw.size).toFloat()
        val minimumStd = 1f / sqrt(raw.size.toFloat())
        val std = max(calculatedStd, minimumStd)

        return workspace.input.apply {
            clear()
            raw.forEach { putFloat((it - mean) / std) }
            rewind()
        }
    }

    private fun l2Normalize(values: FloatArray): FloatArray {
        require(values.size == EMBEDDING_SIZE && values.all { it.isFinite() }) {
            "A saida do modelo facial e invalida."
        }
        var sumSquares = 0.0
        for (value in values) sumSquares += value * value
        val norm = sqrt(sumSquares).toFloat()
        require(norm.isFinite() && norm > 1e-12f) { "A saida do modelo facial possui norma zero." }
        for (index in values.indices) values[index] /= norm
        return FaceEmbeddingIntegrity.requireValid(values, EMBEDDING_SIZE)
    }

    private class InferenceWorkspace {
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        val raw = FloatArray(pixels.size * 3)
        val input: ByteBuffer = ByteBuffer.allocateDirect(raw.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
        val output: Array<FloatArray> = arrayOf(FloatArray(EMBEDDING_SIZE))
    }

    companion object {
        const val MODEL_ASSET = "facenet.tflite"
        const val MODEL_VERSION = "facenet-128d-160-v1"
        const val INPUT_SIZE = 160
        const val EMBEDDING_SIZE = 128
        const val FACE_MARGIN = 0.18f
        private const val CPU_THREADS = 2
        private const val MAX_IDENTIFICATION_CANDIDATES = 3
    }
}
