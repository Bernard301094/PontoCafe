import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const guidance = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/voice/PontoVoiceGuidance.kt', import.meta.url),
  'utf8',
)
const voiceStatus = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/PontoVoiceOperationalStatus.kt', import.meta.url),
  'utf8',
)
const pontoRoutes = readFileSync(
  new URL('../src/routes/ponto-routes.ts', import.meta.url),
  'utf8',
)
const pontoStatusRoutes = readFileSync(
  new URL('../src/routes/ponto-status-routes.ts', import.meta.url),
  'utf8',
)

test('dispositivo removido deixa de ser aceito pelo backend', () => {
  for (const source of [pontoRoutes, pontoStatusRoutes]) {
    assert.match(source, /where token_hash=\$1 and ativo=true limit 1/)
    assert.match(source, /Dispositivo inválido\./)
    assert.match(source, /401/)
  }
})

test('modo Ponto revalida autorização e volta ao cadastro de token após revogação', () => {
  assert.match(guidance, /DEVICE_AUTH_RECHECK_MILLIS = 10_000L/)
  assert.match(guidance, /viewModel\.atualizarConectividadeESincronizar\(\)/)
  assert.match(guidance, /DEVICE_REVOKED_ERROR_FRAGMENT/)
  assert.match(guidance, /viewModel\.removerConfiguracao\(\)/)
})

test('falha de rede temporária não é tratada localmente como revogação', () => {
  assert.doesNotMatch(guidance, /Sem conexão[\s\S]*removerConfiguracao/)
  assert.doesNotMatch(guidance, /modoOffline[\s\S]*removerConfiguracao/)
})

test('resultado aguarda brevemente engine neural instalado antes do fallback Android', () => {
  assert.match(guidance, /NEURAL_RESULT_WAIT_MILLIS = 2_500L/)
  assert.match(guidance, /diagnostics\.modelInstalled/)
  assert.match(guidance, /PontoNeuralVoiceRuntime\.retryNow/)
  assert.match(guidance, /PontoNeuralVoiceAvailability\.READY/)
})

test('Supervisor possui teste neural sem passar pelo Android TextToSpeech', () => {
  assert.match(voiceStatus, /Testar voz PontoCafe/)
  assert.match(voiceStatus, /PontoNeuralVoiceRuntime\.speak/)
  assert.match(voiceStatus, /Voz Ponto Café ativada\./)
  assert.match(voiceStatus, /PontoNeuralSpeechDecision\.UNAVAILABLE/)
  assert.doesNotMatch(voiceStatus, /TextToSpeech/)
  assert.doesNotMatch(voiceStatus, /PontoVoiceRuntime\.speak/)
})
