import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const localBiometric = readFileSync(new URL('../src/routes/local-biometric-routes.ts', import.meta.url), 'utf8')
const offlineRoutes = readFileSync(new URL('../src/routes/offline-routes.ts', import.meta.url), 'utf8')
const offlineStore = readFileSync(new URL('../../app/src/main/java/com/pontocafe/app/data/SecurePontoOfflineStore.kt', import.meta.url), 'utf8')
const viewModel = readFileSync(new URL('../../app/src/main/java/com/pontocafe/app/PontoCafeViewModel.kt', import.meta.url), 'utf8')
const identityScreen = readFileSync(new URL('../../app/src/main/java/com/pontocafe/app/ui/IdentityConfirmationScreen.kt', import.meta.url), 'utf8')

test('bloqueia nova pausa do mesmo periodo e registra a tentativa online', () => {
  assert.match(localBiometric, /PAUSA_PERIODO_JA_UTILIZADA/)
  assert.match(localBiometric, /acaoSugerida: 'BLOQUEADO'/)
  assert.match(localBiometric, /TENTATIVA_PONTO_REPETIDA/)
  assert.match(localBiometric, /Esta nova tentativa de bater o ponto foi registrada/)
})

test('mantem a protecao e auditoria quando o ponto opera offline', () => {
  assert.match(offlineStore, /data class LocalCompletedPause/)
  assert.match(offlineStore, /completedPauseToday/)
  assert.match(offlineStore, /Esta nova tentativa foi registrada e será enviada ao servidor/)
  assert.match(viewModel, /identificacaoOffline\(match\.colaborador, match\.score, embedding\)/)
  assert.match(viewModel, /acaoSugerida = "BLOQUEADO"/)
  assert.match(offlineRoutes, /TENTATIVA_PONTO_REPETIDA/)
  assert.match(offlineRoutes, /origem: 'OFFLINE'/)
})

test('a interface explica que a pausa ja foi usada e nao oferece nova saida', () => {
  assert.match(identityScreen, /Pausa da \$\{periodLabel\(periodo\)\.lowercase\(\)\} já utilizada/)
  assert.match(identityScreen, /Tentativa registrada/)
  assert.match(identityScreen, /Nova pausa bloqueada/)
  assert.match(identityScreen, /consulta e auditoria do Supervisor\/Administrador/)
  assert.match(identityScreen, /if \(pausaJaUtilizada \|\| bloqueadaForaHorario\)/)
})
