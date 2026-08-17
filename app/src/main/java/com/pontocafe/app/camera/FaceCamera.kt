package com.pontocafe.app.camera

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.util.Log
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
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

private const val FACE_CAMERA_TAG = "PontoCafeFaceCamera"

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

    internal fun retry() {
        captureNext.set(true)
    }
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
    try {
        val mediaImage = imageProxy.image ?: return@Analyzer
        val rotation = imageProxy.imageInfo.rotationDegrees
        val uprightWidth = if (rotation % 180 == 0) imageProxy.width else imageProxy.height
        val uprightHeight = if (rotation % 180 == 0) imageProxy.height else imageProxy.width
        val image = InputImage.fromMediaImage(mediaImage, rotation)

        // O analyzer já roda em um executor de uma única thread. Esperar o ML Kit
        // aqui mantém no máximo um ImageProxy em uso e elimina callbacks que podem
        // tentar acessar um frame depois que CameraX ou a tela já foram encerrados.
        val faces = Tasks.await(detector.process(image))
        val observation = if (faces.size == 1) {
            faces.first().toObservation(faces.size, uprightWidth, uprightHeight)
        } else {
            FaceObservation(faceCount = faces.size, imageWidth = uprightWidth, imageHeight = uprightHeight)
        }
        runCatching { onObservation(observation) }
            .onFailure { Log.w(FACE_CAMERA_TAG, "Falha ao entregar observação facial.", it) }

        if (faces.size == 1 && captureController.consume()) {
            try {
                val sourceBitmap = imageProxy.toBitmap()
                val uprightBitmap = try {
                    rotate(sourceBitmap, rotation)
                } catch (error: Throwable) {
                    if (!sourceBitmap.isRecycled) sourceBitmap.recycle()
                    throw error
                }
                if (uprightBitmap !== sourceBitmap && !sourceBitmap.isRecycled) {
                    sourceBitmap.recycle()
                }

                try {
                    onFrame(
                        FaceFrame(
                            bitmap = uprightBitmap,
                            faceBounds = Rect(faces.first().boundingBox),
                        ),
                    )
                } catch (error: Throwable) {
                    if (!uprightBitmap.isRecycled) uprightBitmap.recycle()
                    throw error
                }
            } catch (error: Throwable) {
                // Não perde a solicitação de captura por uma falha transitória do frame.
                captureController.retry()
                Log.w(FACE_CAMERA_TAG, "Falha ao capturar frame facial; uma nova tentativa será feita.", error)
            }
        }
    } catch (error: InterruptedException) {
        Thread.currentThread().interrupt()
        Log.d(FACE_CAMERA_TAG, "Análise facial interrompida durante o encerramento da câmera.")
    } catch (error: Throwable) {
        Log.e(FACE_CAMERA_TAG, "Falha ao analisar frame facial.", error)
        runCatching { onObservation(FaceObservation()) }
    } finally {
        // Cada ImageProxy deve ser fechado exatamente pelo analyzer, mesmo quando
        // ML Kit, conversão de bitmap ou callbacks falham.
        runCatching { imageProxy.close() }
            .onFailure { Log.w(FACE_CAMERA_TAG, "Falha ao liberar frame da câmera.", it) }
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
    showPositionGuide: Boolean = true,
    onObservation: (FaceObservation) -> Unit,
    onFrame: (FaceFrame) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnObservation = rememberUpdatedState(onObservation)
    val currentOnFrame = rememberUpdatedState(onFrame)
    val executor = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val cameraAlive = remember { AtomicBoolean(true) }
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
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

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
        if (showPositionGuide) {
            FacePositionGuide(Modifier.fillMaxSize())
        }
    }

    LaunchedEffect(previewView, lifecycleOwner) {
        val view = previewView ?: return@LaunchedEffect
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            if (!cameraAlive.get()) return@addListener

            runCatching {
                val provider = providerFuture.get()
                if (!cameraAlive.get()) {
                    provider.unbindAll()
                    return@runCatching
                }
                cameraProvider = provider

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
                                captureController = captureController,
                                onObservation = { observation ->
                                    mainExecutor.execute {
                                        if (cameraAlive.get()) {
                                            runCatching { currentOnObservation.value(observation) }
                                                .onFailure { Log.w(FACE_CAMERA_TAG, "Falha ao atualizar estado facial.", it) }
                                        }
                                    }
                                },
                                onFrame = { frame ->
                                    mainExecutor.execute {
                                        if (!cameraAlive.get()) {
                                            if (!frame.bitmap.isRecycled) frame.bitmap.recycle()
                                            return@execute
                                        }
                                        try {
                                            currentOnFrame.value(frame)
                                        } catch (error: Throwable) {
                                            if (!frame.bitmap.isRecycled) frame.bitmap.recycle()
                                            Log.e(FACE_CAMERA_TAG, "Falha ao entregar frame para reconhecimento.", error)
                                        }
                                    }
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
            }.onFailure { error ->
                if (cameraAlive.get()) {
                    Log.e(FACE_CAMERA_TAG, "Não foi possível iniciar a câmera frontal.", error)
                    runCatching { currentOnObservation.value(FaceObservation()) }
                }
            }
        }, mainExecutor)
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraAlive.set(false)
            runCatching { cameraProvider?.unbindAll() }
            executor.shutdownNow()
            runCatching { detector.close() }
        }
    }
}
