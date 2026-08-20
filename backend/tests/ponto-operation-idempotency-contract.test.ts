import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const fastRoute = readFileSync(
  new URL('../src/routes/fast-ponto-routes.ts', import.meta.url),
  'utf8',
)
const offlineRoute = readFileSync(
  new URL('../src/routes/offline-routes.ts', import.meta.url),
  'utf8',
)
const idempotency = readFileSync(
  new URL('../src/ponto-operation-idempotency.ts', import.meta.url),
  'utf8',
)
const migration = readFileSync(
  new URL('../../database/007_ponto_operation_idempotency.sql', import.meta.url),
  'utf8',
)
const apiClient = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/data/ApiClient.kt', import.meta.url),
  'utf8',
)
const journal = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/data/PontoOperationJournal.kt', import.meta.url),
  'utf8',
)
const offlineStore = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/data/SecurePontoOfflineStore.kt', import.meta.url),
  'utf8',
)
const gradle = readFileSync(
  new URL('../../app/build.gradle.kts', import.meta.url),
  'utf8',
)

test('migração mantém identidade e resultado da operação no mesmo domínio transacional', () => {
  assert.match(migration, /create table if not exists operacoes_ponto_idempotentes/i)
  assert.match(migration, /operacao_id uuid primary key/i)
  assert.match(migration, /dispositivo_id uuid not null references dispositivos/i)
  assert.match(migration, /colaborador_id uuid not null references colaboradores/i)
  assert.match(migration, /resposta jsonb not null/i)
  assert.match(migration, /pausa_id uuid null references pausas_cafe/i)
})

test('backend serializa operationId e só persiste replay junto da mutação rápida', () => {
  assert.match(idempotency, /pg_advisory_xact_lock/)
  assert.match(idempotency, /PontoOperationConflictError/)
  assert.match(idempotency, /insert into operacoes_ponto_idempotentes/)
  assert.match(fastRoute, /operacaoId: uuidSchema\.optional\(\)/)
  assert.match(fastRoute, /findPontoOperation<FastSuccessResponse>/)
  assert.match(fastRoute, /savePontoOperation\(client, idempotencyIdentity, current\.id, success\)/)
  assert.match(fastRoute, /savePontoOperation\(client, idempotencyIdentity, pauseId, success\)/)
})

test('fila offline reconcilia primeiro uma operação online já commitada', () => {
  const operationLookup = offlineRoute.indexOf('findPontoOperation<StoredFastResponse>')
  const biometricLookup = offlineRoute.indexOf('from templates_faciais')
  assert.ok(operationLookup >= 0)
  assert.ok(biometricLookup >= 0)
  assert.ok(operationLookup < biometricLookup)
  assert.match(offlineRoute, /O servidor já havia confirmado esta mesma operação online/)
})

test('Android mantém operationId cifrado e não persiste embedding bruto no diário', () => {
  assert.match(journal, /AndroidKeyStore/)
  assert.match(journal, /AES\/GCM\/NoPadding/)
  assert.match(journal, /MessageDigest\.getInstance\("SHA-256"\)/)
  assert.match(journal, /embeddingFingerprint/)
  assert.match(journal, /putString\(PAYLOAD_KEY, encoded\)\.commit\(\)/)
  assert.doesNotMatch(journal, /val embedding: List<Float>/)
  assert.doesNotMatch(journal, /val embedding: FloatArray/)
})

test('resposta incerta bloqueia fallback legado e reaproveita o UUID na fila offline', () => {
  assert.match(apiClient, /val operacaoId: String/)
  assert.match(apiClient, /operationJournal\.markUncertain\(operationId\)/)
  assert.match(apiClient, /operationJournal\.isUncertain\(colaboradorId\)/)
  assert.match(apiClient, /throw IOException\("O resultado do registro anterior ainda precisa ser reconciliado/)
  assert.match(offlineStore, /val operationId = operationJournal\.prepare\(colaborador\.id, embedding\)/)
  assert.match(offlineStore, /eventId = operationId/)
})

test('resposta mutante só libera operationId depois de snapshot local durável', () => {
  assert.match(apiClient, /"INICIO", "RETORNO" -> operationJournal\.markUncertain\(operationId\)/)
  assert.doesNotMatch(apiClient, /"INICIO", "RETORNO" -> operationJournal\.complete\(operationId\)/)
  assert.match(
    offlineStore,
    /private fun saveInternal\(snapshot: PontoOfflineSnapshot, durable: Boolean = false\)/,
  )
  assert.match(offlineStore, /if \(durable\) \{\s*check\(editor\.commit\(\)\)/)
  assert.ok((offlineStore.match(/durable = true/g) ?? []).length >= 5)

  const onlineStart = offlineStore.indexOf('fun recordOnlineStart')
  const startDurable = offlineStore.indexOf('durable = true', onlineStart)
  const startRelease = offlineStore.indexOf('operationJournal.completeForCollaborator', onlineStart)
  assert.ok(onlineStart >= 0 && startDurable > onlineStart && startRelease > startDurable)

  const onlineFinish = offlineStore.indexOf('fun recordOnlineFinish')
  const finishDurable = offlineStore.indexOf('durable = true', onlineFinish)
  const finishRelease = offlineStore.indexOf('operationJournal.completeForCollaborator', onlineFinish)
  assert.ok(onlineFinish >= 0 && finishDurable > onlineFinish && finishRelease > finishDurable)
})

test('release 0.15 mantém otimizações de produção', () => {
  assert.match(gradle, /versionCode = 36/)
  assert.match(gradle, /versionName = "0\.15\.0"/)
  assert.match(gradle, /isMinifyEnabled = true/)
  assert.match(gradle, /isShrinkResources = true/)
  assert.match(gradle, /play-services-tflite-java:16\.5\.0/)
  assert.doesNotMatch(gradle, /play-services-tflite-gpu/)
})
