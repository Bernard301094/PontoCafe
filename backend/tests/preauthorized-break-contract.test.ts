import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const here = dirname(fileURLToPath(import.meta.url))
const repoRoot = resolve(here, '..', '..')
const read = (relativePath: string) => readFileSync(resolve(repoRoot, relativePath), 'utf8')

const authorizationRoute = read('backend/src/routes/authorization-routes.ts')
const pontoRoute = read('backend/src/routes/ponto-routes.ts')
const idempotentRoute = read('backend/src/routes/idempotent-ponto-mutation-routes.ts')
const fastRoute = read('backend/src/routes/fast-ponto-routes.ts')
const localRoute = read('backend/src/routes/local-biometric-routes.ts')
const pontoApi = read('app/src/main/java/com/pontocafe/app/data/ApiClient.kt')
const pontoViewModel = read('app/src/main/java/com/pontocafe/app/PontoCafeViewModel.kt')

function creationSection() {
  const start = authorizationRoute.indexOf("authorizationRoutes.post('/autorizacoes'")
  const end = authorizationRoute.indexOf("authorizationRoutes.post('/autorizacoes/cancelar'")
  assert.ok(start >= 0 && end > start)
  return authorizationRoute.slice(start, end)
}

function assertAuthorizationIsConsumedAfterPause(route: string, label: string) {
  const pauseInsert = route.indexOf('insert into pausas_cafe')
  const authorizationUpdate = route.indexOf('update autorizacoes', pauseInsert)

  assert.notEqual(pauseInsert, -1, `${label}: deve inserir a pausa`)
  assert.ok(
    authorizationUpdate > pauseInsert,
    `${label}: deve consumir a autorização somente depois de inserir a pausa`,
  )
  assert.match(route.slice(authorizationUpdate), /where id=\$1 and colaborador_id=\$2[\s\S]*usado_em is null and cancelada_em is null[\s\S]*returning id/)
  assert.match(route.slice(authorizationUpdate), /rowCount !== 1/)
}

test('Admin e Supervisor liberam a pessoa diretamente sem devolver segredo ao cliente', () => {
  const route = creationSection()
  const adminScreen = read('app/src/main/java/com/pontocafe/app/ui/AdminAuthorizationScreen.kt')
  const supervisorScreen = read('app/src/main/java/com/pontocafe/app/ui/SupervisorAuthorizationScreen.kt')

  assert.match(route, /LIBERAR_PAUSA_FORA_HORARIO/)
  assert.match(route, /Uma pessoa só pode ter uma liberação prévia ativa por vez/)
  assert.match(route, /const nonceHash = hashToken\(newToken\(\)\)/)
  assert.match(route, /liberada: true/)
  assert.match(route, /periodoDefinidoAutomaticamente: true/)
  assert.match(route, /usoUnico: true/)
  assert.doesNotMatch(route, /\bcodigo\s*:/)
  assert.doesNotMatch(route, /generateAuthorizationCode|hashAuthorizationCode/)

  for (const screen of [adminScreen, supervisorScreen]) {
    assert.match(screen, /Autorizar pausa|Liberar pausa/)
    assert.match(screen, /reconhecimento facial|bater o ponto/i)
    assert.doesNotMatch(screen, /Gerar código de 6 dígitos|Código temporário/)
  }
})

test('Ponto encontra somente a liberação pendente da pessoa reconhecida', () => {
  for (const route of [pontoRoute, localRoute]) {
    assert.match(route, /AUTORIZACAO_PREVIA/)
    assert.match(route, /a\.colaborador_id=\$1/)
    assert.match(route, /a\.usado_em is null/)
    assert.match(route, /a\.cancelada_em is null/)
    assert.match(route, /a\.expira_em>now\(\)/)
    assert.match(route, /order by a\.criado_em desc/)
  }

  assert.match(pontoRoute, /Pausa não liberada\. Você está fora do horário permitido/)
  assert.match(localRoute, /acaoSugerida: 'BLOQUEADO'/)
  assert.match(localRoute, /Solicite uma liberação prévia ao Supervisor/)
})

test('início online rejeita expirada, cancelada, usada, pessoa errada e duplicidade', () => {
  for (const route of [pontoRoute, idempotentRoute, fastRoute]) {
    assert.match(route, /a\.colaborador_id=\$1/)
    assert.match(route, /a\.usado_em is null/)
    assert.match(route, /a\.cancelada_em is null/)
    assert.match(route, /a\.expira_em>now\(\)/)
    assert.match(route, /limit 1 for update/)
    assert.match(route, /where colaborador_id=\$1 and periodo=\$2/)
    assertAuthorizationIsConsumedAfterPause(route, 'rota de início')
  }
})

test('consumo e criação da pausa são atômicos e protegidos contra corrida', () => {
  for (const route of [pontoRoute, idempotentRoute, fastRoute]) {
    assert.match(route, /transaction\(async \(client\)/)
    assert.match(route, /limit 1 for update/)
    assert.match(route, /insert into pausas_cafe/)
    assert.match(route, /set usado_em=now\(\)/)
  }
})

test('payload Android de início não envia período nem código de autorização', () => {
  const start = pontoApi.indexOf('data class IniciarPausaRequest(')
  const end = pontoApi.indexOf('data class IniciarPausaResponse(', start)
  assert.ok(start >= 0 && end > start)
  const request = pontoApi.slice(start, end)

  assert.match(request, /val operacaoId: String/)
  assert.match(request, /val colaboradorId: String/)
  assert.match(request, /val verificacaoToken: String/)
  assert.doesNotMatch(request, /codigoAutorizacao|authorizationCode|val periodo:/)
  assert.doesNotMatch(pontoViewModel, /needsAuthorization|confirmarAutorizacao|codigoAutorizacao/)
})

test('modo offline não presume nem consome liberações criadas no servidor', () => {
  assert.match(pontoViewModel, /dentroHorario = rule != null/)
  assert.match(pontoViewModel, /Sem conexão, uma pausa fora do horário não pode ser autorizada/)
  assert.match(pontoViewModel, /if \(identificacao\.dentroHorario == true\)/)
})

test('liberação prévia tem janela operacional de dez minutos por padrão', () => {
  const config = read('backend/src/config.ts')
  const workerConfig = read('backend/wrangler.jsonc')
  const rootWorkerConfig = read('wrangler.jsonc')

  assert.match(config, /AUTHORIZATION_TTL_SECONDS', 600/)
  assert.match(workerConfig, /"AUTHORIZATION_TTL_SECONDS": "600"/)
  assert.match(rootWorkerConfig, /"AUTHORIZATION_TTL_SECONDS": "600"/)
})
