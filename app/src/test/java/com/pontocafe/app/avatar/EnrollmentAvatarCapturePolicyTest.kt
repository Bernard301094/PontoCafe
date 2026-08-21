package com.pontocafe.app.avatar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnrollmentAvatarCapturePolicyTest {
    @Test
    fun `natural crop is square bounded and keeps face with portrait context`() {
        val face = AvatarCropRect(left = 236, top = 90, right = 404, bottom = 326)

        val crop = NaturalAvatarCropPolicy.square(
            imageWidth = 640,
            imageHeight = 480,
            face = face,
        )

        assertTrue(crop.width == crop.height)
        assertTrue(crop.left >= 0 && crop.top >= 0)
        assertTrue(crop.right <= 640 && crop.bottom <= 480)
        assertTrue(crop.left <= face.left && crop.right >= face.right)
        assertTrue(crop.top <= face.top && crop.bottom >= face.bottom)
        assertTrue(crop.bottom - face.bottom > 0)
    }

    @Test
    fun `natural crop clamps safely near frame edges in portrait and landscape`() {
        val portrait = NaturalAvatarCropPolicy.square(
            imageWidth = 480,
            imageHeight = 640,
            face = AvatarCropRect(8, 42, 205, 330),
        )
        val landscape = NaturalAvatarCropPolicy.square(
            imageWidth = 640,
            imageHeight = 480,
            face = AvatarCropRect(438, 100, 632, 386),
        )

        listOf(portrait to (480 to 640), landscape to (640 to 480)).forEach { (crop, size) ->
            assertTrue(crop.width == crop.height)
            assertTrue(crop.left >= 0 && crop.top >= 0)
            assertTrue(crop.right <= size.first && crop.bottom <= size.second)
        }
    }

    @Test
    fun `only one centered frontal visible face with open eyes is eligible`() {
        val valid = validFacts()

        assertTrue(EnrollmentAvatarCandidatePolicy.isFrontalEligible(valid))
        assertFalse(EnrollmentAvatarCandidatePolicy.isFrontalEligible(valid.copy(faceCount = 2)))
        assertFalse(EnrollmentAvatarCandidatePolicy.isFrontalEligible(valid.copy(centered = false)))
        assertFalse(EnrollmentAvatarCandidatePolicy.isFrontalEligible(valid.copy(eyesOpen = false)))
        assertFalse(EnrollmentAvatarCandidatePolicy.isFrontalEligible(valid.copy(yaw = 10.1f)))
        assertFalse(EnrollmentAvatarCandidatePolicy.isFrontalEligible(valid.copy(roll = 8.1f)))
        assertFalse(EnrollmentAvatarCandidatePolicy.isFrontalEligible(valid.copy(fullyVisible = false)))
    }

    @Test
    fun `ranking prefers sharper brighter and more frontal valid frame`() {
        val best = EnrollmentAvatarCandidatePolicy.score(validFacts())
        val weaker = EnrollmentAvatarCandidatePolicy.score(
            validFacts().copy(
                yaw = 8f,
                pitch = 7f,
                roll = 6f,
                centerOffsetX = 0.16f,
                brightness = 62f,
                contrast = 18f,
                sharpness = 3.2f,
                laplacianVariance = 40f,
                sideIlluminationDelta = 42f,
                backlightDelta = 48f,
                leftEyeOpen = 0.73f,
                rightEyeOpen = 0.74f,
            ),
        )

        assertNotNull(best)
        assertNotNull(weaker)
        assertTrue(requireNotNull(best) > requireNotNull(weaker))
    }

    @Test
    fun `unacceptable image quality is never ranked`() {
        assertNull(
            EnrollmentAvatarCandidatePolicy.score(
                validFacts().copy(qualityAcceptable = false),
            ),
        )
    }

    private fun validFacts() = EnrollmentAvatarCandidateFacts(
        faceCount = 1,
        centered = true,
        fullyVisible = true,
        reliableLandmarks = true,
        eyesOpen = true,
        leftEyeOpen = 0.96f,
        rightEyeOpen = 0.95f,
        yaw = 1f,
        pitch = 1f,
        roll = 0.5f,
        faceWidthRatio = 0.42f,
        faceHeightRatio = 0.56f,
        centerOffsetX = 0.01f,
        centerOffsetY = 0.02f,
        qualityAcceptable = true,
        brightness = 132f,
        contrast = 46f,
        sharpness = 11f,
        laplacianVariance = 220f,
        sideIlluminationDelta = 5f,
        backlightDelta = 3f,
    )
}
