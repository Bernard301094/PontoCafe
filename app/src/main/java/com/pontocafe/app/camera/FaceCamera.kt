package com.pontocafe.app.camera

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executor
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
    executor: Executor,
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
        .addOnSuccessListener(executor) { faces ->
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
        .addOnFailureListener(executor) {
            onObservation(FaceObservation())
        }
        .addOnCompleteListener(executor) {
            imageProxy.close()
        }
}

@Composable
private fun FacePositionGuide(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val guideWidth = size.width * 0.62f
        val guideHeight = minOf(size.height * 0.48f, guideWidth * 1.35f)
        val centerX = size.width / 2f
        val centerY = size.height * 0.43f
        val left = centerX - guideWidth / 2f
        val top = centerY - guideHeight / 2f
        val strokeWidth = 4.dp.toPx()
        val guideColor = Color.White.copy(alpha = 0.88f)
        val detailColor = Color.White.copy(alpha = 0.48f)

        drawOval(
            color = guideColor,
            topLeft = Offset(left, top),
            size = ComposeSize(guideWidth, guideHeight),
            style = Stroke(width = strokeWidth),
        )

        val eyeY = top + guideHeight * 0.42f
        val eyeHalfWidth = guideWidth * 0.07f
        val eyeOffset = guideWidth * 0.18f
        drawLine(
            color = detailColor,
            start = Offset(centerX - eyeOffset - eyeHalfWidth, eyeY),
            end = Offset(centerX - eyeOffset + eyeHalfWidth, eyeY),
            strokeWidth = 2.dp.toPx(),
        )
        drawLine(
            color = detailColor,
            start = Offset(centerX + eyeOffset - eyeHalfWidth, eyeY),
            end = Offset(centerX + eyeOffset + eyeHalfWidth, eyeY),
            strokeWidth = 2.dp.toPx(),
        )
        drawLine(
            color = detailColor,
            start = Offset(centerX, top + guideHeight * 0.46f),
            end = Offset(centerX, top + guideHeight * 0.61f),
            strokeWidth = 2.dp.toPx(),
        )
        drawLine(
            color = detailColor,
            start = Offset(centerX - guideWidth * 0.08f, top + guideHeight * 0.72f),
            end = Offset(centerX + guideWidth * 0.08f, top + guideHeight * 0.72f),
            strokeWidth = 2.dp.toPx(),
        )
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
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
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

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                PreviewView(it).also { view ->
                    view.scaleType = PreviewView.ScaleType.FILL_CENTER
                    previewView = view
                }
            },
        )
        FacePositionGuide(Modifier.fillMaxSize())
    }

    LaunchedEffect(previewView, lifecycleOwner) {
        val view = previewView ?: return@LaunchedEffect
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(view.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(
                        executor,
                        analyzer(
                            detector = detector,
                            executor = executor,
                            captureController = captureController,
                            onObservation = { observation ->
                                mainExecutor.execute { currentOnObservation.value(observation) }
                            },
                            onFrame = { frame ->
                                mainExecutor.execute { currentOnFrame.value(frame) }
                            },
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
        }, mainExecutor)
    }

    DisposableEffect(Unit) {
        onDispose {
            detector.close()
            executor.shutdown()
        }
    }
}
