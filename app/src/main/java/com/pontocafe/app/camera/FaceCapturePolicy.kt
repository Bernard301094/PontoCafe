package com.pontocafe.app.camera

enum class FaceCapturePurpose {
    IDENTIFICATION,
    ENROLLMENT,
    DIAGNOSTIC,
}

enum class FaceCaptureRejectionReason {
    NO_FACE,
    MULTIPLE_FACES,
    NOT_CENTERED,
    FACE_TOO_SMALL,
    FACE_TOO_LARGE,
    PARTIAL_FACE,
    EXTREME_POSE,
    LANDMARKS_MISSING,
    EYES_NOT_VISIBLE,
    TRACK_CHANGED,
    POSE_CHANGED,
    REQUEST_EXPIRED,
}

data class FaceCaptureFacts(
    val faceCount: Int,
    val centered: Boolean,
    val faceWidthRatio: Float,
    val faceHeightRatio: Float,
    val fullyVisible: Boolean,
    val yaw: Float,
    val pitch: Float,
    val roll: Float,
    val reliableLandmarks: Boolean,
    val eyesAcceptable: Boolean,
)

/** Pure policy so frame selection can be tested without CameraX or ML Kit. */
object FaceCapturePolicy {
    const val MIN_FACE_WIDTH_RATIO = 0.22f
    const val MAX_FACE_WIDTH_RATIO = 0.68f
    const val MIN_FACE_HEIGHT_RATIO = 0.28f
    const val MAX_FACE_HEIGHT_RATIO = 0.90f
    const val MAX_POSITION_ROLL = 12f
    const val MAX_ENROLLMENT_YAW = 42f
    const val MAX_ENROLLMENT_PITCH = 34f
    const val MAX_IDENTIFICATION_YAW = 12f
    const val MAX_IDENTIFICATION_PITCH = 12f
    const val MAX_IDENTIFICATION_ROLL = 8f

    fun evaluate(
        facts: FaceCaptureFacts,
        purpose: FaceCapturePurpose,
    ): FaceCaptureRejectionReason? = when {
        facts.faceCount <= 0 -> FaceCaptureRejectionReason.NO_FACE
        facts.faceCount > 1 -> FaceCaptureRejectionReason.MULTIPLE_FACES
        !facts.centered -> FaceCaptureRejectionReason.NOT_CENTERED
        facts.faceWidthRatio < MIN_FACE_WIDTH_RATIO ||
            facts.faceHeightRatio < MIN_FACE_HEIGHT_RATIO -> FaceCaptureRejectionReason.FACE_TOO_SMALL
        facts.faceWidthRatio > MAX_FACE_WIDTH_RATIO ||
            facts.faceHeightRatio > MAX_FACE_HEIGHT_RATIO -> FaceCaptureRejectionReason.FACE_TOO_LARGE
        !facts.fullyVisible -> FaceCaptureRejectionReason.PARTIAL_FACE
        kotlin.math.abs(facts.roll) > MAX_POSITION_ROLL -> FaceCaptureRejectionReason.EXTREME_POSE
        purpose == FaceCapturePurpose.ENROLLMENT &&
            (kotlin.math.abs(facts.yaw) > MAX_ENROLLMENT_YAW ||
                kotlin.math.abs(facts.pitch) > MAX_ENROLLMENT_PITCH) ->
            FaceCaptureRejectionReason.EXTREME_POSE
        purpose != FaceCapturePurpose.ENROLLMENT &&
            (kotlin.math.abs(facts.yaw) > MAX_IDENTIFICATION_YAW ||
                kotlin.math.abs(facts.pitch) > MAX_IDENTIFICATION_PITCH ||
                kotlin.math.abs(facts.roll) > MAX_IDENTIFICATION_ROLL) ->
            FaceCaptureRejectionReason.EXTREME_POSE
        !facts.reliableLandmarks -> FaceCaptureRejectionReason.LANDMARKS_MISSING
        purpose != FaceCapturePurpose.ENROLLMENT && !facts.eyesAcceptable ->
            FaceCaptureRejectionReason.EYES_NOT_VISIBLE
        else -> null
    }
}
