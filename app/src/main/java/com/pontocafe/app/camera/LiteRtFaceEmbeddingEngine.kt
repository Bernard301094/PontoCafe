package com.pontocafe.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.tflite.client.TfLiteInitializationOptions
import com.google.android.gms.tflite.gpu.support.TfLiteGpu
import com.google.android.gms.tflite.java.TfLite
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tensorflow.lite.InterpreterApi
import org.tensorflow.lite.InterpreterApi.Options.TfLiteRuntime
import org.tensorflow.lite.gpu.GpuDelegateFactory

class LiteRtFaceEmbeddingEngine(
    private val context: Context,
    private val assetName: String = MODEL_ASSET,
) : FaceEmbeddingEngine {

    private enum class Backend { CPU, GPU }

    private val initMutex = Mutex()
    private val inferenceMutex = Mutex()
    private val inferenceDispatcher = Executors
        .newSingleThreadExecutor { runnable ->
            Thread(runnable, "PontoCafe-FaceNet").apply { priority = Thread.NORM_PRIORITY }
        }
        .asCoroutineDispatcher()
    private val prefs = context.applicationContext.getSharedPreferences(
        "pontocafe_facenet_runtime",
        Context.MODE_PRIVATE,
    )

    @Volatile
    private var interpreter: InterpreterApi? = null

    @Volatile
    private var activeBackend: Backend = Backend.CPU

    override val isReady: Boolean
        get() = runCatching { context.assets.openFd(assetName).close() }.isSuccess

    override val modelName: String = "FaceNet 128D · LiteRT"
    override val modelVersion: String = MODEL_VERSION

    override suspend fun warmUp() {
        if (!isReady) return
        getInterpreter()
    }

    /**
     * Caminho canônico do cadastro biométrico. O recorte e o espaço de embedding
     * permanecem compatíveis com todas as biometrias já cadastradas.
     */
    override suspend fun embed(frame: FaceFrame): FloatArray = withContext(Dispatchers.Default) {
        validateFrame(frame)
        val source = frame.bitmap
        var cropped: Bitmap? = null
        try {
            val currentCrop = crop(source, canonicalRect(source, frame.faceBounds))
            cropped = currentCrop
            embedBitmap(currentCrop)
        } finally {
            cropped?.takeIf { it !== source && !it.isRecycled }?.recycle()
            if (!source.isRecycled) source.recycle()
        }
    }

    /**
     * Identificação adaptativa progressiva com uma única captura.
     *
     * O FaceNet roda primeiro somente no recorte canônico. Se [shouldContinue]
     * disser que a identidade já foi aprovada, encerramos imediatamente e não
     * calculamos os dois fallbacks. Isso deixa o caminho comum tão barato quanto
     * o reconhecimento antigo e mantém os recortes extras apenas para casos
     * difíceis (touca, cabelo, pequenas variações do bounding-box).
     */
    override suspend fun embedForIdentification(
        frame: FaceFrame,
        shouldContinue: (embedding: FloatArray, candidateIndex: Int) -> Boolean,
    ): List<FloatArray> = withContext(Dispatchers.Default) {
        validateFrame(frame)
        val source = frame.bitmap
        val candidates = ArrayList<FloatArray>(MAX_IDENTIFICATION_CANDIDATES)
        val usedRects = LinkedHashSet<Rect>(MAX_IDENTIFICATION_CANDIDATES)

        try {
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
                    embedRect(source, landmarkRect, required = false)?.let { anchored ->
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

    private fun validateFrame(frame: FaceFrame) {
        if (!isReady) throw FaceModelUnavailableException()
        check(!frame.bitmap.isRecycled) { "O frame facial já foi liberado." }
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

    private suspend fun embedBitmap(face: Bitmap): FloatArray {
        var resized: Bitmap? = null
        try {
            val scaled = if (face.width == INPUT_SIZE && face.height == INPUT_SIZE) {
                face
            } else {
                Bitmap.createScaledBitmap(face, INPUT_SIZE, INPUT_SIZE, true)
            }
            resized = scaled
            FaceImageQualityAnalyzer.requireAcceptable(scaled)

            val input = toStandardizedBuffer(scaled)
            val output = Array(1) { FloatArray(EMBEDDING_SIZE) }
            runInference(input, output)
            return l2Normalize(output[0])
        } finally {
            resized?.takeIf { it !== face && !it.isRecycled }?.recycle()
        }
    }

    private suspend fun runInference(input: ByteBuffer, output: Array<FloatArray>) {
        inferenceMutex.withLock {
            var runtime = getInterpreter()
            try {
                withContext(inferenceDispatcher) {
                    input.rewind()
                    runtime.run(input, output)
                }
            } catch (error: Throwable) {
                if (activeBackend != Backend.GPU) throw error

                // Driver/delegado GPU pode falhar em aparelhos específicos. A
                // identidade nunca depende disso: desativamos a GPU, recriamos o
                // runtime CPU/XNNPACK e repetimos a mesma inferência uma única vez.
                Log.w(TAG, "Falha no delegate GPU; alternando FaceNet para CPU.", error)
                runtime = switchToCpu(runtime)
                withContext(inferenceDispatcher) {
                    input.rewind()
                    runtime.run(input, output)
                }
            }
        }
    }

    private suspend fun switchToCpu(current: InterpreterApi): InterpreterApi = initMutex.withLock {
        interpreter?.let { existing ->
            if (existing !== current && activeBackend == Backend.CPU) return@withLock existing
        }

        val cpu = withContext(inferenceDispatcher) {
            runCatching { current.close() }
            createCpuInterpreter()
        }
        activeBackend = Backend.CPU
        interpreter = cpu
        rememberBackend(Backend.CPU)
        cpu
    }

    private suspend fun getInterpreter(): InterpreterApi {
        interpreter?.let { return it }
        return initMutex.withLock {
            interpreter?.let { return@withLock it }
            withContext(inferenceDispatcher) {
                createBestInterpreter().also { interpreter = it }
            }
        }
    }

    /**
     * Seleciona CPU ou GPU medindo o próprio FaceNet neste aparelho. O resultado
     * fica salvo por versão do modelo para que inicializações seguintes não
     * repitam o benchmark. GPU só é escolhida quando realmente é mais rápida.
     */
    private fun createBestInterpreter(): InterpreterApi {
        val appContext = context.applicationContext
        val gpuAvailable = runCatching {
            Tasks.await(TfLiteGpu.isGpuDelegateAvailable(appContext))
        }.getOrDefault(false)

        val initializedWithGpu = if (gpuAvailable) {
            runCatching {
                val options = TfLiteInitializationOptions.builder()
                    .setEnableGpuDelegateSupport(true)
                    .build()
                Tasks.await(TfLite.initialize(appContext, options))
            }.isSuccess
        } else {
            false
        }

        if (!initializedWithGpu) {
            Tasks.await(TfLite.initialize(appContext))
        }

        val saved = prefs.getString(runtimePreferenceKey(), null)
        if (saved == Backend.GPU.name && initializedWithGpu) {
            runCatching { createGpuInterpreter() }.getOrNull()?.let { runtime ->
                activeBackend = Backend.GPU
                Log.i(TAG, "FaceNet reutilizando delegate GPU previamente validado.")
                return runtime
            }
        }
        if (saved == Backend.CPU.name) {
            activeBackend = Backend.CPU
            Log.i(TAG, "FaceNet reutilizando CPU/XNNPACK previamente validada.")
            return createCpuInterpreter()
        }

        val cpu = createCpuInterpreter()
        if (!initializedWithGpu) {
            activeBackend = Backend.CPU
            rememberBackend(Backend.CPU)
            return cpu
        }

        val gpu = runCatching { createGpuInterpreter() }.getOrNull()
        if (gpu == null) {
            activeBackend = Backend.CPU
            rememberBackend(Backend.CPU)
            return cpu
        }

        val cpuNs = runCatching { benchmark(cpu) }.getOrElse { Long.MAX_VALUE }
        val gpuNs = runCatching { benchmark(gpu) }.getOrElse { Long.MAX_VALUE }
        val chooseGpu = gpuNs < cpuNs && gpuNs != Long.MAX_VALUE

        return if (chooseGpu) {
            runCatching { cpu.close() }
            activeBackend = Backend.GPU
            rememberBackend(Backend.GPU)
            Log.i(TAG, "FaceNet: GPU selecionada (${gpuNs / 1_000_000.0} ms vs CPU ${cpuNs / 1_000_000.0} ms).")
            gpu
        } else {
            runCatching { gpu.close() }
            activeBackend = Backend.CPU
            rememberBackend(Backend.CPU)
            Log.i(TAG, "FaceNet: CPU/XNNPACK selecionada (${cpuNs / 1_000_000.0} ms vs GPU ${gpuNs / 1_000_000.0} ms).")
            cpu
        }
    }

    private fun createCpuInterpreter(): InterpreterApi {
        val options = InterpreterApi.Options()
            .setRuntime(TfLiteRuntime.FROM_SYSTEM_ONLY)
            .setNumThreads(CPU_THREADS)
        return InterpreterApi.create(loadModelBuffer(), options)
    }

    private fun createGpuInterpreter(): InterpreterApi {
        val options = InterpreterApi.Options()
            .setRuntime(TfLiteRuntime.FROM_SYSTEM_ONLY)
            .addDelegateFactory(GpuDelegateFactory())
        return InterpreterApi.create(loadModelBuffer(), options)
    }

    private fun benchmark(runtime: InterpreterApi): Long {
        val input = ByteBuffer.allocateDirect(INPUT_FLOAT_COUNT * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
        val output = Array(1) { FloatArray(EMBEDDING_SIZE) }

        // Uma passagem prepara caches/delegate. A mediana de três execuções
        // reduz ruído sem transformar o warm-up em um teste demorado.
        input.rewind()
        runtime.run(input, output)
        val samples = LongArray(BENCHMARK_RUNS)
        repeat(BENCHMARK_RUNS) { index ->
            input.rewind()
            val start = SystemClock.elapsedRealtimeNanos()
            runtime.run(input, output)
            samples[index] = SystemClock.elapsedRealtimeNanos() - start
        }
        samples.sort()
        return samples[samples.size / 2]
    }

    private fun rememberBackend(backend: Backend) {
        prefs.edit().putString(runtimePreferenceKey(), backend.name).apply()
    }

    private fun runtimePreferenceKey(): String = "backend_${MODEL_VERSION}_v2"

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
     * Prewhitening FaceNet sem o FloatArray intermediário antigo. Mantemos a
     * mesma matemática, mas reduzimos alocações/GC em cada captura.
     */
    private fun toStandardizedBuffer(bitmap: Bitmap): ByteBuffer {
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        var sum = 0.0
        for (pixel in pixels) {
            sum += ((pixel shr 16) and 0xFF)
            sum += ((pixel shr 8) and 0xFF)
            sum += (pixel and 0xFF)
        }
        val mean = (sum / INPUT_FLOAT_COUNT).toFloat()

        var squared = 0.0
        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF).toFloat() - mean
            val g = ((pixel shr 8) and 0xFF).toFloat() - mean
            val b = (pixel and 0xFF).toFloat() - mean
            squared += r * r + g * g + b * b
        }

        val calculatedStd = sqrt(squared / INPUT_FLOAT_COUNT).toFloat()
        val minimumStd = 1f / sqrt(INPUT_FLOAT_COUNT.toFloat())
        val std = max(calculatedStd, minimumStd)

        return ByteBuffer.allocateDirect(INPUT_FLOAT_COUNT * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .apply {
                for (pixel in pixels) {
                    putFloat((((pixel shr 16) and 0xFF).toFloat() - mean) / std)
                    putFloat((((pixel shr 8) and 0xFF).toFloat() - mean) / std)
                    putFloat(((pixel and 0xFF).toFloat() - mean) / std)
                }
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
        private const val TAG = "PontoCafeFaceNet"
        const val MODEL_ASSET = "facenet.tflite"
        const val MODEL_VERSION = "facenet-128d-160-v1"
        const val INPUT_SIZE = 160
        const val EMBEDDING_SIZE = 128
        const val FACE_MARGIN = 0.18f
        private const val MAX_IDENTIFICATION_CANDIDATES = 3
        private const val CPU_THREADS = 2
        private const val BENCHMARK_RUNS = 3
        private const val INPUT_FLOAT_COUNT = INPUT_SIZE * INPUT_SIZE * 3
    }
}
