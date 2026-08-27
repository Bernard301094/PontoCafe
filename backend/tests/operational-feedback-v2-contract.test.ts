import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const supervisorAlerts = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/SupervisorLiveAlerts.kt', import.meta.url),
  'utf8',
)
const supervisorNotifier = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/notifications/SupervisorAlertNotifier.kt', import.meta.url),
  'utf8',
)
const pauseFeed = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/OperationalPauseFeed.kt', import.meta.url),
  'utf8',
)
const pontoFlow = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/PontoFlowHost.kt', import.meta.url),
  'utf8',
)
const faceGuide = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/KioskFaceGuide.kt', import.meta.url),
  'utf8',
)
const material = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/MaterialDesignSystem.kt', import.meta.url),
  'utf8',
)
const voice = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/voice/PontoNeuralVoice.kt', import.meta.url),
  'utf8',
)
const capturePolicy = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/camera/FaceCapturePolicy.kt', import.meta.url),
  'utf8',
)

test('Supervisor recebe progressão visual antes do limite sem notificação repetitiva', () => {
  assert.match(supervisorAlerts, /WARNING_THRESHOLD_SECONDS = 60/)
  assert.match(supervisorAlerts, /CRITICAL_THRESHOLD_SECONDS = 15/)
  assert.match(supervisorAlerts, /PROXIMO_LIMITE/)
  assert.match(supervisorAlerts, /CRITICO/)
  assert.match(supervisorAlerts, /EXCESSO/)
  assert.match(supervisorAlerts, /warningBaseline/)
  assert.match(supervisorAlerts, /criticalBaseline/)
  assert.match(supervisorAlerts, /overdueBaseline/)

  assert.match(pauseFeed, /OPERATIONAL_WARNING_SECONDS = 60/)
  assert.match(pauseFeed, /OPERATIONAL_CRITICAL_SECONDS = 15/)
  assert.match(pauseFeed, /Crítico/)
  assert.match(pauseFeed, /Excedido/)
})

test('notificações do Supervisor não sobrescrevem eventos diferentes e permitem autoteste', () => {
  assert.match(supervisorNotifier, /GROUP_KEY/)
  assert.match(supervisorNotifier, /stableNotificationId/)
  assert.match(supervisorNotifier, /setGroupSummary\(true\)/)
  assert.match(supervisorNotifier, /sendSelfTest/)
})

test('feedback do Ponto diferencia confirmado, offline, limite excedido e bloqueio', () => {
  assert.match(pontoFlow, /offline -> Color\(0xFFA5CDFF\)/)
  assert.match(pontoFlow, /offline -> Icons\.Default\.CloudDone/)
  assert.match(pontoFlow, /warning -> Icons\.Default\.Warning/)
  assert.match(pontoFlow, /else -> Icons\.Default\.CheckCircle/)
  assert.match(pontoFlow, /PointBlockReason\.GENERIC/)
  assert.match(pontoFlow, /Color\(0xFFFFB4AB\)/)
  assert.match(pontoFlow, /MotionReveal/)
})

test('microinterações permanecem curtas e acessíveis', () => {
  assert.match(material, /collectIsPressedAsState/)
  assert.match(material, /0\.975f/)
  assert.match(material, /HapticFeedbackConstants\.VIRTUAL_KEY/)
  assert.match(material, /HapticFeedbackConstants\.REJECT/)
  assert.match(material, /MotionReveal/)
  assert.match(faceGuide, /kiosk-recognition-pulse/)
  assert.match(faceGuide, /FACE_GUIDE_READY_STABILITY_MILLIS = 180L/)
})

test('estado da voz neural fica diagnosticável sem retirar fallback Android', () => {
  assert.match(voice, /PontoNeuralVoiceDiagnostics/)
  assert.match(voice, /usingAndroidFallback/)
  assert.match(voice, /lastFailureReason/)
  assert.match(voice, /retryAvailableInMillis/)
  assert.match(voice, /retryNow/)
  assert.match(voice, /RETRY_AFTER_MILLIS = 30_000L/)
  assert.match(voice, /VOICE_PLAYBACK_FAILED/)
})

test('feedback operacional não reduz geometria biométrica de identificação', () => {
  assert.match(capturePolicy, /MAX_IDENTIFICATION_YAW = 12f/)
  assert.match(capturePolicy, /MAX_IDENTIFICATION_PITCH = 12f/)
  assert.match(capturePolicy, /MAX_IDENTIFICATION_ROLL = 8f/)
  assert.doesNotMatch(supervisorAlerts, /faceThreshold|cosine|embedding/)
  assert.doesNotMatch(material, /faceThreshold|cosine|embedding/)
})
