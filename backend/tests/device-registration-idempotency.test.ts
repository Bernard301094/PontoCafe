import assert from 'node:assert/strict'
import test from 'node:test'
import {
  decryptDeviceRegistrationToken,
  deviceRegistrationFingerprint,
  encryptDeviceRegistrationToken,
  isValidDeviceRegistrationIdempotencyKey,
  normalizeDeviceRegistrationName,
} from '../src/device-registration-idempotency.js'

const masterKey = Buffer.from(Array.from({ length: 32 }, (_, index) => index + 1))
const codePepper = 'test-code-pepper-not-used-in-production'

const context = {
  idempotencyKey: '550e8400-e29b-41d4-a716-446655440000',
  actorId: 'admin-test-id',
  fingerprint: deviceRegistrationFingerprint('admin-test-id', 'Galaxy A55', '2279', codePepper),
  deviceId: '11111111-2222-4333-8444-555555555555',
}

test('aceita UUID como Idempotency-Key e rejeita valores fracos', () => {
  assert.equal(isValidDeviceRegistrationIdempotencyKey(context.idempotencyKey), true)
  assert.equal(isValidDeviceRegistrationIdempotencyKey('curta'), false)
  assert.equal(isValidDeviceRegistrationIdempotencyKey('valor com espaços não permitido'), false)
})

test('normaliza nome do aparelho antes do fingerprint', () => {
  assert.equal(normalizeDeviceRegistrationName('  Galaxy A55  '), 'Galaxy A55')
})

test('fingerprint é determinístico e vincula admin, nome e PIN sem expor o PIN', () => {
  const first = deviceRegistrationFingerprint('admin-test-id', 'Galaxy A55', '2279', codePepper)
  const same = deviceRegistrationFingerprint('admin-test-id', ' Galaxy A55 ', '2279', codePepper)
  const otherPin = deviceRegistrationFingerprint('admin-test-id', 'Galaxy A55', '2280', codePepper)
  const otherAdmin = deviceRegistrationFingerprint('other-admin', 'Galaxy A55', '2279', codePepper)

  assert.equal(first, same)
  assert.match(first, /^[0-9a-f]{64}$/)
  assert.equal(first.includes('2279'), false)
  assert.notEqual(first, otherPin)
  assert.notEqual(first, otherAdmin)
})

test('token de 10 caracteres é cifrado e recuperado somente com o mesmo contexto', () => {
  const token = 'Ab3X9kP2Qz'
  const encrypted = encryptDeviceRegistrationToken(token, masterKey, context)

  assert.notEqual(encrypted.ciphertext.toString('utf8'), token)
  assert.equal(
    decryptDeviceRegistrationToken(
      encrypted.ciphertext,
      encrypted.iv,
      encrypted.authTag,
      masterKey,
      context,
    ),
    token,
  )

  assert.throws(() => decryptDeviceRegistrationToken(
    encrypted.ciphertext,
    encrypted.iv,
    encrypted.authTag,
    masterKey,
    { ...context, deviceId: 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee' },
  ))
})

test('não aceita armazenar replay de token fora do formato de 10 caracteres', () => {
  assert.throws(() => encryptDeviceRegistrationToken('token-muito-longo', masterKey, context))
})
