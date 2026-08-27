import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const camera = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/camera/FaceCamera.kt', import.meta.url),
  'utf8',
)
const engine = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/camera/LiteRtFaceEmbeddingEngine.kt', import.meta.url),
  'utf8',
)
const kiosk = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/FaceKioskScreen.kt', import.meta.url),
  'utf8',
)

test('analisador facial usa um único detector detalhado e sempre libera ImageProxy', () => {
  assert.match(camera, /Tasks\.await\(detector\.process\(image\)\)/)
  assert.match(camera, /LANDMARK_MODE_ALL/)
  assert.match(camera, /CLASSIFICATION_MODE_ALL/)
  assert.match(camera, /enableTracking\(\)/)
  assert.doesNotMatch(camera, /presenceDetector/)
  assert.doesNotMatch(camera, /detailedDetector/)
  assert.match(camera, /finally\s*\{[\s\S]*imageProxy\.close\(\)/)
  assert.match(camera, /captureController\.retry\(claim\.request\)/)
  assert.match(camera, /FaceCapturePolicy\.evaluate/)
  assert.match(camera, /FaceTrackContinuity/)
  assert.match(camera, /STRATEGY_KEEP_ONLY_LATEST/)
  assert.match(camera, /executor\.shutdownNow\(\)/)
})

test('análise ML Kit pausa enquanto um reconhecimento não pode aceitar novos frames', () => {
  assert.match(camera, /analysisEnabled: \(\) -> Boolean/)
  assert.match(camera, /if \(!analysisEnabled\(\)\)/)
  assert.match(camera, /return@Analyzer/)
  assert.match(camera, /analysisEnabledFlag::get/)
  assert.match(kiosk, /analysisEnabled = viewModel\.faceModelReady && state\.scanning/)
  assert.match(kiosk, /state\.catalogoBiometricoPronto && !state\.carregando/)
  assert.match(kiosk, /state\.sincronizandoBiometrias && !state\.catalogoBiometricoPronto/)
  assert.match(kiosk, /!state\.carregando/)
})

test('falha ao abrir câmera frontal chega à interface em vez de parecer ausência de rosto', () => {
  assert.match(camera, /onError: \(String\) -> Unit/)
  assert.match(camera, /currentOnError\.value/)
  assert.match(camera, /Não foi possível iniciar a câmera frontal/)
  assert.match(kiosk, /var cameraError by remember/)
  assert.match(kiosk, /Câmera indisponível/)
  assert.match(kiosk, /error = cameraError \?: state\.erro/)
})

test('embedding canônico continua CPU e com duas threads', () => {
  assert.match(engine, /private val inferenceMutex = Mutex\(\)/)
  assert.match(engine, /inferenceMutex\.withLock/)
  assert.match(engine, /TfLite\.initialize\(context\.applicationContext\)/)
  assert.match(engine, /\.setRuntime\(TfLiteRuntime\.FROM_SYSTEM_ONLY\)/)
  assert.match(engine, /\.setNumThreads\(CPU_THREADS\)/)
  assert.match(engine, /CPU_THREADS = 2/)
  assert.doesNotMatch(engine, /GpuDelegateFactory/)
  assert.doesNotMatch(engine, /TfLiteGpu/)
})
