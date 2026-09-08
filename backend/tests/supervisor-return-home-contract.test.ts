import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const alerts = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/SupervisorLiveAlerts.kt', import.meta.url),
  'utf8',
)
const viewModel = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/SupervisorViewModel.kt', import.meta.url),
  'utf8',
)

test('início do Supervisor recupera e mantém o último retorno registrado', () => {
  // A busca saiu do composable e foi para o ViewModel. Um composable que cria
  // repositório e chama a rede refaz esse trabalho a cada recomposição e morre
  // junto com a tela — o último retorno precisa sobreviver à troca de aba.
  assert.match(viewModel, /val ultimoRetorno: PausaSupervisor\? = null/)
  assert.match(viewModel, /fun atualizarUltimoRetornoSilencioso\(\)/)
  assert.match(viewModel, /repository\.historico\(LocalDate\.now\(\)\.toString\(\)\)/)
  assert.match(viewModel, /ultimoRetorno = historico\.ultimoRetorno\(\)/)
  assert.match(viewModel, /filter \{ !it\.fimLocal\.isNullOrBlank\(\) \}/)

  // O composable apenas recebe o valor pronto.
  assert.match(alerts, /latestReturn: PausaSupervisor\? = null/)
  assert.match(alerts, /Último retorno registrado/)
  assert.match(alerts, /return transientAlert \?: latestReturnAlert/)
  assert.doesNotMatch(alerts, /SupervisorApiClient/)
})

test('alertas transitórios expiram separadamente do retorno persistente', () => {
  assert.match(alerts, /TRANSIENT_ALERT_DURATION_MILLIS/)
  assert.match(alerts, /if \(transientAlert\?\.id == currentId\) transientAlert = null/)

  // O retorno persistente passou a ser derivado (`val ... = remember(...)`) em
  // vez de um `var` mutável. Assim o timer do alerta transitório não tem como
  // apagá-lo nem por engano: não existe atribuição possível.
  assert.match(alerts, /val latestReturnAlert = remember\(/)
  assert.doesNotMatch(alerts, /var latestReturnAlert/)
  assert.doesNotMatch(alerts, /latestReturnAlert = null/)
})
