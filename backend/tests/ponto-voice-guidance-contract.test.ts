import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const voice = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/voice/PontoVoiceGuidance.kt', import.meta.url),
  'utf8',
)
const kiosk = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/FaceKioskScreen.kt', import.meta.url),
  'utf8',
)
const activity = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/MainActivity.kt', import.meta.url),
  'utf8',
)
const capturePolicy = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/camera/FaceCapturePolicy.kt', import.meta.url),
  'utf8',
)
const biometricPolicy = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/data/BiometricSecurityPolicy.kt', import.meta.url),
  'utf8',
)

test('voz do Ponto usa TTS local pt-BR e falha sem bloquear o registro', () => {
  assert.match(voice, /TextToSpeech/)
  assert.match(voice, /Locale\.forLanguageTag\("pt-BR"\)/)
  assert.match(voice, /runCatching \{ current\.speak/)
  assert.match(voice, /screenReaderOwnsSpeech/)
  assert.match(voice, /isTouchExplorationEnabled/)
  assert.match(voice, /REPEAT_SUPPRESSION_MILLIS/)
  assert.doesNotMatch(voice, /RECORD_AUDIO|SpeechRecognizer|MediaRecorder/)
})

test('liveness fala as mesmas ações já exigidas visualmente', () => {
  assert.match(kiosk, /PontoVoiceKioskCue\.BLINK/)
  assert.match(kiosk, /PontoVoiceKioskCue\.OPEN_EYES/)
  assert.match(kiosk, /PontoVoiceKioskCue\.TURN_LEFT/)
  assert.match(kiosk, /PontoVoiceKioskCue\.TURN_RIGHT/)
  assert.match(kiosk, /PontoVoiceKioskCue\.CENTER_FACE/)
  assert.match(kiosk, /PontoVoiceKioskCue\.MULTIPLE_FACES/)
  assert.match(kiosk, /PontoVoiceKioskCue\.FACE_NOT_RECOGNIZED/)
  assert.match(kiosk, /PontoVoiceRuntime\.speak\(context, PontoVoicePromptPolicy\.kiosk\(cue\)\)/)
  assert.match(kiosk, /challenge == KioskLivenessChallenge\.BLINK/)
  assert.match(kiosk, /challenge == KioskLivenessChallenge\.TURN_LEFT/)
})

test('resultados do ponto e encerramento do TTS ficam no lifecycle da Activity', () => {
  assert.match(activity, /PontoVoiceGuidanceEffect\(viewModel = vm\)/)
  assert.match(activity, /PontoVoiceRuntime\.shutdown\(\)/)
  assert.match(voice, /PontoVoicePromptPolicy\.receipt\(comprovante\)/)
  assert.match(voice, /PontoVoicePromptPolicy\.blocked\(identificacao\.motivo\)/)
  assert.match(voice, /PontoRecognitionStage\.REGISTRANDO_PONTO/)
})

test('feature de voz não altera gates biométricos nem limiares de identidade', () => {
  assert.match(capturePolicy, /MAX_IDENTIFICATION_YAW = 12f/)
  assert.match(capturePolicy, /MAX_IDENTIFICATION_PITCH = 12f/)
  assert.match(capturePolicy, /MAX_IDENTIFICATION_ROLL = 8f/)
  assert.match(biometricPolicy, /MINIMUM_INTRA_USER_SIMILARITY = 0\.60/)
  assert.doesNotMatch(voice, /faceThreshold|limiar|margem|cosine|embedding/)
})
