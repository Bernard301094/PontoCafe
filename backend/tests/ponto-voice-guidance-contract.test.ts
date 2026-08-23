import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const voice = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/voice/PontoVoiceGuidance.kt', import.meta.url),
  'utf8',
)
const neuralVoice = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/voice/PontoNeuralVoice.kt', import.meta.url),
  'utf8',
)
const normalizer = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/voice/PontoVoiceTextNormalizer.kt', import.meta.url),
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
const gradle = readFileSync(new URL('../../app/build.gradle.kts', import.meta.url), 'utf8')
const settings = readFileSync(new URL('../../settings.gradle.kts', import.meta.url), 'utf8')

test('voz própria usa sherpa-onnx e Piper pt-BR offline com modelo verificado', () => {
  assert.match(settings, /https:\/\/jitpack\.io/)
  assert.match(gradle, /com\.github\.k2-fsa:sherpa-onnx:v1\.13\.6/)
  assert.match(gradle, /org\.apache\.commons:commons-compress:1\.27\.1/)
  assert.match(neuralVoice, /OfflineTts/)
  assert.match(neuralVoice, /OfflineTtsVitsModelConfig/)
  assert.match(neuralVoice, /vits-piper-pt_BR-faber-medium/)
  assert.match(neuralVoice, /pt_BR-faber-medium\.onnx/)
  assert.match(neuralVoice, /39fb6b580d6d40a3230b7a9d0851d282074537b9694892b5b3cd90ff87c6cbb3/)
  assert.match(neuralVoice, /GenerationConfig/)
  assert.match(neuralVoice, /AudioTrack/)
  assert.match(neuralVoice, /context\.filesDir/)
  assert.match(neuralVoice, /sha256\(model\)/)
  assert.match(neuralVoice, /MAX_ARCHIVE_BYTES/)
  assert.match(neuralVoice, /MAX_EXTRACTED_BYTES/)
})

test('runtime neural evita fallback prolongado por erro transitório de AudioTrack', () => {
  assert.match(neuralVoice, /RETRY_AFTER_MILLIS = 30_000L/)
  assert.match(neuralVoice, /VOICE_PLAYBACK_FAILED/)
  assert.match(neuralVoice, /track\.play\(\)[\s\S]*track\.write/)
  assert.match(neuralVoice, /WRITE_CHUNK_SAMPLES/)
  assert.match(neuralVoice, /Falha de AudioTrack não invalida o modelo\/engine/)
  assert.match(neuralVoice, /numThreads = 1/)
  assert.match(neuralVoice, /provider = "cpu"/)
})

test('voz neural mantém Android TTS pt-BR como fallback fail-open e respeita acessibilidade', () => {
  assert.match(voice, /PontoNeuralVoiceRuntime\.prewarm/)
  assert.match(voice, /PontoNeuralVoiceRuntime\.speak/)
  assert.match(voice, /PontoNeuralSpeechDecision\.UNAVAILABLE/)
  assert.match(voice, /TextToSpeech/)
  assert.match(voice, /Locale\.forLanguageTag\("pt-BR"\)/)
  assert.match(voice, /runCatching/)
  assert.match(voice, /screenReaderOwnsSpeech/)
  assert.match(voice, /isTouchExplorationEnabled/)
  assert.match(voice, /PontoVoiceGate/)
  assert.match(voice, /MAX_INSTRUCTIONS_PER_SESSION = 3/)
  assert.match(voice, /cooldownMillis/)
  assert.match(voice, /stabilityDelayMillis/)
  assert.doesNotMatch(voice, /RECORD_AUDIO|SpeechRecognizer|MediaRecorder/)
})

test('horários são normalizados somente na fala para pt-BR mais natural', () => {
  assert.match(voice, /PontoVoiceTextNormalizer\.normalize/)
  assert.match(normalizer, /clockPattern/)
  assert.match(normalizer, /uma hora/)
  assert.match(normalizer, /quarenta/)
  assert.match(normalizer, /cinquenta/)
})

test('liveness passivo fica silencioso salvo orientação estável e fallback ativo', () => {
  assert.match(kiosk, /activeFallback/)
  assert.match(kiosk, /!activeFallback -> PontoVoiceKioskCue\.LOOK_AT_CAMERA/)
  assert.match(kiosk, /PontoVoiceKioskCue\.BLINK/)
  assert.match(kiosk, /PontoVoiceKioskCue\.OPEN_EYES/)
  assert.match(kiosk, /PontoVoiceKioskCue\.TURN_LEFT/)
  assert.match(kiosk, /PontoVoiceKioskCue\.TURN_RIGHT/)
  assert.match(kiosk, /PontoVoiceKioskCue\.MULTIPLE_FACES/)
  assert.match(kiosk, /PontoVoiceKioskCue\.FACE_NOT_RECOGNIZED/)
  assert.match(kiosk, /delay\(prompt\.stabilityDelayMillis\)/)
  assert.match(kiosk, /sessionKey = "scan:\$\{state\.scanCycle\}"/)
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

test('guia facial usa readiness real e feedback neutro vermelho verde mais rápido', () => {
  assert.match(kiosk, /observation\.faceCount == 1 && observation\.isIdentificationReady/)
  assert.match(kiosk, /faceDetected = detectedFaces == 1/)
  assert.match(kiosk, /positioned = facePositioned/)
  assert.match(guide, /FACE_GUIDE_READY_STABILITY_MILLIS = 180L/)
  assert.match(guide, /!faceDetected -> Color\.White/)
  assert.match(guide, /!stablePositioned -> Color\(0xFFFF5C5C\)/)
  assert.match(guide, /else -> Color\(0xFF49E39A\)/)
  assert.match(guide, /delay\(FACE_GUIDE_READY_STABILITY_MILLIS\)/)
})

test('feature de voz não altera gates biométricos nem limiares de identidade', () => {
  assert.match(capturePolicy, /MAX_IDENTIFICATION_YAW = 12f/)
  assert.match(capturePolicy, /MAX_IDENTIFICATION_PITCH = 12f/)
  assert.match(capturePolicy, /MAX_IDENTIFICATION_ROLL = 8f/)
  assert.match(biometricPolicy, /MINIMUM_INTRA_USER_SIMILARITY = 0\.60/)
  assert.doesNotMatch(voice, /faceThreshold|limiar|margem|cosine|embedding/)
  assert.doesNotMatch(neuralVoice, /faceThreshold|margem|cosine|embedding/)
  assert.doesNotMatch(guide, /faceThreshold|limiar|margem|cosine|embedding/)
})
