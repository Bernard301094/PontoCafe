import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const config = readFileSync(new URL('../src/config.ts', import.meta.url), 'utf8')
const maintenance = readFileSync(new URL('../src/maintenance.ts', import.meta.url), 'utf8')
const cloudflare = readFileSync(new URL('../src/cloudflare.ts', import.meta.url), 'utf8')
const envExample = readFileSync(new URL('../../.env.example', import.meta.url), 'utf8')

test('1.0 limita crescimento dos diários técnicos', () => {
  assert.match(config, /PONTO_OPERATION_RETENTION_DAYS', 30/)
  assert.match(config, /DEVICE_HEALTH_RETENTION_DAYS', 30/)

  assert.match(maintenance, /cleanupExpiredPontoOperations/)
  assert.match(maintenance, /delete from operacoes_ponto_idempotentes/)
  assert.match(maintenance, /config\.pontoOperationRetentionDays/)

  assert.match(maintenance, /cleanupExpiredDeviceHealthTelemetry/)
  assert.match(maintenance, /acao='APP_HEALTH'/)
  assert.match(maintenance, /config\.deviceHealthRetentionDays/)
})

test('Cloudflare executa as limpezas no cron sem misturar dados de negócio', () => {
  assert.match(cloudflare, /cleanupExpiredPontoOperations/)
  assert.match(cloudflare, /cleanupExpiredDeviceHealthTelemetry/)
  assert.match(cloudflare, /pontoOperations/)
  assert.match(cloudflare, /deviceHealth/)
  assert.match(cloudflare, /PONTO_OPERATION_RETENTION_DAYS/)
  assert.match(cloudflare, /DEVICE_HEALTH_RETENTION_DAYS/)
})

test('exemplo de configuração acompanha os defaults seguros da Release', () => {
  assert.match(envExample, /FACE_MATCH_THRESHOLD=0\.72/)
  assert.match(envExample, /FACE_IDENTIFICATION_MARGIN=0\.06/)
  assert.match(envExample, /FACE_ENROLLMENT_DUPLICATE_THRESHOLD=0\.78/)
  assert.match(envExample, /AUTHORIZATION_TTL_SECONDS=600/)
  assert.match(envExample, /PONTO_OPERATION_RETENTION_DAYS=30/)
  assert.match(envExample, /DEVICE_HEALTH_RETENTION_DAYS=30/)
  assert.match(envExample, /APP_LATEST_ANDROID_VERSION=1\.0\.0/)
  assert.match(envExample, /APP_MIN_ANDROID_VERSION=0\.15\.0/)
})
