import assert from 'node:assert/strict'
import { Hono } from 'hono'
import test from 'node:test'
import type { AppEnv } from '../src/auth-runtime.js'
import {
  DEVICE_AUTH_INVALID_CODE,
  deviceAuthContractMiddleware,
} from '../src/routes/shared.js'

function contractApp() {
  const app = new Hono<AppEnv>()
  app.use('*', async (c, next) => {
    c.set('requestId', 'test-request-id')
    await next()
  })
  app.use('*', deviceAuthContractMiddleware)
  app.get('/missing-device', (c) => c.json({ erro: 'Dispositivo não autenticado.' }, 401))
  app.get('/invalid-device', (c) => c.json({ erro: 'Dispositivo inválido.' }, 401))
  app.get('/wrong-pin', (c) => c.json({ erro: 'PIN incorreto.' }, 401))
  app.get('/business-forbidden', (c) => c.json({ erro: 'Pausa não liberada.' }, 403))
  return app
}

test('legacy missing-token 401 gains DEVICE_AUTH_INVALID without changing status', async () => {
  const response = await contractApp().request('/missing-device')
  assert.equal(response.status, 401)
  const body = await response.json() as Record<string, unknown>
  assert.equal(body.erro, 'Dispositivo não autenticado.')
  assert.equal(body.codigo, DEVICE_AUTH_INVALID_CODE)
  assert.equal(body.requestId, 'test-request-id')
})

test('legacy invalid-token 401 gains DEVICE_AUTH_INVALID', async () => {
  const response = await contractApp().request('/invalid-device')
  assert.equal(response.status, 401)
  const body = await response.json() as Record<string, unknown>
  assert.equal(body.codigo, DEVICE_AUTH_INVALID_CODE)
})

test('business 401 is not misclassified as device revocation', async () => {
  const response = await contractApp().request('/wrong-pin')
  assert.equal(response.status, 401)
  const body = await response.json() as Record<string, unknown>
  assert.equal(body.erro, 'PIN incorreto.')
  assert.equal(body.codigo, undefined)
})

test('business 403 is never tagged as device revocation', async () => {
  const response = await contractApp().request('/business-forbidden')
  assert.equal(response.status, 403)
  const body = await response.json() as Record<string, unknown>
  assert.equal(body.erro, 'Pausa não liberada.')
  assert.equal(body.codigo, undefined)
})
