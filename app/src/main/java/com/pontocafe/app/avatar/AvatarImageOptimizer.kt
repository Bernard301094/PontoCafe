package com.pontocafe.app.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

object AvatarImageOptimizer {
    private const val TARGET_SIZE = 256
    private const val FALLBACK_SIZE = 192
    private const val LAST_RESORT_SIZE = 160
    const val MAX_BYTES = 28 * 1024

    fun optimize(context: Context, uri: Uri): ByteArray = optimizeDecoded(decode(context, uri))

    /**
     * Usa o mesmo pipeline da galeria para fotos tiradas diretamente pela câmera.
     * Uma cópia software é criada para que o bitmap retornado pelo ActivityResult
     * não seja reciclado ou alterado pelo otimizador.
     */
    fun optimize(bitmap: Bitmap): ByteArray {
        require(!bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0) {
            "A foto da câmera é inválida."
        }
        val decoded = bitmap.copy(Bitmap.Config.ARGB_8888, false)
            ?: error("Não foi possível preparar a foto da câmera.")
        return optimizeDecoded(decoded)
    }

    private fun optimizeDecoded(decoded: Bitmap): ByteArray {
        var square: Bitmap? = null
        try {
            val prepared = centerCrop(decoded)
            square = prepared
            return optimizePreparedSquare(prepared)
        } finally {
            square?.takeIf { it !== decoded && !it.isRecycled }?.recycle()
            if (!decoded.isRecycled) decoded.recycle()
        }
    }

    /**
     * Otimiza um recorte quadrado já preparado sem assumir a posse do bitmap.
     * O enrolamento usa esta variante para manter somente o WebP final e liberar
     * seu recorte temporário logo depois, sem copiar novamente o frame inteiro.
     */
    internal fun optimizePreparedSquare(bitmap: Bitmap): ByteArray {
        require(!bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0) {
            "A foto de perfil preparada é inválida."
        }
        require(bitmap.width == bitmap.height) { "A foto de perfil deve usar recorte quadrado." }

        val sourceSide = bitmap.width
        val attempts = linkedSetOf(
            min(TARGET_SIZE, sourceSide),
            min(FALLBACK_SIZE, sourceSide),
            min(LAST_RESORT_SIZE, sourceSide),
        ).filter { it > 0 }

        attempts.forEachIndexed { index, size ->
            val scaled = Bitmap.createScaledBitmap(bitmap, size, size, true)
            try {
                val encoded = when (index) {
                    0 -> compressToLimit(scaled, startQuality = 78, minQuality = 46)
                    1 -> compressToLimit(scaled, startQuality = 70, minQuality = 40)
                    else -> compressToLimit(scaled, startQuality = 62, minQuality = 34)
                }
                if (encoded != null) return encoded
            } finally {
                if (scaled !== bitmap && !scaled.isRecycled) scaled.recycle()
            }
        }

        error("A imagem continua grande demais mesmo após otimização.")
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
            val orientation = resolver.openInputStream(uri)?.use { input ->
                runCatching {
                    ExifInterface(input).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
                }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Não foi possível ler a imagem selecionada." }

            var sample = 1
            while (max(bounds.outWidth / sample, bounds.outHeight / sample) > 720) sample *= 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = resolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: error("Não foi possível abrir a imagem selecionada.")
            applyExifOrientation(decoded, orientation)
        }
    }

    /** ImageDecoder handles EXIF itself; this covers the Android 8.x fallback. */
    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }

        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                .also { transformed ->
                    if (transformed !== bitmap && !bitmap.isRecycled) bitmap.recycle()
                }
        } catch (error: Throwable) {
            if (!bitmap.isRecycled) bitmap.recycle()
            throw error
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
