package com.pontocafe.app.camera

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.Rect
import android.os.SystemClock
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
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.pontocafe.app.data.BiometricRuntimeDiagnostics
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.hypot

private const val FACE_CAMERA_TAG = "PontoCafeFaceCamera"
private const val OBSERVATION_DELIVERY_INTERVAL_MS = 50L

/**
 * Sinal mínimo, em memória, da câmera ativa. O fluxo rápido usa apenas a
 * contagem para saber quando a pessoa que acabou de registrar saiu do quadro.
 * Nenhuma imagem, landmark ou dado biométrico é armazenado aqui.
 */
object FacePresenceMonitor {
    @Volatile
    var faceCount: Int = 0
        internal set
}

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
    val trackingId: Int? = null,
    val leftEye: PointF? = null,
    val rightEye: PointF? = null,
    val noseBase: PointF? = null,
    val mouthBottom: PointF? = null,
) {
    val faceWidthRatio: Float
        get() = if (imageWidth > 0 && bounds != null) bounds.width().toFloat() / imageWidth else 0f

    val faceHeightRatio: Float
        get() = if (imageHeight > 0 && bounds != null) bounds.height().toFloat() / imageHeight else 0f

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
        get() = FaceCapturePolicy.evaluate(toCaptureFacts(), FaceCapturePurpose.ENROLLMENT) == null

    val isFrontal: Boolean
        get() = isWellPositioned && abs(yaw) <= 15f && abs(pitch) <= 15f

    val eyeClassificationAvailable: Boolean
        get() = leftEyeOpen != null && rightEyeOpen != null

    val eyesClosed: Boolean
        get() = eyeClassificationAvailable && leftEyeOpen!! < 0.35f && rightEyeOpen!! < 0.35f

    val eyesOpen: Boolean
        get() = eyeClassificationAvailable && leftEyeOpen!! > 0.70f && rightEyeOpen!! > 0.70f

    val isFullyVisible: Boolean
        get() {
            val box = bounds ?: return false
            if (imageWidth <= 0 || imageHeight <= 0) return false
            val marginX = imageWidth * 0.02f
            val marginY = imageHeight * 0.02f
            return box.left >= marginX && box.top >= marginY &&
                box.right <= imageWidth - marginX && box.bottom <= imageHeight - marginY
        }

    val hasReliableLandmarks: Boolean
        get() {
            val box = bounds ?: return false
            val left = leftEye ?: return false
            val right = rightEye ?: return false
            val nose = noseBase ?: return false
            val mouth = mouthBottom ?: return false
            val points = listOf(left, right, nose, mouth)
            if (points.any { !box.contains(it.x.toInt(), it.y.toInt()) }) return false
            val eyeDistance = hypot((right.x - left.x).toDouble(), (right.y - left.y).toDouble()).toFloat()
            val eyeMidY = (left.y + right.y) / 2f
            return eyeDistance in box.width() * 0.18f..box.width() * 0.72f &&
                nose.y > eyeMidY && mouth.y > nose.y
        }

    val eyesAcceptableForIdentification: Boolean
        get() = !eyeClassificationAvailable || (leftEyeOpen!! >= 0.45f && rightEyeOpen!! >= 0.45f)

    val isIdentificationReady: Boolean
        get() = FaceCapturePolicy.evaluate(toCaptureFacts(), FaceCapturePurpose.IDENTIFICATION) == null

    fun toCaptureFacts() = FaceCaptureFacts(
        faceCount = faceCount,
        centered = isCentered,
        faceWidthRatio = faceWidthRatio,
        faceHeightRatio = faceHeightRatio,
        fullyVisible = isFullyVisible,
        yaw = yaw,
        pitch = pitch,
        roll = roll,
        reliableLandmarks = hasReliableLandmarks,
        eyesAcceptable = eyesAcceptableForIdentification,
    )
}

data class FaceFrame(
    val bitmap: Bitmap,
    val faceBounds: Rect,
    val leftEye: PointF? = null,
    val rightEye: PointF? = null,
    val noseBase: PointF? = null,
    val mouthBottom: PointF? = null,
    val observation: FaceObservation,
    val capturedAtMillis: Long,
)

class FrameCaptureController {
    internal data class Request(
        val reference: FaceObservation?,
        val purpose: FaceCapturePurpose,
        val requestedAtMillis: Long,
    )

    internal sealed interface ClaimResult {
        data object None : ClaimResult
        data class Claimed(val request: Request) : ClaimResult
        data class Rejected(val reason: FaceCaptureRejectionReason) : ClaimResult
    }

    private val pending = AtomicReference<Request?>(null)

    fun request() {
        setRequest(reference = null, purpose = FaceCapturePurpose.DIAGNOSTIC)
    }

    fun request(reference: FaceObservation, purpose: FaceCapturePurpose) {
        setRequest(reference, purpose)
    }

    private fun setRequest(reference: FaceObservation?, purpose: FaceCapturePurpose) {
        pending.set(Request(reference, purpose, SystemClock.elapsedRealtime()))
    }

    internal fun claim(observation: FaceObservation): ClaimResult {
        val request = pending.get() ?: return ClaimResult.None
        val now = SystemClock.elapsedRealtime()
        val policyRejection = FaceCapturePolicy.evaluate(observation.toCaptureFacts(), request.purpose)
        val reason = when {
            now - request.requestedAtMillis > CAPTURE_REQUEST_TTL_MILLIS ->
                FaceCaptureRejectionReason.REQUEST_EXPIRED
            policyRejection != null -> policyRejection
            request.reference?.trackingId != null && observation.trackingId != null &&
                request.reference.trackingId != observation.trackingId -> FaceCaptureRejectionReason.TRACK_CHANGED
            request.reference != null &&
                (abs(request.reference.yaw - observation.yaw) > MAX_CAPTURE_POSE_DRIFT ||
                    abs(request.reference.pitch - observation.pitch) > MAX_CAPTURE_POSE_DRIFT ||
                    abs(request.reference.roll - observation.roll) > MAX_CAPTURE_POSE_DRIFT) ->
                FaceCaptureRejectionReason.POSE_CHANGED
            else -> null
        }
        if (reason != null) {
            pending.compareAndSet(request, null)
            return ClaimResult.Rejected(reason)
        }
        return if (pending.compareAndSet(request, null)) ClaimResult.Claimed(request) else ClaimResult.None
    }

    internal fun retry(request: Request) {
        if (SystemClock.elapsedRealtime() - request.requestedAtMillis <= CAPTURE_REQUEST_TTL_MILLIS) {
            pending.compareAndSet(null, request)
        }
    }

    companion object {
        private const val CAPTURE_REQUEST_TTL_MILLIS = 1_500L
        private const val MAX_CAPTURE_POSE_DRIFT = 8f
    }
}

enum class LivenessState {
    POSICIONE_ROSTO,
    PISQUE,
    ABRA_OS_OLHOS,
    CONCLUIDO,
}

/** Binds a liveness sequence to one ML Kit track, with an IoU fallback. */
class FaceTrackContinuity(
    private val minimumIntersectionOverUnion: Float = 0.35f,
) {
    private var trackingId: Int? = null
    private var bounds: Rect? = null

    init {
        require(minimumIntersectionOverUnion in 0f..1f)
    }

    fun bind(observation: FaceObservation) {
        trackingId = observation.trackingId
        bounds = observation.bounds?.let(::Rect)
    }

    fun reset() {
        trackingId = null
        bounds = null
    }

    fun matches(observation: FaceObservation): Boolean {
        if (trackingId != null || observation.trackingId != null) {
            return trackingId != null && trackingId == observation.trackingId
        }
        val original = bounds ?: return false
        val current = observation.bounds ?: return false
        val intersectionLeft = maxOf(original.left, current.left)
        val intersectionTop = maxOf(original.top, current.top)
        val intersectionRight = minOf(original.right, current.right)
        val intersectionBottom = minOf(original.bottom, current.bottom)
        val intersection = maxOf(0, intersectionRight - intersectionLeft) *
            maxOf(0, intersectionBottom - intersectionTop)
        val union = original.width() * original.height() + current.width() * current.height() - intersection
        return union > 0 && intersection.toFloat() / union >= minimumIntersectionOverUnion
    }
}

class BlinkLiveness {
    private var sawClosedEyes = false
    private val continuity = FaceTrackContinuity()

    fun reset() {
        sawClosedEyes = false
        continuity.reset()
    }

    fun matchesChallengeFace(observation: FaceObservation): Boolean =
        sawClosedEyes && continuity.matches(observation)

    fun update(observation: FaceObservation): LivenessState {
        if (!observation.isFrontal || !observation.eyeClassificationAvailable) {
            reset()
            return LivenessState.POSICIONE_ROSTO
        }
        if (sawClosedEyes && !sameChallengeFace(observation)) reset()
        if (!sawClosedEyes) {
            if (observation.eyesClosed) {
                sawClosedEyes = true
                continuity.bind(observation)
            }
            return if (sawClosedEyes) LivenessState.ABRA_OS_OLHOS else LivenessState.PISQUE
        }
        return if (observation.eyesOpen) LivenessState.CONCLUIDO else LivenessState.ABRA_OS_OLHOS
    }

    private fun sameChallengeFace(observation: FaceObservation): Boolean = continuity.matches(observation)
}

private fun Face.landmarkPoint(type: Int): PointF? =
    getLandmark(type)?.position?.let { PointF(it.x, it.y) }

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
    trackingId = trackingId,
    leftEye = landmarkPoint(FaceLandmark.LEFT_EYE),
    rightEye = landmarkPoint(FaceLandmark.RIGHT_EYE),
    noseBase = landmarkPoint(FaceLandmark.NOSE_BASE),
    mouthBottom = landmarkPoint(FaceLandmark.MOUTH_BOTTOM),
)

private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
    if (degrees == 0) return bitmap
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

/**
 * Pipeline comprovado do PontoCafe: um único detector ML Kit FAST fornece, no
 * mesmo frame, caixa facial, pose, olhos e landmarks. Isso evita divergência de
 * estado entre dois detectores e preserva o comportamento que já reconhecia os
 * colaboradores corretamente.
 */
@SuppressLint("UnsafeOptInUsageError")
private fun analyzer(
    detector: FaceDetector,
    captureController: FrameCaptureController,
    analysisEnabled: () -> Boolean,
    onObservation: (FaceObservation) -> Unit,
    onFrame: (FaceFrame) -> Unit,
    onCaptureRejected: (FaceCaptureRejectionReason) -> Unit,
): ImageAnalysis.Analyzer {
    var lastObservationDeliveryAt = 0L
    var lastDeliveredFaceCount = -1

    return ImageAnalysis.Analyzer { imageProxy ->
        try {
            if (!analysisEnabled()) {
                FacePresenceMonitor.faceCount = 0
                return@Analyzer
            }
            val mediaImage = imageProxy.image ?: return@Analyzer
            val rotation = imageProxy.imageInfo.rotationDegrees
            val uprightWidth = if (rotation % 180 == 0) imageProxy.width else imageProxy.height
            val uprightHeight = if (rotation % 180 == 0) imageProxy.height else imageProxy.width
            val image = InputImage.fromMediaImage(mediaImage, rotation)

            val faces = Tasks.await(detector.process(image))
            val observation = if (faces.size == 1) {
                faces.first().toObservation(faces.size, uprightWidth, uprightHeight)
            } else {
                FaceObservation(faceCount = faces.size, imageWidth = uprightWidth, imageHeight = uprightHeight)
            }
            FacePresenceMonitor.faceCount = observation.faceCount

            val now = SystemClock.uptimeMillis()
            val shouldDeliverObservation =
                observation.faceCount != lastDeliveredFaceCount ||
                    now - lastObservationDeliveryAt >= OBSERVATION_DELIVERY_INTERVAL_MS
            if (shouldDeliverObservation) {
                lastObservationDeliveryAt = now
                lastDeliveredFaceCount = observation.faceCount
                runCatching { onObservation(observation) }
                    .onFailure { Log.w(FACE_CAMERA_TAG, "Falha ao entregar observação facial.", it) }
            }

            when (val claim = captureController.claim(observation)) {
                is FrameCaptureController.ClaimResult.Rejected -> {
                    BiometricRuntimeDiagnostics.recordQualityRejection("CAPTURE_${claim.reason.name}")
                    runCatching { onCaptureRejected(claim.reason) }
                }
                is FrameCaptureController.ClaimResult.Claimed -> {
                    if (faces.size == 1) {
                        try {
                            val face = faces.first()
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
                                        faceBounds = Rect(face.boundingBox),
                                        leftEye = face.landmarkPoint(FaceLandmark.LEFT_EYE),
                                        rightEye = face.landmarkPoint(FaceLandmark.RIGHT_EYE),
                                        noseBase = face.landmarkPoint(FaceLandmark.NOSE_BASE),
                                        mouthBottom = face.landmarkPoint(FaceLandmark.MOUTH_BOTTOM),
                                        observation = observation,
                                        capturedAtMillis = System.currentTimeMillis(),
                                    ),
                                )
                            } catch (error: Throwable) {
                                if (!uprightBitmap.isRecycled) uprightBitmap.recycle()
                                throw error
                            }
                        } catch (error: Throwable) {
                            captureController.retry(claim.request)
                            Log.w(
                                FACE_CAMERA_TAG,
                                "Falha ao capturar frame facial; uma nova tentativa será feita.",
                                error,
                            )
                        }
                    }
                }
                FrameCaptureController.ClaimResult.None -> Unit
            }
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            Log.d(FACE_CAMERA_TAG, "Análise facial interrompida durante o encerramento da câmera.")
        } catch (error: Throwable) {
            FacePresenceMonitor.faceCount = 0
            Log.e(FACE_CAMERA_TAG, "Falha ao analisar frame facial.", error)
            runCatching { onObservation(FaceObservation()) }
        } finally {
            runCatching { imageProxy.close() }
                .onFailure { Log.w(FACE_CAMERA_TAG, "Falha ao liberar frame da câmera.", it) }
        }
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
    analysisEnabled: Boolean = true,
    showPositionGuide: Boolean = true,
    onObservation: (FaceObservation) -> Unit,
    onFrame: (FaceFrame) -> Unit,
    onCaptureRejected: (FaceCaptureRejectionReason) -> Unit = {},
    onError: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val rootView = LocalView.current
    val currentOnObservation = rememberUpdatedState(onObservation)
    val currentOnFrame = rememberUpdatedState(onFrame)
    val currentOnCaptureRejected = rememberUpdatedState(onCaptureRejected)
    val currentOnError = rememberUpdatedState(onError)
    val executor = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val cameraAlive = remember { AtomicBoolean(true) }
    val analysisEnabledFlag = remember { AtomicBoolean(analysisEnabled) }
    val detector = remember {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.15f)
                .enableTracking()
                .build(),
        )
    }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    SideEffect {
        analysisEnabledFlag.set(analysisEnabled)
        if (!analysisEnabled) FacePresenceMonitor.faceCount = 0
    }

    DisposableEffect(rootView) {
        val previousKeepScreenOn = rootView.keepScreenOn
        rootView.keepScreenOn = true
        onDispose {
            rootView.keepScreenOn = previousKeepScreenOn
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .clearAndSetSemantics { },
            factory = {
                PreviewView(it).also { view ->
                    view.implementationMode = PreviewView.ImplementationMode.PERFORMANCE
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
                                analysisEnabled = analysisEnabledFlag::get,
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
                                onCaptureRejected = { reason ->
                                    mainExecutor.execute {
                                        if (cameraAlive.get()) currentOnCaptureRejected.value(reason)
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
                    FacePresenceMonitor.faceCount = 0
                    Log.e(FACE_CAMERA_TAG, "Não foi possível iniciar a câmera frontal.", error)
                    runCatching { currentOnObservation.value(FaceObservation()) }
                    runCatching {
                        currentOnError.value(
                            "Não foi possível iniciar a câmera frontal. Verifique se ela está disponível e tente novamente.",
                        )
                    }
                }
            }
        }, mainExecutor)
    }

    DisposableEffect(Unit) {
        onDispose {
            FacePresenceMonitor.faceCount = 0
            cameraAlive.set(false)
            runCatching { cameraProvider?.unbindAll() }
            executor.shutdownNow()
            runCatching { detector.close() }
        }
    }
}
