package com.pontocafe.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.tflite.java.TfLite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tensorflow.lite.InterpreterApi
import org.tensorflow.lite.InterpreterApi.Options.TfLiteRuntime
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class LiteRtFaceEmbeddingEngine(
    private val context: Context,
    private val assetName: String = MODEL_ASSET,
) : FaceEmbeddingEngine {

    private val initMutex = Mutex()
    private val inferenceMutex = Mutex()

    @Volatile
    private var interpreter: InterpreterApi? = null

    override val isReady: Boolean
        get() = runCatching { context.assets.openFd(assetName).close() }.isSuccess

    override val modelName: String = "FaceNet 128D · LiteRT"
    override val modelVersion: String = MODEL_VERSION

    override suspend fun warmUp() {
        if (!isReady) return
        getInterpreter()
    }

    /**
     * Caminho canônico do cadastro biométrico. Este recorte permanece idêntico
     * ao usado nas versões anteriores para manter compatibilidade integral com
     * todos os embeddings já cadastrados.
     */
    override suspend fun embed(frame: FaceFrame): FloatArray = withContext(Dispatchers.Default) {
        validateFrame(frame)
        val source = frame.bitmap
        var cropped: Bitmap? = null
        try {
            cropped = crop(source, canonicalRect(source, frame.faceBounds))
            embedBitmap(cropped)
        } finally {
            cropped?.takeIf { it !== source && !it.isRecycled }?.recycle()
            if (!source.isRecycled) source.recycle()
        }
    }

    /**
     * Identificação adaptativa com uma única foto.
     *
     * O primeiro embedding é sempre o canônico. Os demais usam somente recortes
     * alternativos do MESMO frame para reduzir a influência de cabelo, touca e
     * pequenas variações do bounding-box. FaceNet, normalização, liveness,
     * limiar e margem não são alterados.
     */
    override suspend fun embedForIdentification(frame: FaceFrame): List<FloatArray> =
        withContext(Dispatchers.Default) {
            validateFrame(frame)
            val source = frame.bitmap
            val candidates = ArrayList<FloatArray>(MAX_IDENTIFICATION_CANDIDATES)
            val usedRects = LinkedHashSet<Rect>(MAX_IDENTIFICATION_CANDIDATES)

            try {
                val primaryRect = canonicalRect(source, frame.faceBounds)
                usedRects += primaryRect
                candidates += requireNotNull(embedRect(source, primaryRect, required = true))

                val tightRect = faceRect(
                    bitmap = source,
                    bounds = frame.faceBounds,
                    horizontalMargin = 0.10f,
                    topMargin = 0.02f,
                    bottomMargin = 0.14f,
                )
                if (usedRects.add(tightRect)) {
                    embedRect(source, tightRect, required = false)?.let(candidates::add)
                }

                landmarkAnchoredRect(source, frame)?.let { landmarkRect ->
                    if (candidates.size < MAX_IDENTIFICATION_CANDIDATES && usedRects.add(landmarkRect)) {
                        embedRect(source, landmarkRect, required = false)?.let(candidates::add)
                    }
                }

                candidates
            } finally {
                if (!source.isRecycled) source.recycle()
            }
        }

    private fun validateFrame(frame: FaceFrame) {
        if (!isReady) throw FaceModelUnavailableException()
        check(!frame.bitmap.isRecycled) { "O frame facial já foi liberado." }
    }

    private suspend fun embedRect(source: Bitmap, rect: Rect, required: Boolean): FloatArray? {
        var cropped: Bitmap? = null
        return try {
            cropped = crop(source, rect)
            if (required) {
                embedBitmap(cropped)
            } else {
                runCatching { embedBitmap(cropped) }.getOrNull()
            }
        } finally {
            cropped?.takeIf { it !== source && !it.isRecycled }?.recycle()
        }
    }

    private suspend fun embedBitmap(face: Bitmap): FloatArray {
        var resized: Bitmap? = null
        try {
            resized = Bitmap.createScaledBitmap(face, INPUT_SIZE, INPUT_SIZE, true)
            FaceImageQualityAnalyzer.requireAcceptable(resized)

            val input = toStandardizedBuffer(resized)
            val output = Array(1) { FloatArray(EMBEDDING_SIZE) }
            val runtime = getInterpreter()

            inferenceMutex.withLock {
                runtime.run(input, output)
            }
            return l2Normalize(output[0])
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
                    .setNumThreads(2)
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
     * Recorte estável ancorado nos olhos. Ele evita que cabelo ou cobertura da
     * cabeça desloquem excessivamente a região enviada ao FaceNet. Só é usado
     * como fallback; o embedding canônico continua sendo a primeira tentativa.
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

    private fun toStandardizedBuffer(bitmap: Bitmap): ByteBuffer {
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        val raw = FloatArray(pixels.size * 3)
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

        return ByteBuffer.allocateDirect(raw.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .apply {
                raw.forEach { putFloat((it - mean) / std) }
                rewind()
            }
    }

    private fun l2Normalize(values: FloatArray): FloatArray {
        var sumSquares = 0.0
        for (value in values) sumSquares += value * value
        val norm = sqrt(sumSquares).toFloat()
        if (norm <= 1e-12f) return values
        for (index in values.indices) values[index] /= norm
        return values
    }

    companion object {
        const val MODEL_ASSET = "facenet.tflite"
        const val MODEL_VERSION = "facenet-128d-160-v1"
        const val INPUT_SIZE = 160
        const val EMBEDDING_SIZE = 128
        const val FACE_MARGIN = 0.18f
        private const val MAX_IDENTIFICATION_CANDIDATES = 3
    }
}
