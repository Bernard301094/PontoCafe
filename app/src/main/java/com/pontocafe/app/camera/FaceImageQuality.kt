package com.pontocafe.app.camera

import android.graphics.Bitmap
import kotlin.math.sqrt


data class FaceImageQuality(
    val brightness: Float,
    val contrast: Float,
    val sharpness: Float,
    val acceptable: Boolean,
    val hint: String?,
)

class FaceImageQualityException(message: String) : IllegalArgumentException(message)

object FaceImageQualityAnalyzer {
    fun analyze(bitmap: Bitmap): FaceImageQuality {
        val width = bitmap.width
        val height = bitmap.height
        require(width > 1 && height > 1) { "Imagem facial inválida." }

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val luminance = FloatArray(pixels.size)
        var sum = 0.0
        pixels.forEachIndexed { index, pixel ->
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val y = (0.2126f * r + 0.7152f * g + 0.0722f * b)
            luminance[index] = y
            sum += y
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
        for (y in 1 until height) {
            for (x in 1 until width) {
                val current = luminance[y * width + x]
                val left = luminance[y * width + x - 1]
                val top = luminance[(y - 1) * width + x]
                edgeSum += kotlin.math.abs(current - left) + kotlin.math.abs(current - top)
                edgeCount += 2
            }
        }
        val sharpness = if (edgeCount > 0) (edgeSum / edgeCount).toFloat() else 0f

        val hint = when {
            brightness < 28f -> "O rosto está muito escuro. Procure mais iluminação frontal."
            brightness > 238f -> "O rosto está superexposto. Evite luz forte diretamente na câmera."
            contrast < 10f -> "A imagem tem pouco contraste. Evite contraluz e melhore a iluminação do rosto."
            sharpness < 2.2f -> "A imagem está muito desfocada. Fique parado e limpe a lente da câmera."
            else -> null
        }

        return FaceImageQuality(
            brightness = brightness,
            contrast = contrast,
            sharpness = sharpness,
            acceptable = hint == null,
            hint = hint,
        )
    }

    fun requireAcceptable(bitmap: Bitmap): FaceImageQuality {
        val quality = analyze(bitmap)
        if (!quality.acceptable) throw FaceImageQualityException(quality.hint ?: "A qualidade da imagem facial é insuficiente.")
        return quality
    }
}
