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
    // 0.28 ficava logo abaixo do que o guia oval realmente produz: com
    // aspectRatio(0.80f) e guideWidth = min(largura*0.72, altura*0.58), um rosto
    // encaixado no guia rende ~0.32 de altura. Uma leve inclinacao para tras ja
    // derrubava a pessoa abaixo do piso e o guia ficava vermelho sem explicacao.
    // Este e um portao de QUALIDADE de imagem, nao de identidade: 0.24 de 640px
    // ainda entrega ~154px de origem para uma entrada FaceNet de 160x160.
    const val MIN_FACE_HEIGHT_RATIO = 0.24f
    const val MAX_FACE_HEIGHT_RATIO = 0.90f
    const val MAX_POSITION_ROLL = 12f
    const val MAX_ENROLLMENT_YAW = 42f
    const val MAX_ENROLLMENT_PITCH = 34f
    const val MAX_IDENTIFICATION_YAW = 12f
    const val MAX_IDENTIFICATION_PITCH = 12f
    const val MAX_IDENTIFICATION_ROLL = 8f

    // ── Banda estendida de pose ────────────────────────────────────────────────
    //
    // Os limites acima definem a pose de *plena confiança*: dentro deles nada
    // muda em relação ao comportamento anterior. O problema prático é que um
    // quiosque fixado na altura do peito impõe um pitch sistemático a todas as
    // pessoas — não é sinal de fraude, é geometria de instalação. Rejeitar esses
    // frames por completo empurra a operação para contornos muito piores para a
    // segurança (login compartilhado, liberação por supervisor, biometria
    // desligada). Em vez de afrouxar a constante, abrimos uma banda estendida em
    // que o frame chega ao matcher carregando um rigor proporcional ao desvio.
    //
    // Pitch recebe mais folga que yaw de propósito: yaw oclui metade do rosto,
    // enquanto pitch sobretudo encurta verticalmente — degrada menos o embedding.
    const val EXTENDED_IDENTIFICATION_YAW = 22f
    const val EXTENDED_IDENTIFICATION_PITCH = 26f

    // Roll é o caso especial: o recorte já é de-rotacionado pela linha dos olhos
    // (postRotate(-angleDegrees)) antes de chegar ao FaceNet, então o roll bruto
    // praticamente não sobrevive até o embedding. O gate de 8° era o mais estreito
    // de todos justamente sobre a única distorção já corrigida. Ampliamos, mas só
    // quando há landmarks confiáveis — sem olhos detectados o alinhamento não
    // aconteceu e o limite estrito continua valendo.
    const val ALIGNED_IDENTIFICATION_ROLL = 18f

    /**
     * Rigor exigido do matcher para esta pose, em 0f..1f. Zero dentro da banda de
     * plena confiança (comportamento idêntico ao anterior) e 1f na borda da banda
     * estendida. Nunca é negativo: esta função só pode tornar a decisão mais
     * exigente, nunca mais permissiva.
     */
    fun identificationPoseStringency(facts: FaceCaptureFacts): Float {
        fun excess(value: Float, nominal: Float, extended: Float): Float =
            if (extended <= nominal) 0f
            else ((kotlin.math.abs(value) - nominal) / (extended - nominal)).coerceIn(0f, 1f)

        val rollExtended = if (facts.reliableLandmarks) ALIGNED_IDENTIFICATION_ROLL else MAX_IDENTIFICATION_ROLL
        return maxOf(
            excess(facts.yaw, MAX_IDENTIFICATION_YAW, EXTENDED_IDENTIFICATION_YAW),
            excess(facts.pitch, MAX_IDENTIFICATION_PITCH, EXTENDED_IDENTIFICATION_PITCH),
            excess(facts.roll, MAX_IDENTIFICATION_ROLL, rollExtended),
        )
    }

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
            (kotlin.math.abs(facts.yaw) > EXTENDED_IDENTIFICATION_YAW ||
                kotlin.math.abs(facts.pitch) > EXTENDED_IDENTIFICATION_PITCH ||
                kotlin.math.abs(facts.roll) >
                (if (facts.reliableLandmarks) ALIGNED_IDENTIFICATION_ROLL else MAX_IDENTIFICATION_ROLL)) ->
            FaceCaptureRejectionReason.EXTREME_POSE
        // Landmarks and eye probabilities are auxiliary ML Kit signals, not
        // identity evidence. Glasses/reflections can make them intermittently
        // unavailable even when the face box and pose are valid. Enrollment and
        // identification therefore keep the geometric gates and let FaceNet,
        // liveness, temporal consensus and the authoritative matcher decide the
        // identity. Diagnostic capture remains strict so signal regressions stay
        // observable without blocking legitimate users.
        purpose == FaceCapturePurpose.DIAGNOSTIC && !facts.reliableLandmarks ->
            FaceCaptureRejectionReason.LANDMARKS_MISSING
        purpose == FaceCapturePurpose.DIAGNOSTIC && !facts.eyesAcceptable ->
            FaceCaptureRejectionReason.EYES_NOT_VISIBLE
        else -> null
    }
}
