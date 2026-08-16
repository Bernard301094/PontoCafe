import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const route = readFileSync(new URL('../src/routes/device-activation-routes.ts', import.meta.url), 'utf8')
const migration = readFileSync(new URL('../../database/004_device_registration_idempotency.sql', import.meta.url), 'utf8')
const android = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/data/AdminApiClient.kt', import.meta.url),
  'utf8',
)

test('cadastro de dispositivo continua restrito a ADMIN', () => {
  assert.match(route, /requireUser, requireRole\('ADMIN'\)/)
})

test('endpoint e token de 10 caracteres permanecem no contrato atual', () => {
  assert.match(route, /post\('\/device-activation'/)
  assert.match(route, /newDeviceToken\(10\)/)
})

test('criação idempotente usa uma única query CTE sem transaction manual', () => {
  assert.match(route, /with idempotencia as/i)
  assert.match(route, /novo_dispositivo as/i)
  assert.match(route, /auditoria_criacao as/i)
  assert.doesNotMatch(route, /pool\.connect\(/)
  assert.doesNotMatch(route, /client\.query\(['"]BEGIN/i)
})

test('replay não pode criar nova auditoria quando request_nonce não pertence à tentativa', () => {
  assert.match(route, /from idempotencia\s+where request_nonce=\$4::uuid/i)
  assert.match(route, /from novo_dispositivo\s+returning id/i)
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
  assert.match(android, /UUID\.randomUUID\(\)\.toString\(\)/)
})
