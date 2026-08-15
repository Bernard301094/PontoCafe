import assert from 'node:assert/strict'
import test from 'node:test'
import { generateDeviceActivationCode } from '../src/device-activation-code.js'

test('código de ativação usa exatamente 10 caracteres alfanuméricos por padrão', () => {
  for (let index = 0; index < 100; index += 1) {
    assert.match(generateDeviceActivationCode(), /^[A-Za-z0-9]{10}$/)
  }
})

test('códigos consecutivos não devem colapsar no mesmo valor em uma amostra normal', () => {
  const values = new Set(Array.from({ length: 200 }, () => generateDeviceActivationCode()))
  assert.equal(values.size, 200)
})

test('comprimentos inválidos são rejeitados', () => {
  assert.throws(() => generateDeviceActivationCode(0))
  assert.throws(() => generateDeviceActivationCode(65))
})
