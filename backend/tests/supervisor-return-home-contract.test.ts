import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const alerts = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/SupervisorLiveAlerts.kt', import.meta.url),
  'utf8',
)

test('início do Supervisor recupera e mantém o último retorno registrado', () => {
  assert.match(alerts, /SupervisorApiClient\.create/)
  assert.match(alerts, /historyRepository\.historico\(\)/)
  assert.match(alerts, /filter \{ !it\.fimLocal\.isNullOrBlank\(\) \}/)
  assert.match(alerts, /Último retorno registrado/)
  assert.match(alerts, /return transientAlert \?: latestReturnAlert/)
})

test('alertas transitórios expiram separadamente do retorno persistente', () => {
  assert.match(alerts, /TRANSIENT_ALERT_DURATION_MILLIS/)
  assert.match(alerts, /if \(transientAlert\?\.id == currentId\) transientAlert = null/)
  assert.match(alerts, /var latestReturnAlert by remember/)
  assert.doesNotMatch(alerts, /if \(latestReturnAlert\?\.id == currentId\) latestReturnAlert = null/)
})
