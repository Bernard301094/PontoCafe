package com.pontocafe.app.camera

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs


data class FaceObservation(
    val faceCount: Int = 0,
    val bounds: Rect? = null,
    val leftEyeOpen: Float? = null,
    val rightEyeOpen: Float? = null,
    val pitch: Float = 0f,
    val yaw: Float = 0f,
    val roll: Float = 0f,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
) {
    val faceWidthRatio: Float
        get() = if (imageWidth > 0 && bounds != null) bounds.width().toFloat() / imageWidth else 0f

    val isCentered: Boolean
        get() {
            val box = bounds ?: return false
            if (imageWidth <= 0 || imageHeight <= 0) return false
            val centerX = box.exactCenterX()
            val centerY = box.exactCenterY()
            return abs(centerX - imageWidth / 2f) <= imageWidth * 0.22f &&
                abs(centerY - imageHeight / 2f) <= imageHeight * 0.25f
        }

    val isWellPositioned: Boolean
        get() = faceCount == 1 && bounds != null && isCentered &&
            faceWidthRatio in 0.22f..0.68f && abs(roll) <= 12f

    val isFrontal: Boolean
        get() = isWellPositioned && abs(yaw) <= 15f && abs(pitch) <= 15f

    val eyesClosed: Boolean
        get() = leftEyeOpen != null && rightEyeOpen != null && leftEyeOpen < 0.35f && rightEyeOpen < 0.35f

    val eyesOpen: Boolean
        get() = leftEyeOpen != null && rightEyeOpen != null && leftEyeOpen > 0.70f && rightEyeOpen > 0.70f
}

data class FaceFrame(
    val bitmap: Bitmap,
    val faceBounds: Rect,
)

class FrameCaptureController {
    private val captureNext = AtomicBoolean(false)

    fun request() {
        captureNext.set(true)
    }

    internal fun consume(): Boolean = captureNext.compareAndSet(true, false)
}

enum class LivenessState {
    POSICIONE_ROSTO,
    PISQUE,
    ABRA_OS_OLHOS,
    CONCLUIDO,
}

class BlinkLiveness {
    private var sawClosedEyes = false

    fun reset() {
        sawClosedEyes = false
    }

    fun update(observation: FaceObservation): LivenessState {
        if (!observation.isFrontal) {
            sawClosedEyes = false
            return LivenessState.POSICIONE_ROSTO
        }
        if (!sawClosedEyes) {
            if (observation.eyesClosed) sawClosedEyes = true
            return if (sawClosedEyes) LivenessState.ABRA_OS_OLHOS else LivenessState.PISQUE
        }
        return if (observation.eyesOpen) LivenessState.CONCLUIDO else LivenessState.ABRA_OS_OLHOS
    }
}

private fun Face.toObservation(total: Int, imageWidth: Int, imageHeight: Int) = FaceObservation(
    faceCount = total,
    bounds = boundingBox,
    leftEyeOpen = leftEyeOpenProbability,
    rightEyeOpen = rightEyeOpenProbability,
    pitch = headEulerAngleX,
    yaw = headEulerAngleY,
    roll = headEulerAngleZ,
    imageWidth = imageWidth,
    imageHeight = imageHeight,
)

private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
    if (degrees == 0) return bitmap
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

@SuppressLint("UnsafeOptInUsageError")
private fun analyzer(
    detector: FaceDetector,
    captureController: FrameCaptureController,
    onObservation: (FaceObservation) -> Unit,
    onFrame: (FaceFrame) -> Unit,
) = ImageAnalysis.Analyzer { imageProxy ->
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return@Analyzer
    }

    val rotation = imageProxy.imageInfo.rotationDegrees
    val uprightWidth = if (rotation % 180 == 0) imageProxy.width else imageProxy.height
    val uprightHeight = if (rotation % 180 == 0) imageProxy.height else imageProxy.width
    val image = InputImage.fromMediaImage(mediaImage, rotation)
    detector.process(image)
        .addOnSuccessListener { faces ->
            val observation = if (faces.size == 1) {
                faces.first().toObservation(faces.size, uprightWidth, uprightHeight)
            } else {
                FaceObservation(faceCount = faces.size, imageWidth = uprightWidth, imageHeight = uprightHeight)
            }
            onObservation(observation)

            if (faces.size == 1 && captureController.consume()) {
                runCatching {
                    val bitmap = rotate(imageProxy.toBitmap(), rotation)
                    FaceFrame(bitmap = bitmap, faceBounds = Rect(faces.first().boundingBox))
                }.onSuccess(onFrame)
            }
        }
        .addOnFailureListener {
            onObservation(FaceObservation())
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}

@Composable
fun FaceCameraPreview(
    modifier: Modifier = Modifier,
    captureController: FrameCaptureController,
    onObservation: (FaceObservation) -> Unit,
    onFrame: (FaceFrame) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnObservation = rememberUpdatedState(onObservation)
    val currentOnFrame = rememberUpdatedState(onFrame)
    val executor = remember { Executors.newSingleThreadExecutor() }
    val detector = remember {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.20f)
                .enableTracking()
                .build(),
        )
    }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = {
            PreviewView(it).also { view ->
                view.scaleType = PreviewView.ScaleType.FILL_CENTER
                previewView = view
            }
        },
    )

    LaunchedEffect(previewView, lifecycleOwner) {
        val view = previewView ?: return@LaunchedEffect
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(view.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(
                        executor,
                        analyzer(
                            detector = detector,
                            captureController = captureController,
                            onObservation = { observation -> currentOnObservation.value(observation) },
                            onFrame = { frame -> currentOnFrame.value(frame) },
                        ),
                    )
                }

            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview,
                analysis,
            )
        }, ContextCompat.getMainExecutor(context))
    }

    DisposableEffect(Unit) {
        onDispose {
            detector.close()
            executor.shutdown()
        }
    }
}
