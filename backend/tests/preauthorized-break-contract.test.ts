import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const here = dirname(fileURLToPath(import.meta.url))
const repoRoot = resolve(here, '..', '..')
const read = (relativePath: string) => readFileSync(resolve(repoRoot, relativePath), 'utf8')

test('Supervisor libera a pausa previamente sem expor código no fluxo novo', () => {
  const route = read('backend/src/routes/authorization-routes.ts')
  const screen = read('app/src/main/java/com/pontocafe/app/ui/SupervisorAuthorizationScreen.kt')

  assert.match(route, /LIBERAR_PAUSA_FORA_HORARIO/)
  assert.match(route, /Uma pessoa só pode ter uma liberação prévia ativa por vez/)
  assert.match(route, /Pausa liberada previamente/)
  assert.match(screen, /Nenhum código precisa ser informado no terminal/)
  assert.match(screen, /Liberar pausa/)
  assert.match(screen, /Pausa liberada/)
  assert.doesNotMatch(screen, /Gerar código de 6 dígitos/)
})

test('Ponto detecta liberação ativa e bloqueia quem está fora do horário sem liberação', () => {
  const pontoRoute = read('backend/src/routes/ponto-routes.ts')
  const localRoute = read('backend/src/routes/local-biometric-routes.ts')
  const identityScreen = read('app/src/main/java/com/pontocafe/app/ui/IdentityConfirmationScreen.kt')

  for (const route of [pontoRoute, localRoute]) {
    assert.match(route, /AUTORIZACAO_PREVIA/)
    assert.match(route, /FORA_HORARIO_NAO_LIBERADO/)
    assert.match(route, /a\.usado_em is null/)
    assert.match(route, /a\.cancelada_em is null/)
    assert.match(route, /a\.expira_em>now\(\)/)
  }

  assert.match(pontoRoute, /Pausa não liberada\. Você está fora do horário permitido/)
  assert.match(pontoRoute, /update autorizacoes set usado_em=now\(\)/)
  assert.doesNotMatch(pontoRoute, /hashAuthorizationCode/)

  assert.match(identityScreen, /Pausa não liberada/)
  assert.match(identityScreen, /Procure seu Supervisor/)
  assert.match(identityScreen, /Pausa liberada/)
  assert.match(identityScreen, /Voltar ao Ponto/)
})

test('liberação prévia tem janela operacional de dez minutos por padrão', () => {
  const config = read('backend/src/config.ts')
  const workerConfig = read('backend/wrangler.jsonc')
  const rootWorkerConfig = read('wrangler.jsonc')

  assert.match(config, /AUTHORIZATION_TTL_SECONDS', 600/)
  assert.match(workerConfig, /"AUTHORIZATION_TTL_SECONDS": "600"/)
  assert.match(rootWorkerConfig, /"AUTHORIZATION_TTL_SECONDS": "600"/)
})
