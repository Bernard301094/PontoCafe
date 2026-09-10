import assert from 'node:assert/strict'
import test from 'node:test'
import { newRequestId, safeErrorDescriptor } from '../src/observability.js'

test('preserva request id válido recebido do cliente', () => {
  assert.equal(newRequestId('PC-CLIENT-1234'), 'PC-CLIENT-1234')
})

test('gera request id seguro quando o recebido é inválido', () => {
  const id = newRequestId('token secreto com espaços')
  assert.match(id, /^PC-[A-F0-9]{12}$/)
})

test('classifica falha de conexão sem expor mensagem', () => {
  const descriptor = safeErrorDescriptor(new Error('connection timeout to database'))
  assert.equal(descriptor.code, 'DATABASE_CONNECTION')
  assert.equal('message' in descriptor, false)
})
