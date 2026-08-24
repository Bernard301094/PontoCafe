import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

function read(path: string): string {
  return readFileSync(new URL(path, import.meta.url), 'utf8')
}

const guidance = read('../../app/src/main/java/com/pontocafe/app/voice/PontoVoiceGuidance.kt')
const neuralVoice = read('../../app/src/main/java/com/pontocafe/app/voice/PontoNeuralVoice.kt')
const voiceStatus = read('../../app/src/main/java/com/pontocafe/app/ui/PontoVoiceOperationalStatus.kt')
const startupProvisioning = read('../../app/src/main/java/com/pontocafe/app/ui/PontoStartupProvisioningScreen.kt')
const viewModel = read('../../app/src/main/java/com/pontocafe/app/PontoCafeViewModel.kt')
const apiClient = read('../../app/src/main/java/com/pontocafe/app/data/ApiClient.kt')
const authRuntime = read('../../app/src/main/java/com/pontocafe/app/data/DeviceAuthorizationRuntime.kt')
const offlineStore = read('../../app/src/main/java/com/pontocafe/app/data/SecurePontoOfflineStore.kt')
const mainActivity = read('../../app/src/main/java/com/pontocafe/app/MainActivity.kt')
const supervisorScreen = read('../../app/src/main/java/com/pontocafe/app/ui/SupervisorOperationScreen.kt')
const appHealth = read('../../app/src/main/java/com/pontocafe/app/data/AppHealthMonitor.kt')
const application = read('../src/application.ts')
const shared = read('../src/routes/shared.ts')

test('Worker expõe contrato estável para credencial de dispositivo inválida', () => {
  assert.match(shared, /DEVICE_AUTH_INVALID_CODE\s*=\s*'DEVICE_AUTH_INVALID'/)
  assert.match(shared, /where token_hash=\$1 and ativo=true limit 1/)
  assert.match(shared, /deviceAuthInvalidPayload/)
  assert.match(application, /app\.use\('\/ponto\/\*', deviceAuthContractMiddleware\)/)
  assert.match(shared, /response\.status !== 401/)
  assert.doesNotMatch(shared, /response\.status !== 403/)
})

test('Android observa DEVICE_AUTH_INVALID sem consumir o corpo Retrofit', () => {
  assert.match(authRuntime, /response\.peekBody\(MAX_ERROR_BODY_BYTES\)/)
  assert.match(authRuntime, /response\.code != 401/)
  assert.match(authRuntime, /DEVICE_AUTH_INVALID_CODE/)
  assert.match(apiClient, /addInterceptor\(DeviceAuthResponseInterceptor\(\)\)/)
  assert.match(apiClient, /error is HttpException && error\.code\(\) == 401/)
  assert.doesNotMatch(apiClient, /error\.code\(\) == 403/)
})

test('token local não autoriza câmera antes da validação autoritativa', () => {
  assert.match(viewModel, /deviceConfigured = false,[\s\S]*DeviceAuthorizationState\.CHECKING/)
  assert.match(viewModel, /validarAutorizacaoDoDispositivo\(bloquearDuranteValidacao = true\)/)
  assert.match(mainActivity, /Lifecycle\.Event\.ON_RESUME[\s\S]*validarAutorizacaoDoDispositivo\(bloquearDuranteValidacao = true\)/)
  assert.match(mainActivity, /PONTO_IDLE_AUTH_RECHECK_MILLIS = 120_000L/)
  assert.doesNotMatch(guidance, /DEVICE_AUTH_RECHECK_MILLIS/)
  assert.doesNotMatch(guidance, /não está mais autorizado/)
  assert.doesNotMatch(guidance, /removerConfiguracao\(\)/)
})

test('CHECKING usa tela de validação e nunca pisca formulário de token', () => {
  const checkingBranch = mainActivity.indexOf(
    'state.deviceAuthorizationState == DeviceAuthorizationState.CHECKING',
  )
  const setupBranch = mainActivity.indexOf('!state.deviceConfigured -> {')

  assert.ok(checkingBranch >= 0, 'branch CHECKING precisa existir')
  assert.ok(setupBranch > checkingBranch, 'CHECKING precisa ser resolvido antes do DeviceSetupScreen')
  assert.match(mainActivity, /DeviceAuthorizationState\.CHECKING[\s\S]*PontoDeviceAuthorizationScreen/)
  assert.match(startupProvisioning, /Validando dispositivo/)
  assert.match(startupProvisioning, /código de ativação não será solicitado/)
})

test('revogação remota preserva e quarentena pendências offline', () => {
  assert.match(viewModel, /handleRemoteRevocation\(\)/)
  assert.match(viewModel, /offlineStore\.quarantinePendingEvents\("DEVICE_AUTH_INVALID"\)/)
  assert.match(offlineStore, /fun quarantinePendingEvents\(reason: String\)/)
  assert.match(offlineStore, /eventosEmQuarentena = true/)
  assert.match(viewModel, /hasQuarantinedPendingEvents\(\)/)
  assert.match(viewModel, /não serão enviados com esta credencial/)

  const remoteHandler = viewModel.slice(
    viewModel.indexOf('private suspend fun handleRemoteRevocation()'),
    viewModel.indexOf('fun atualizarConectividadeESincronizar()'),
  )
  assert.doesNotMatch(remoteHandler, /offlineStore\.clear\(\)/)
})

test('teste neural só confirma sucesso após PlaybackCompleted', () => {
  assert.match(neuralVoice, /data object PlaybackCompleted/)
  assert.match(neuralVoice, /PontoNeuralVoiceFailureStage\.SYNTHESIS/)
  assert.match(neuralVoice, /PontoNeuralVoiceFailureStage\.PLAYBACK/)
  assert.match(neuralVoice, /PontoSpeechBackend\.NEURAL_PONTOCAFE/)
  assert.match(voiceStatus, /PontoNeuralSpeechEvent\.PlaybackCompleted/)
  assert.match(voiceStatus, /testSucceeded = true/)
  assert.match(voiceStatus, /ACCEPTED means queued, never successful playback/)
  assert.doesNotMatch(voiceStatus, /TextToSpeech/)
  assert.doesNotMatch(voiceStatus, /PontoVoiceRuntime\.speak/)
})

test('instalação do Ponto baixa voz natural, valida playback real e só então libera uso persistente', () => {
  assert.match(mainActivity, /!naturalVoiceReadyForSession[\s\S]*PontoNaturalVoiceProvisioningScreen/)
  assert.match(startupProvisioning, /PontoNeuralVoiceRuntime\.prewarm\(appContext\)/)
  assert.match(startupProvisioning, /PontoNeuralVoiceRuntime\.speak\(/)
  assert.match(
    startupProvisioning,
    /PontoNeuralSpeechEvent\.PlaybackCompleted[\s\S]*markNaturalVoiceProvisioned\(appContext\)/,
  )
  assert.match(startupProvisioning, /verified_voice_version/)
  assert.match(startupProvisioning, /Usar Ponto temporariamente com voz do Android/)
  assert.doesNotMatch(startupProvisioning, /PontoVoiceRuntime\.speak/)
  assert.match(neuralVoice, /ensureModelInstalled\(context\)/)
  assert.match(neuralVoice, /VOICE_MODEL_INSTALLED/)
})

test('modelo neural instalado pode enfileirar fala atrás de PREPARING', () => {
  assert.match(neuralVoice, /canQueueBehindPreparation/)
  assert.match(neuralVoice, /state == NeuralVoiceState\.PREPARING/)
  assert.match(neuralVoice, /emitEvent\(onEvent, PontoNeuralSpeechEvent\.Queued\)/)
  assert.match(neuralVoice, /emitEvent\(onEvent, PontoNeuralSpeechEvent\.PlaybackCompleted\)/)
})

test('loops de Supervisor e AppHealth respeitam lifecycle', () => {
  assert.match(supervisorScreen, /repeatOnLifecycle\(Lifecycle\.State\.STARTED\)/)
  assert.match(supervisorScreen, /VOICE_DIAGNOSTICS_REFRESH_MILLIS = 1_000L/)
  assert.match(supervisorScreen, /CONNECTION_AGE_REFRESH_MILLIS = 1_000L/)
  assert.match(appHealth, /fun startStallWatchdog\(\)/)
  assert.match(appHealth, /fun stopStallWatchdog\(\)/)
  assert.match(appHealth, /HEARTBEAT_INTERVAL_MS = 2_000L/)
  assert.match(mainActivity, /override fun onStart\(\)[\s\S]*startStallWatchdog\(\)/)
  assert.match(mainActivity, /override fun onStop\(\)[\s\S]*stopStallWatchdog\(\)/)
})
