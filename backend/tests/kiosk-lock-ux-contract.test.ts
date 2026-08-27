import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const lockScreen = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/RestrictedAreaLockScreen.kt', import.meta.url),
  'utf8',
)
const kiosk = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/FaceKioskScreen.kt', import.meta.url),
  'utf8',
)
const flow = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/PontoFlowHost.kt', import.meta.url),
  'utf8',
)
const gradle = readFileSync(
  new URL('../../app/build.gradle.kts', import.meta.url),
  'utf8',
)

test('área protegida mantém autenticação forte e melhora a hierarquia de UX', () => {
  assert.match(lockScreen, /BiometricPrompt/)
  assert.match(lockScreen, /BIOMETRIC_STRONG/)
  assert.match(lockScreen, /DEVICE_CREDENTIAL/)
  assert.match(lockScreen, /Desbloquear agora/)
  assert.match(lockScreen, /Voltar ao Ponto Café/)
  assert.match(lockScreen, /Sessão ativa neste dispositivo/)
  assert.match(lockScreen, /BoxWithConstraints/)
  assert.match(lockScreen, /widthIn\(max = 520\.dp\)/)
})

test('Ponto preserva exatamente o contrato de liveness existente', () => {
  assert.match(kiosk, /CHALLENGE_STABLE_FRAMES = 4/)
  assert.match(kiosk, /RECOGNITION_STABLE_FRAMES = 4/)
  assert.match(kiosk, /BLINK_FALLBACK_FRAMES = 36/)
  assert.match(kiosk, /BlinkLiveness/)
  assert.match(kiosk, /FaceTrackContinuity/)
  assert.match(kiosk, /FaceCameraPreview/)
  assert.match(kiosk, /captureController\.request\(observation, FaceCapturePurpose\.IDENTIFICATION\)/)
})

test('Ponto comunica o fluxo facial com layout responsivo', () => {
  assert.match(kiosk, /Bater ponto por reconhecimento facial/)
  assert.match(kiosk, /Ative a câmera para bater o ponto/)
  assert.match(kiosk, /Confirmando seu ponto/)
  assert.match(kiosk, /Rosto não reconhecido/)
  assert.match(kiosk, /KioskCameraScrims/)
  assert.match(kiosk, /maxWidth >= 600\.dp/)
  assert.match(kiosk, /fillMaxWidth\(if \(expanded\) 0\.46f else 0\.72f\)/)
})

test('Ponto distingue captura, identificação, confirmação e registro', () => {
  assert.match(kiosk, /Capturando rosto/)
  assert.match(kiosk, /PontoRecognitionStage\.IDENTIFICANDO/)
  assert.match(kiosk, /Identificando rosto/)
  assert.match(kiosk, /PontoRecognitionStage\.CONFIRMANDO_IDENTIDADE/)
  assert.match(kiosk, /Confirmando identidade/)
  assert.match(kiosk, /PontoRecognitionStage\.REGISTRANDO_PONTO/)
  assert.match(kiosk, /Confirmando seu ponto/)
})

test('feedback do ponto preserva durações e prioridade dos bloqueios', () => {
  assert.match(flow, /POINT_RECEIPT_VISIBLE_MILLIS = 3_000L/)
  assert.match(flow, /POINT_BLOCKED_VISIBLE_MILLIS = 2_000L/)
  assert.match(flow, /USED_BREAK_WARNING_VISIBLE_MILLIS = 5_000L/)
  assert.match(flow, /DAILY_EXHAUSTED/)
  assert.match(flow, /PERIOD_USED/)
  assert.match(flow, /OUTSIDE_WINDOW/)
  assert.match(flow, /PAUSAS_DO_DIA_JA_UTILIZADAS/)
  assert.match(flow, /PAUSA_PERIODO_JA_UTILIZADA/)
  assert.match(flow, /Pausas do dia já utilizadas/)
  assert.match(flow, /Fora do horário permitido/)
})

test('comprovante usa linguagem direta sem alterar registro automático', () => {
  assert.match(flow, /Pausa iniciada/)
  assert.match(flow, /Retorno registrado/)
  assert.match(flow, /Próxima pessoa em instantes/)
  assert.match(flow, /viewModel\.confirmarIdentidade\(\)/)
  assert.match(flow, /viewModel\.concluirComprovante\(\)/)
  assert.match(flow, /viewModel\.rejeitarIdentidade\(\)/)
})

test('release da atualização de Ponto é 0.13.0', () => {
  assert.match(gradle, /versionCode = 34/)
  assert.match(gradle, /versionName = "0\.13\.0"/)
  assert.match(gradle, /play-services-tflite-java:16\.5\.0/)
  assert.doesNotMatch(gradle, /play-services-tflite-gpu/)
})
