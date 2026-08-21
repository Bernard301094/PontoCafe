package com.pontocafe.app.camera

import android.graphics.Bitmap
import android.graphics.Rect
import com.pontocafe.app.data.BiometricRuntimeDiagnostics
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

enum class FaceImageQualityRejection {
    UNDEREXPOSED,
    OVEREXPOSED,
    LOW_CONTRAST,
    BLURRED,
    EXCESSIVE_SHADOW,
    BACKLIT,
    CLIPPED_DARK,
    CLIPPED_BRIGHT,
}

data class FaceImageQualityMetrics(
    val brightness: Float,
    val contrast: Float,
    val sharpness: Float,
    val laplacianVariance: Float,
    val darkPixelRatio: Float,
    val brightPixelRatio: Float,
    val sideIlluminationDelta: Float,
    val backlightDelta: Float = 0f,
)

data class FaceImageQuality(
    val brightness: Float,
    val contrast: Float,
    val sharpness: Float,
    val acceptable: Boolean,
    val hint: String?,
    val rejection: FaceImageQualityRejection? = null,
    val metrics: FaceImageQualityMetrics? = null,
)

class FaceImageQualityException(
    val rejection: FaceImageQualityRejection,
    val quality: FaceImageQuality,
) : IllegalArgumentException(quality.hint ?: "A qualidade da imagem facial e insuficiente.")

/** Pure thresholds separated from Bitmap processing for deterministic tests. */
object FaceImageQualityPolicy {
    fun reject(metrics: FaceImageQualityMetrics): FaceImageQualityRejection? = when {
        metrics.brightness < 38f -> FaceImageQualityRejection.UNDEREXPOSED
        metrics.brightness > 225f -> FaceImageQualityRejection.OVEREXPOSED
        metrics.darkPixelRatio > 0.38f -> FaceImageQualityRejection.CLIPPED_DARK
        metrics.brightPixelRatio > 0.32f -> FaceImageQualityRejection.CLIPPED_BRIGHT
        metrics.backlightDelta > 65f && metrics.brightness < 115f -> FaceImageQualityRejection.BACKLIT
        metrics.sideIlluminationDelta > 52f -> FaceImageQualityRejection.EXCESSIVE_SHADOW
        metrics.contrast < 14f -> FaceImageQualityRejection.LOW_CONTRAST
        metrics.sharpness < 2.5f || metrics.laplacianVariance < 32f -> FaceImageQualityRejection.BLURRED
        else -> null
    }

    fun hint(reason: FaceImageQualityRejection): String = when (reason) {
        FaceImageQualityRejection.UNDEREXPOSED,
        FaceImageQualityRejection.CLIPPED_DARK ->
            "O rosto esta muito escuro. Procure mais iluminacao frontal."
        FaceImageQualityRejection.OVEREXPOSED,
        FaceImageQualityRejection.CLIPPED_BRIGHT ->
            "O rosto esta superexposto. Evite luz forte diretamente na camera."
        FaceImageQualityRejection.LOW_CONTRAST ->
            "A imagem tem pouco contraste. Melhore a iluminacao do rosto."
        FaceImageQualityRejection.BLURRED ->
            "A imagem esta desfocada. Fique parado e limpe a lente da camera."
        FaceImageQualityRejection.EXCESSIVE_SHADOW ->
            "Ha sombra excessiva no rosto. Use iluminacao frontal uniforme."
        FaceImageQualityRejection.BACKLIT ->
            "Ha muita luz atras do rosto. Fique de frente para a fonte de luz."
    }
}

object FaceImageQualityAnalyzer {
    fun analyze(bitmap: Bitmap, backlightDelta: Float = 0f): FaceImageQuality {
        val width = bitmap.width
        val height = bitmap.height
        require(width > 2 && height > 2) { "Imagem facial invalida." }

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val luminance = FloatArray(pixels.size)
        var sum = 0.0
        var darkPixels = 0
        var brightPixels = 0
        var leftSum = 0.0
        var rightSum = 0.0
        var leftCount = 0
        var rightCount = 0

        pixels.forEachIndexed { index, pixel ->
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val value = 0.2126f * r + 0.7152f * g + 0.0722f * b
            luminance[index] = value
            sum += value
            if (value < 20f) darkPixels += 1
            if (value > 245f) brightPixels += 1
            val x = index % width
            when {
                x < width / 2 -> {
                    leftSum += value
                    leftCount += 1
                }
                x > width / 2 -> {
                    rightSum += value
                    rightCount += 1
                }
            }
        }

        val brightness = (sum / luminance.size).toFloat()
        var variance = 0.0
        luminance.forEach { value ->
            val delta = value - brightness
            variance += delta * delta
        }
        val contrast = sqrt(variance / luminance.size).toFloat()

        var edgeSum = 0.0
        var edgeCount = 0
        var laplacianSum = 0.0
        var laplacianSquared = 0.0
        var laplacianCount = 0
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val index = y * width + x
                val current = luminance[index]
                edgeSum += abs(current - luminance[index - 1]) + abs(current - luminance[index - width])
                edgeCount += 2
                val laplacian = 4f * current - luminance[index - 1] - luminance[index + 1] -
                    luminance[index - width] - luminance[index + width]
                laplacianSum += laplacian
                laplacianSquared += laplacian * laplacian
                laplacianCount += 1
            }
        }
        val sharpness = if (edgeCount > 0) (edgeSum / edgeCount).toFloat() else 0f
        val laplacianMean = if (laplacianCount > 0) laplacianSum / laplacianCount else 0.0
        val laplacianVariance = if (laplacianCount > 0) {
            max(0.0, laplacianSquared / laplacianCount - laplacianMean * laplacianMean).toFloat()
        } else {
            0f
        }
        val leftMean = if (leftCount > 0) (leftSum / leftCount).toFloat() else brightness
        val rightMean = if (rightCount > 0) (rightSum / rightCount).toFloat() else brightness
        val metrics = FaceImageQualityMetrics(
            brightness = brightness,
            contrast = contrast,
            sharpness = sharpness,
            laplacianVariance = laplacianVariance,
            darkPixelRatio = darkPixels.toFloat() / luminance.size,
            brightPixelRatio = brightPixels.toFloat() / luminance.size,
            sideIlluminationDelta = abs(leftMean - rightMean),
            backlightDelta = backlightDelta,
        )
        val rejection = FaceImageQualityPolicy.reject(metrics)
        return FaceImageQuality(
            brightness = brightness,
            contrast = contrast,
            sharpness = sharpness,
            acceptable = rejection == null,
            hint = rejection?.let(FaceImageQualityPolicy::hint),
            rejection = rejection,
            metrics = metrics,
        )
    }

    /** Estimates backlight from the face versus pixels outside its box. */
    fun analyzeFrame(bitmap: Bitmap, faceBounds: Rect): FaceImageQuality {
        val safe = Rect(
            faceBounds.left.coerceIn(0, bitmap.width - 1),
            faceBounds.top.coerceIn(0, bitmap.height - 1),
            faceBounds.right.coerceIn(1, bitmap.width),
            faceBounds.bottom.coerceIn(1, bitmap.height),
        )
        if (safe.width() <= 2 || safe.height() <= 2) return analyze(bitmap)

        val step = 4
        var faceSum = 0.0
        var faceCount = 0
        var backgroundSum = 0.0
        var backgroundCount = 0
        for (y in 0 until bitmap.height step step) {
            for (x in 0 until bitmap.width step step) {
                val pixel = bitmap.getPixel(x, y)
                val value = 0.2126f * ((pixel shr 16) and 0xFF) +
                    0.7152f * ((pixel shr 8) and 0xFF) +
                    0.0722f * (pixel and 0xFF)
                if (safe.contains(x, y)) {
                    faceSum += value
                    faceCount += 1
                } else {
                    backgroundSum += value
                    backgroundCount += 1
                }
            }
        }
        val faceMean = if (faceCount > 0) (faceSum / faceCount).toFloat() else 0f
        val backgroundMean = if (backgroundCount > 0) (backgroundSum / backgroundCount).toFloat() else faceMean
        val crop = Bitmap.createBitmap(bitmap, safe.left, safe.top, safe.width(), safe.height())
        return try {
            analyze(crop, backlightDelta = backgroundMean - faceMean)
        } finally {
            if (!crop.isRecycled) crop.recycle()
        }
    }

    fun requireAcceptable(bitmap: Bitmap): FaceImageQuality = requireResult(analyze(bitmap))

    fun requireAcceptableFrame(bitmap: Bitmap, faceBounds: Rect): FaceImageQuality =
        requireResult(analyzeFrame(bitmap, faceBounds))

    private fun requireResult(quality: FaceImageQuality): FaceImageQuality {
        val rejection = quality.rejection ?: return quality
        BiometricRuntimeDiagnostics.recordQualityRejection(rejection.name)
        throw FaceImageQualityException(rejection, quality)
    }
}
