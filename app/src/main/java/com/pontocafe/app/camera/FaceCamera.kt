package com.pontocafe.app.camera

import android.annotation.SuppressLint
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


data class FaceObservation(
    val faceCount: Int = 0,
    val bounds: Rect? = null,
    val leftEyeOpen: Float? = null,
    val rightEyeOpen: Float? = null,
    val yaw: Float = 0f,
    val roll: Float = 0f,
) {
    val isFrontal: Boolean
        get() = faceCount == 1 && kotlin.math.abs(yaw) <= 15f && kotlin.math.abs(roll) <= 12f

    val eyesClosed: Boolean
        get() = leftEyeOpen != null && rightEyeOpen != null && leftEyeOpen < 0.35f && rightEyeOpen < 0.35f

    val eyesOpen: Boolean
        get() = leftEyeOpen != null && rightEyeOpen != null && leftEyeOpen > 0.70f && rightEyeOpen > 0.70f
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

private fun Face.toObservation(total: Int) = FaceObservation(
    faceCount = total,
    bounds = boundingBox,
    leftEyeOpen = leftEyeOpenProbability,
    rightEyeOpen = rightEyeOpenProbability,
    yaw = headEulerAngleY,
    roll = headEulerAngleZ,
)

@SuppressLint("UnsafeOptInUsageError")
private fun analyzer(
    detector: FaceDetector,
    onObservation: (FaceObservation) -> Unit,
) = ImageAnalysis.Analyzer { imageProxy ->
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return@Analyzer
    }

    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    detector.process(image)
        .addOnSuccessListener { faces ->
            val observation = if (faces.size == 1) {
                faces.first().toObservation(faces.size)
            } else {
                FaceObservation(faceCount = faces.size)
            }
            onObservation(observation)
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
    onObservation: (FaceObservation) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
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
                .also { it.setAnalyzer(executor, analyzer(detector, onObservation)) }

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
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            detector.close()
            executor.shutdown()
        }
    }
}
