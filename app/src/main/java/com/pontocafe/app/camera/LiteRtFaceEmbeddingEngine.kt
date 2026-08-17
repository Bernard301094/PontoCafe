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

    override suspend fun embed(frame: FaceFrame): FloatArray = withContext(Dispatchers.Default) {
        if (!isReady) {
            throw FaceModelUnavailableException()
        }

        val source = frame.bitmap
        check(!source.isRecycled) { "O frame facial já foi liberado." }

        var face: Bitmap? = null
        var resized: Bitmap? = null
        try {
            // Mantemos exatamente o mesmo recorte/preprocessamento da versão
            // anterior para que todas as biometrias já cadastradas continuem
            // comparáveis. A robustez a touca/óculos é obtida por múltiplos
            // templates da mesma identidade, não mudando o espaço FaceNet.
            val cropped = cropFace(source, frame.faceBounds)
            face = cropped
            val scaled = Bitmap.createScaledBitmap(cropped, INPUT_SIZE, INPUT_SIZE, true)
            resized = scaled

            FaceImageQualityAnalyzer.requireAcceptable(scaled)
            val input = toStandardizedBuffer(scaled)
            val output = Array(1) { FloatArray(EMBEDDING_SIZE) }
            val runtime = getInterpreter()

            inferenceMutex.withLock {
                runtime.run(input, output)
            }
            l2Normalize(output[0])
        } finally {
            val scaled = resized
            val cropped = face
            if (
                scaled != null &&
                scaled !== cropped &&
                scaled !== source &&
                !scaled.isRecycled
            ) {
                scaled.recycle()
            }
            if (cropped != null && cropped !== source && !cropped.isRecycled) {
                cropped.recycle()
            }
            if (!source.isRecycled) {
                source.recycle()
            }
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

    private fun cropFace(bitmap: Bitmap, bounds: Rect): Bitmap {
        val extraX = (bounds.width() * FACE_MARGIN).toInt()
        val extraY = (bounds.height() * FACE_MARGIN).toInt()
        val left = max(0, bounds.left - extraX)
        val top = max(0, bounds.top - extraY)
        val right = min(bitmap.width, bounds.right + extraX)
        val bottom = min(bitmap.height, bounds.bottom + extraY)
        require(right > left && bottom > top) { "Área facial inválida." }
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

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
    }
}
