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
const material = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/MaterialDesignSystem.kt', import.meta.url),
  'utf8',
)
const voice = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/voice/PontoNeuralVoice.kt', import.meta.url),
  'utf8',
)
const capturePolicy = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/domain/AccessCode.kt', import.meta.url),
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

test('feedback do Ponto diferencia confirmado, offline, limite excedido e recusa', () => {
  // O comprovante não pode dizer a mesma coisa nos quatro casos: quem
  // excedeu, quem registrou sem rede e quem foi recusado precisam de ações
  // diferentes ao sair do quiosque.
  assert.match(pontoFlow, /if \(comprovante\.excedeuLimite\) Icons\.Default\.Warning else Icons\.Default\.CheckCircle/)
  assert.match(pontoFlow, /comprovante\.excedeuLimite -> PontoCafeTone\.WARNING/)
  assert.match(pontoFlow, /Retorno acima do limite/)
  assert.match(pontoFlow, /Registrado sem conexão/)
  assert.match(pontoFlow, /comprovante\.pendenteSincronizacao/)
  assert.match(pontoFlow, /Fora do horário habitual/)
  assert.match(pontoFlow, /Código não aceito/)
  assert.match(pontoFlow, /tone = PontoCafeTone\.DANGER/)
})

test('microinterações permanecem curtas e acessíveis', () => {
  assert.match(material, /collectIsPressedAsState/)
  assert.match(material, /targetValue = if \(pressed\) 0\.975f else 1f/)
  assert.match(material, /durationMillis = if \(pressed\) PontoCafeMotion\.Quick else PontoCafeMotion\.Standard/)
  assert.match(material, /MotionReveal/)
  // O háptico dos botões passa pelo objeto central, não pela API do Android.
  assert.match(material, /PontoHaptics\.tap\(view\)/)
  assert.doesNotMatch(material, /performHapticFeedback/)
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

test('o alfabeto do código de acesso evita os caracteres que se confundem', () => {
  // I, L, O e U ficam de fora: os três primeiros somem contra 1 e 0 num papel
  // escrito à pressa, e o U evita que um sorteio produza palavra ofensiva.
  assert.match(capturePolicy, /const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"/)
  assert.match(capturePolicy, /'I', 'L' -> '1'/)
  assert.match(capturePolicy, /'O' -> '0'/)
  assert.doesNotMatch(supervisorAlerts, /faceThreshold|cosine|embedding/)
  assert.doesNotMatch(material, /faceThreshold|cosine|embedding/)
})
