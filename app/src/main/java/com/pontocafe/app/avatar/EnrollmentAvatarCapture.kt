package com.pontocafe.app.avatar

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import com.pontocafe.app.camera.FaceFrame
import com.pontocafe.app.camera.FaceImageQualityAnalyzer
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

enum class EnrollmentAvatarUploadStatus {
    NOT_CAPTURED,
    UPLOADING,
    SAVED,
    FAILED,
}

/** Integer-only crop model so the framing policy can be unit-tested without Android graphics. */
data class AvatarCropRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Produces a square portrait crop with headroom and room below the face.
 * This is deliberately wider than the FaceNet crop and is never fed back into
 * recognition; it exists only for the visual profile image.
 */
object NaturalAvatarCropPolicy {
    fun square(
        imageWidth: Int,
        imageHeight: Int,
        face: AvatarCropRect,
    ): AvatarCropRect {
        require(imageWidth > 0 && imageHeight > 0)
        require(face.width > 0 && face.height > 0)

        val safeLeft = face.left.coerceIn(0, imageWidth - 1)
        val safeTop = face.top.coerceIn(0, imageHeight - 1)
        val safeRight = face.right.coerceIn(safeLeft + 1, imageWidth)
        val safeBottom = face.bottom.coerceIn(safeTop + 1, imageHeight)
        val faceWidth = safeRight - safeLeft
        val faceHeight = safeBottom - safeTop

        // El rostro ocupa aproximadamente 55-65 % del retrato. El centro baja
        // levemente para conservar hombros sin cortar la parte superior de la cabeza.
        val desiredSide = ceil(max(faceWidth * 1.90f, faceHeight * 1.62f)).toInt()
        val maximumSide = min(imageWidth, imageHeight)
        val minimumSide = min(max(faceWidth, faceHeight), maximumSide)
        val side = desiredSide.coerceIn(
            minimumSide,
            maximumSide,
        )
        val desiredCenterX = (safeLeft + safeRight) / 2f
        val desiredCenterY = (safeTop + safeBottom) / 2f + faceHeight * 0.10f
        val left = (desiredCenterX - side / 2f).toInt().coerceIn(0, imageWidth - side)
        val top = (desiredCenterY - side / 2f).toInt().coerceIn(0, imageHeight - side)
        return AvatarCropRect(left, top, left + side, top + side)
    }
}

data class EnrollmentAvatarCandidateFacts(
    val faceCount: Int,
    val centered: Boolean,
    val fullyVisible: Boolean,
    val reliableLandmarks: Boolean,
    val eyesOpen: Boolean,
    val leftEyeOpen: Float,
    val rightEyeOpen: Float,
    val yaw: Float,
    val pitch: Float,
    val roll: Float,
    val faceWidthRatio: Float,
    val faceHeightRatio: Float,
    val centerOffsetX: Float,
    val centerOffsetY: Float,
    val qualityAcceptable: Boolean = false,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val sharpness: Float = 0f,
    val laplacianVariance: Float = 0f,
    val sideIlluminationDelta: Float = 0f,
    val backlightDelta: Float = 0f,
)

/** Pure eligibility and ranking policy used before any WebP encoding. */
object EnrollmentAvatarCandidatePolicy {
    const val MAX_YAW = 10f
    const val MAX_PITCH = 10f
    const val MAX_ROLL = 8f
    const val MIN_FACE_WIDTH_RATIO = 0.24f
    const val MAX_FACE_WIDTH_RATIO = 0.62f
    const val MIN_FACE_HEIGHT_RATIO = 0.30f
    const val MAX_FACE_HEIGHT_RATIO = 0.84f

    fun isFrontalEligible(facts: EnrollmentAvatarCandidateFacts): Boolean =
        facts.faceCount == 1 &&
            facts.centered &&
            facts.fullyVisible &&
            facts.reliableLandmarks &&
            facts.eyesOpen &&
            abs(facts.yaw) <= MAX_YAW &&
            abs(facts.pitch) <= MAX_PITCH &&
            abs(facts.roll) <= MAX_ROLL &&
            facts.faceWidthRatio in MIN_FACE_WIDTH_RATIO..MAX_FACE_WIDTH_RATIO &&
            facts.faceHeightRatio in MIN_FACE_HEIGHT_RATIO..MAX_FACE_HEIGHT_RATIO

    fun score(facts: EnrollmentAvatarCandidateFacts): Float? {
        if (!isFrontalEligible(facts) || !facts.qualityAcceptable) return null

        val frontal = (1f - (
            0.45f * abs(facts.yaw) / MAX_YAW +
                0.35f * abs(facts.pitch) / MAX_PITCH +
                0.20f * abs(facts.roll) / MAX_ROLL
            )).coerceIn(0f, 1f)
        val centered = (1f - (
            0.60f * abs(facts.centerOffsetX) / 0.22f +
                0.40f * abs(facts.centerOffsetY) / 0.25f
            )).coerceIn(0f, 1f)
        val scale = (
            proximity(facts.faceWidthRatio, target = 0.42f, tolerance = 0.20f) +
                proximity(facts.faceHeightRatio, target = 0.56f, tolerance = 0.28f)
            ) / 2f
        val sharpness = (
            increasing(facts.sharpness, 2.5f, 12f) +
                increasing(facts.laplacianVariance, 32f, 240f)
            ) / 2f
        val exposure = proximity(facts.brightness, target = 132f, tolerance = 94f)
        val uniformLighting = (
            0.65f * (1f - facts.sideIlluminationDelta / 52f).coerceIn(0f, 1f) +
                0.35f * (1f - facts.backlightDelta.coerceAtLeast(0f) / 65f).coerceIn(0f, 1f)
            )
        val contrast = increasing(facts.contrast, 14f, 55f)
        val openEyes = (
            increasing(facts.leftEyeOpen, 0.70f, 1f) +
                increasing(facts.rightEyeOpen, 0.70f, 1f)
            ) / 2f

        return 30f * frontal +
            18f * centered +
            10f * scale +
            18f * sharpness +
            10f * exposure +
            7f * uniformLighting +
            4f * contrast +
            3f * openEyes
    }

    private fun proximity(value: Float, target: Float, tolerance: Float): Float =
        (1f - abs(value - target) / tolerance).coerceIn(0f, 1f)

    private fun increasing(value: Float, minimum: Float, ideal: Float): Float =
        ((value - minimum) / (ideal - minimum)).coerceIn(0f, 1f)
}

internal class StagedEnrollmentAvatar(
    val bitmap: Bitmap,
    val faceBounds: Rect,
    val facts: EnrollmentAvatarCandidateFacts,
) : AutoCloseable {
    override fun close() {
        if (!bitmap.isRecycled) bitmap.recycle()
    }
}

/**
 * Session-local best-frame selector. It never stores the camera frame or a
 * biometric crop: only the currently staged visual crop and the winning WebP.
 */
class EnrollmentAvatarCaptureSession(
    private val mirrorFrontCamera: Boolean = true,
) {
    private var bestScore = Float.NEGATIVE_INFINITY
    private var bestWebp: ByteArray? = null

    internal fun stage(frame: FaceFrame): StagedEnrollmentAvatar? {
        val source = frame.bitmap
        if (source.isRecycled || source.width <= 0 || source.height <= 0) return null
        val observation = frame.observation
        val bounds = frame.faceBounds
        val centerOffsetX = if (source.width > 0) {
            (bounds.exactCenterX() - source.width / 2f) / source.width
        } else {
            1f
        }
        val centerOffsetY = if (source.height > 0) {
            (bounds.exactCenterY() - source.height / 2f) / source.height
        } else {
            1f
        }
        val facts = EnrollmentAvatarCandidateFacts(
            faceCount = observation.faceCount,
            centered = observation.isCentered,
            fullyVisible = observation.isFullyVisible,
            reliableLandmarks = observation.hasReliableLandmarks,
            eyesOpen = observation.eyesOpen,
            leftEyeOpen = observation.leftEyeOpen ?: 0f,
            rightEyeOpen = observation.rightEyeOpen ?: 0f,
            yaw = observation.yaw,
            pitch = observation.pitch,
            roll = observation.roll,
            faceWidthRatio = observation.faceWidthRatio,
            faceHeightRatio = observation.faceHeightRatio,
            centerOffsetX = centerOffsetX,
            centerOffsetY = centerOffsetY,
        )
        if (!EnrollmentAvatarCandidatePolicy.isFrontalEligible(facts)) return null

        val crop = NaturalAvatarCropPolicy.square(
            imageWidth = source.width,
            imageHeight = source.height,
            face = AvatarCropRect(bounds.left, bounds.top, bounds.right, bounds.bottom),
        )
        val matrix = if (mirrorFrontCamera) Matrix().apply { setScale(-1f, 1f) } else null
        val portrait = Bitmap.createBitmap(
            source,
            crop.left,
            crop.top,
            crop.width,
            crop.height,
            matrix,
            true,
        )
        val localFace = Rect(
            bounds.left.coerceIn(crop.left, crop.right) - crop.left,
            bounds.top.coerceIn(crop.top, crop.bottom) - crop.top,
            bounds.right.coerceIn(crop.left, crop.right) - crop.left,
            bounds.bottom.coerceIn(crop.top, crop.bottom) - crop.top,
        )
        val portraitFace = if (mirrorFrontCamera) {
            Rect(
                portrait.width - localFace.right,
                localFace.top,
                portrait.width - localFace.left,
                localFace.bottom,
            )
        } else {
            localFace
        }
        return StagedEnrollmentAvatar(portrait, portraitFace, facts)
    }

    /** Returns true only when this frame replaced the previous best candidate. */
    internal fun consider(staged: StagedEnrollmentAvatar): Boolean {
        val quality = FaceImageQualityAnalyzer.analyzeFrame(staged.bitmap, staged.faceBounds)
        val metrics = quality.metrics ?: return false
        val score = EnrollmentAvatarCandidatePolicy.score(
            staged.facts.copy(
                qualityAcceptable = quality.acceptable,
                brightness = metrics.brightness,
                contrast = metrics.contrast,
                sharpness = metrics.sharpness,
                laplacianVariance = metrics.laplacianVariance,
                sideIlluminationDelta = metrics.sideIlluminationDelta,
                backlightDelta = metrics.backlightDelta,
            ),
        ) ?: return false
        if (score <= bestScore + MIN_SCORE_IMPROVEMENT) return false

        val optimized = AvatarImageOptimizer.optimizePreparedSquare(staged.bitmap)
        bestWebp?.fill(0)
        bestWebp = optimized
        bestScore = score
        return true
    }

    fun hasCandidate(): Boolean = bestWebp != null

    /** Transfers the winning compressed image to the caller and empties the session. */
    fun takeBestWebp(): ByteArray? {
        val result = bestWebp
        bestWebp = null
        bestScore = Float.NEGATIVE_INFINITY
        return result
    }

    fun clear() {
        bestWebp?.fill(0)
        bestWebp = null
        bestScore = Float.NEGATIVE_INFINITY
    }

    private companion object {
        const val MIN_SCORE_IMPROVEMENT = 0.10f
    }
}
