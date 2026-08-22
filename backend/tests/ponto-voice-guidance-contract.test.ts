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
const guide = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/KioskFaceGuide.kt', import.meta.url),
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
  assert.match(voice, /PontoVoiceGate/)
  assert.match(voice, /MAX_INSTRUCTIONS_PER_SESSION = 3/)
  assert.match(voice, /cooldownMillis/)
  assert.match(voice, /stabilityDelayMillis/)
  assert.doesNotMatch(voice, /RECORD_AUDIO|SpeechRecognizer|MediaRecorder/)
})

test('liveness fala só ações estáveis e limita repetição por ciclo', () => {
  assert.match(kiosk, /PontoVoiceKioskCue\.BLINK/)
  assert.match(kiosk, /PontoVoiceKioskCue\.OPEN_EYES/)
  assert.match(kiosk, /PontoVoiceKioskCue\.TURN_LEFT/)
  assert.match(kiosk, /PontoVoiceKioskCue\.TURN_RIGHT/)
  assert.match(kiosk, /PontoVoiceKioskCue\.CENTER_FACE/)
  assert.match(kiosk, /PontoVoiceKioskCue\.MULTIPLE_FACES/)
  assert.match(kiosk, /PontoVoiceKioskCue\.FACE_NOT_RECOGNIZED/)
  assert.match(kiosk, /delay\(prompt\.stabilityDelayMillis\)/)
  assert.match(kiosk, /sessionKey = "scan:\$\{state\.scanCycle\}"/)
  assert.match(kiosk, /challenge == KioskLivenessChallenge\.BLINK/)
  assert.match(kiosk, /challenge == KioskLivenessChallenge\.TURN_LEFT/)
  assert.match(voice, /NO_FACE[\s\S]*stabilityDelayMillis = 5_000L/)
  assert.match(voice, /LOOK_AT_CAMERA[\s\S]*stabilityDelayMillis = 1_800L/)
})

test('processamento normal fica silencioso e resultados continuam falados', () => {
  assert.match(activity, /PontoVoiceGuidanceEffect\(viewModel = vm\)/)
  assert.match(activity, /PontoVoiceRuntime\.shutdown\(\)/)
  assert.match(voice, /PontoVoicePromptPolicy\.receipt\(comprovante\)/)
  assert.match(voice, /PontoVoicePromptPolicy\.blocked\(identificacao\.motivo\)/)
  assert.doesNotMatch(voice, /registrationInProgress/)
  assert.doesNotMatch(voice, /PontoRecognitionStage\.REGISTRANDO_PONTO/)
})

test('guia facial usa posição real e feedback neutro vermelho verde estável', () => {
  assert.match(kiosk, /observation\.faceCount == 1 && observation\.isWellPositioned/)
  assert.match(kiosk, /faceDetected = detectedFaces == 1/)
  assert.match(kiosk, /positioned = facePositioned/)
  assert.match(guide, /FACE_GUIDE_READY_STABILITY_MILLIS = 350L/)
  assert.match(guide, /!faceDetected -> Color\.White/)
  assert.match(guide, /!stablePositioned -> Color\(0xFFFF5C5C\)/)
  assert.match(guide, /else -> Color\(0xFF49E39A\)/)
  assert.match(guide, /delay\(FACE_GUIDE_READY_STABILITY_MILLIS\)/)
})

test('feature de voz e guia não alteram gates biométricos nem limiares de identidade', () => {
  assert.match(capturePolicy, /MAX_IDENTIFICATION_YAW = 12f/)
  assert.match(capturePolicy, /MAX_IDENTIFICATION_PITCH = 12f/)
  assert.match(capturePolicy, /MAX_IDENTIFICATION_ROLL = 8f/)
  assert.match(biometricPolicy, /MINIMUM_INTRA_USER_SIMILARITY = 0\.60/)
  assert.doesNotMatch(voice, /faceThreshold|limiar|margem|cosine|embedding/)
  assert.doesNotMatch(guide, /faceThreshold|limiar|margem|cosine|embedding/)
})
