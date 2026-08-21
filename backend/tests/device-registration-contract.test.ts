import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const route = readFileSync(new URL('../src/routes/device-activation-routes.ts', import.meta.url), 'utf8')
const migration = readFileSync(new URL('../../database/004_device_registration_idempotency.sql', import.meta.url), 'utf8')
const android = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/data/AdminApiClient.kt', import.meta.url),
  'utf8',
)
const androidViewModel = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/AdminDeviceViewModel.kt', import.meta.url),
  'utf8',
)
const managementRoute = readFileSync(new URL('../src/routes/device-management-routes.ts', import.meta.url), 'utf8')
const deviceScreen = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/AdminDevicesScreenV2.kt', import.meta.url),
  'utf8',
)

test('cadastro de dispositivo continua restrito a ADMIN', () => {
  assert.match(route, /requireUser, requireRole\('ADMIN'\)/)
})

test('endpoint e token de 10 caracteres permanecem no contrato atual', () => {
  assert.match(route, /post\('\/device-activation'/)
  assert.match(route, /newDeviceToken\(10\)/)
})

test('cadastro atual exige PIN individual e não mantém fallback sem PIN', () => {
  assert.match(route, /pin:\s*z\.string\(\)\.trim\(\)\.regex\(\/\^\\d\{4,12\}\$\//s)
  assert.doesNotMatch(route, /regex\(\/\^\\d\{4,12\}\$\/\)\.optional\(\)/s)
  assert.match(route, /hashDeviceUnlockPin\(deviceId, body\.data\.pin\)/)
  assert.match(route, /pinConfigurado:\s*true/)
})

test('Idempotency-Key é obrigatória e clientes legados não recebem chave efêmera', () => {
  assert.match(route, /IDEMPOTENCY_KEY_REQUIRED/)
  assert.match(route, /if \(!incomingIdempotencyKey\)/)
  assert.match(route, /const idempotencyKey = incomingIdempotencyKey/)
  assert.doesNotMatch(route, /legacy:\$\{newId\(\)\}/)
})

test('criação idempotente preserva a query CTE validada em PostgreSQL', () => {
  assert.match(route, /with idempotencia as/i)
  assert.match(route, /novo_dispositivo as/i)
  assert.match(route, /auditoria_criacao as/i)
  assert.doesNotMatch(route, /pool\.connect\(/)
  assert.doesNotMatch(route, /client\.query\(['"]BEGIN/i)
})

test('UUIDs permanecem UUID dentro do CTE antes das comparações', () => {
  assert.doesNotMatch(route, /request_nonce::text/i)
  assert.doesNotMatch(route, /dispositivo_id::text,\s*token_ciphertext/i)
  assert.match(route, /where request_nonce=\$4::uuid/i)
  assert.match(route, /\(i\.request_nonce=\$4::uuid\) as criado_agora/i)
})

test('replay não pode criar nova auditoria quando request_nonce não pertence à tentativa', () => {
  assert.match(route, /from idempotencia\s+where request_nonce=\$4::uuid/i)
  assert.match(route, /from novo_dispositivo\s+returning id/i)
})

test('replay confirmado retorna HTTP 200 sem alterar o token original', () => {
  assert.match(route, /registration\.criado_agora \? 201 : 200/)
  assert.match(route, /replayIdempotente:\s*!registration\.criado_agora/)
  assert.match(route, /decryptDeviceRegistrationToken\(/)
})

test('falha 500 informa etapa e classificação segura sem expor segredo', () => {
  assert.match(route, /safeErrorDescriptor\(error\)/)
  assert.match(route, /Diagnóstico \$\{stage\}\/\$\{descriptor\.code\}/)
  assert.match(route, /codigoBanco:\s*descriptor\.databaseCode/)
  assert.doesNotMatch(route, /console\.(?:log|error).*body\.data\.pin/s)
  assert.doesNotMatch(route, /console\.(?:log|error).*responseToken/s)
})

test('migração guarda somente token cifrado para replay temporário', () => {
  assert.match(migration, /token_ciphertext bytea not null/i)
  assert.match(migration, /token_iv bytea not null/i)
  assert.match(migration, /token_auth_tag bytea not null/i)
  assert.doesNotMatch(migration, /\bactivation_token\b/i)
  assert.doesNotMatch(migration, /\btoken_plain/i)
})

test('Android envia Idempotency-Key no endpoint administrativo existente', () => {
  assert.match(android, /@POST\("admin\/device-activation"\)/)
  assert.match(android, /@Header\("Idempotency-Key"\) idempotencyKey: String/)
  assert.match(android, /data class CreateDeviceRequest\(val nome: String, val pin: String\)/)
})

test('Android reutiliza a mesma chave enquanto repete o mesmo cadastro após falha transitória', () => {
  assert.match(androidViewModel, /pendingRegistrationKey: String\? = null/)
  assert.match(androidViewModel, /pendingRegistrationFingerprint: String\? = null/)
  assert.match(androidViewModel, /UUID\.randomUUID\(\)\.toString\(\)/)
  assert.match(androidViewModel, /repository\.createDevice\(cleanName, cleanPin, idempotencyKey\)/)
  assert.match(androidViewModel, /shouldDiscardPendingRegistration\(error\)/)
  assert.match(androidViewModel, /error\.code\(\) in setOf\(400, 409, 422\)/)
})

test('token aparece imediatamente, pode ser copiado e exige fechamento deliberado', () => {
  assert.match(deviceScreen, /state\.tokenGerado\?\.let \{ token ->/)
  assert.match(deviceScreen, /onDismissRequest = \{\}/)
  assert.match(deviceScreen, /SelectionContainer/)
  assert.match(deviceScreen, /FontFamily\.Monospace/)
  assert.match(deviceScreen, /Visível somente agora/)
  assert.match(deviceScreen, /não poderá ser consultado novamente/)
  assert.match(deviceScreen, /clipboard\.setText\(AnnotatedString\(token\)\)/)
  assert.match(deviceScreen, /Copiar token/)
  assert.match(deviceScreen, /Já salvei · fechar/)
})

test('lista administrativa não recupera o token em texto puro depois do cadastro', () => {
  const start = managementRoute.indexOf("deviceManagementRoutes.get('/devices'")
  const end = managementRoute.indexOf("deviceManagementRoutes.put('/devices/:id/unlock-pin'", start)
  assert.ok(start >= 0 && end > start)
  const listRoute = managementRoute.slice(start, end)

  assert.doesNotMatch(listRoute, /token_hash|token_ciphertext|activationToken|tokenGerado/)
  assert.match(listRoute, /statusAtivacao/)
  assert.match(listRoute, /telemetriaEm/)
})

test('cards mostram ativação, versão, atividade, saúde e estado operacional', () => {
  assert.match(managementRoute, /APP_HEALTH/)
  assert.match(managementRoute, /ATIVAR_DISPOSITIVO/)
  assert.match(managementRoute, /ROTACIONAR_TOKEN_DISPOSITIVO/)
  assert.match(managementRoute, /alertaSaude/)
  assert.match(deviceScreen, /label = "Ativação"/)
  assert.match(deviceScreen, /label = "Aplicativo"/)
  assert.match(deviceScreen, /label = "Última atividade"/)
  assert.match(deviceScreen, /label = "Saúde e segurança"/)
  assert.match(deviceScreen, /Aguardando ativação/)
  assert.match(deviceScreen, /Atualização disponível/)
  assert.match(deviceScreen, /Sem telemetria recente/)
  assert.match(deviceScreen, /containerColor = if \(device\.alertaSaude\)/)
})
