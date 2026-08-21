import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const localBiometric = readFileSync(new URL('../src/routes/local-biometric-routes.ts', import.meta.url), 'utf8')
const fastPonto = readFileSync(new URL('../src/routes/fast-ponto-routes.ts', import.meta.url), 'utf8')
const pontoRoutes = readFileSync(new URL('../src/routes/ponto-routes.ts', import.meta.url), 'utf8')
const offlineRoutes = readFileSync(new URL('../src/routes/offline-routes.ts', import.meta.url), 'utf8')
const offlineStore = readFileSync(new URL('../../app/src/main/java/com/pontocafe/app/data/SecurePontoOfflineStore.kt', import.meta.url), 'utf8')
const viewModel = readFileSync(new URL('../../app/src/main/java/com/pontocafe/app/PontoCafeViewModel.kt', import.meta.url), 'utf8')
const flowHost = readFileSync(new URL('../../app/src/main/java/com/pontocafe/app/ui/PontoFlowHost.kt', import.meta.url), 'utf8')
const kioskScreen = readFileSync(new URL('../../app/src/main/java/com/pontocafe/app/ui/FaceKioskScreen.kt', import.meta.url), 'utf8')
const auditScreen = readFileSync(new URL('../../app/src/main/java/com/pontocafe/app/ui/AdminAuditScreen.kt', import.meta.url), 'utf8')

test('duas pausas consumidas têm prioridade sobre fora do horário no fluxo online', () => {
  const exhausted = localBiometric.indexOf("PAUSAS_DO_DIA_JA_UTILIZADAS")
  const outOfHours = localBiometric.indexOf("motivo: 'FORA_HORARIO'")

  assert.ok(exhausted >= 0, 'falta estado terminal para 2/2 pausas usadas')
  assert.ok(outOfHours >= 0, 'falta estado fora do horário')
  assert.ok(exhausted < outOfHours, '2/2 pausas usadas deve ser decidido antes de FORA_HORARIO')
  assert.match(localBiometric, /Não há mais pausa disponível para hoje/)
  assert.match(localBiometric, /acaoSugerida: 'BLOQUEADO'/)
})

test('fast path não mascara 2/2 pausas usadas como fora do horário', () => {
  const exhausted = fastPonto.indexOf("PAUSAS_DO_DIA_JA_UTILIZADAS")
  const outOfHours = fastPonto.indexOf("motivo: 'FORA_HORARIO'")

  assert.ok(exhausted >= 0)
  assert.ok(outOfHours >= 0)
  assert.ok(exhausted < outOfHours)
})

test('offline verifica manhã e tarde mesmo quando não existe janela ativa', () => {
  assert.match(offlineStore, /data class LocalCompletedPause/)
  assert.match(offlineStore, /completedPauseToday/)
  assert.match(viewModel, /completedPauseToday\(colaborador\.id, "MANHA"\)/)
  assert.match(viewModel, /completedPauseToday\(colaborador\.id, "TARDE"\)/)
  assert.match(viewModel, /PAUSAS_DO_DIA_JA_UTILIZADAS/)
  assert.match(viewModel, /Não há mais pausa disponível para hoje/)

  const exhausted = viewModel.indexOf('PAUSAS_DO_DIA_JA_UTILIZADAS')
  const currentRule = viewModel.indexOf('val rule = offlineStore.currentRule()', exhausted)
  assert.ok(exhausted >= 0)
  assert.ok(currentRule > exhausted, 'o bloqueio 2/2 deve acontecer antes de depender da janela atual')
})

test('overlay do Ponto prioriza explicitamente o estado 2/2', () => {
  assert.match(flowHost, /serverDayExhausted/)
  assert.match(flowHost, /localDayExhausted/)
  assert.match(flowHost, /PAUSAS_DO_DIA_JA_UTILIZADAS/)
  assert.match(flowHost, /Pausas do dia já utilizadas/)
  assert.match(flowHost, /Pausas de hoje já utilizadas \(2\/2\)/)
  assert.match(flowHost, /reason = PointBlockReason\.DAILY_EXHAUSTED/)
  assert.match(flowHost, /USED_BREAK_WARNING_VISIBLE_MILLIS = 5_000L/)

  const exhaustedOverlay = flowHost.indexOf('dayExhausted -> FastPointBlockedOverlay')
  const genericBlocked = flowHost.indexOf('identificacao?.acaoSugerida == "BLOQUEADO"')
  assert.ok(exhaustedOverlay >= 0)
  assert.ok(genericBlocked > exhaustedOverlay, 'overlay 2/2 deve vir antes do bloqueio genérico')
})

test('mantém proteção do mesmo período e auditoria em todos os caminhos', () => {
  assert.match(localBiometric, /PAUSA_PERIODO_JA_UTILIZADA/)
  assert.match(localBiometric, /TENTATIVA_PONTO_REPETIDA/)
  assert.match(pontoRoutes, /PAUSA_PERIODO_JA_UTILIZADA/)
  assert.match(pontoRoutes, /TENTATIVA_PONTO_REPETIDA/)
  assert.match(offlineRoutes, /TENTATIVA_PONTO_REPETIDA/)
  assert.match(offlineRoutes, /origem: 'OFFLINE'/)
})

test('kiosk mostra a mensagem operacional atual sem depender de tela legada', () => {
  assert.match(kioskScreen, /error\?\.let \{ message/)
  assert.match(kioskScreen, /message/)
  assert.doesNotMatch(kioskScreen, /IdentityConfirmationScreen/)
  assert.doesNotMatch(flowHost, /IdentityConfirmationScreen/)
})

test('auditoria administrativa continua identificando tentativa repetida', () => {
  assert.match(auditScreen, /TENTATIVA_PONTO_REPETIDA/)
  assert.match(auditScreen, /Tentativa repetida de pausa bloqueada/)
  assert.match(auditScreen, /colaboradorNome/)
})
