package com.pontocafe.app.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

object AvatarImageOptimizer {
    private const val TARGET_SIZE = 160
    private const val FALLBACK_SIZE = 128
    const val MAX_BYTES = 28 * 1024

    fun optimize(context: Context, uri: Uri): ByteArray {
        val decoded = decode(context, uri)
        val square = centerCrop(decoded)
        if (square !== decoded) decoded.recycle()

        try {
            val primary = Bitmap.createScaledBitmap(square, TARGET_SIZE, TARGET_SIZE, true)
            try {
                compressToLimit(primary)?.let { return it }
            } finally {
                if (primary !== square) primary.recycle()
            }

            val fallback = Bitmap.createScaledBitmap(square, FALLBACK_SIZE, FALLBACK_SIZE, true)
            try {
                return compressToLimit(fallback, startQuality = 58, minQuality = 34)
                    ?: error("A imagem continua grande demais mesmo após otimização.")
            } finally {
                if (fallback !== square) fallback.recycle()
            }
        } finally {
            square.recycle()
        }
    }

    private fun decode(context: Context, uri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val sourceWidth = info.size.width.coerceAtLeast(1)
                val sourceHeight = info.size.height.coerceAtLeast(1)
                val longest = max(sourceWidth, sourceHeight)
                if (longest > 720) {
                    val scale = 720.0 / longest.toDouble()
                    decoder.setTargetSize(
                        (sourceWidth * scale).toInt().coerceAtLeast(1),
                        (sourceHeight * scale).toInt().coerceAtLeast(1),
                    )
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Não foi possível ler a imagem selecionada." }

            var sample = 1
            while (max(bounds.outWidth / sample, bounds.outHeight / sample) > 720) sample *= 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            resolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: error("Não foi possível abrir a imagem selecionada.")
        }
    }

    private fun centerCrop(bitmap: Bitmap): Bitmap {
        val side = min(bitmap.width, bitmap.height)
        val left = ((bitmap.width - side) / 2).coerceAtLeast(0)
        val top = ((bitmap.height - side) / 2).coerceAtLeast(0)
        return Bitmap.createBitmap(bitmap, left, top, side, side)
    }

    private fun compressToLimit(
        bitmap: Bitmap,
        startQuality: Int = 72,
        minQuality: Int = 40,
    ): ByteArray? {
        var quality = startQuality
        while (quality >= minQuality) {
            val output = ByteArrayOutputStream()
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            if (!bitmap.compress(format, quality, output)) return null
            val bytes = output.toByteArray()
            if (bytes.size <= MAX_BYTES) return bytes
            quality -= 6
        }
        return null
    }
}
