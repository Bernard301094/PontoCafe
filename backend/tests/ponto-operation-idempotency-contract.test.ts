import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const fastRoute = readFileSync(
  new URL('../src/routes/fast-ponto-routes.ts', import.meta.url),
  'utf8',
)
const mutationRoute = readFileSync(
  new URL('../src/routes/idempotent-ponto-mutation-routes.ts', import.meta.url),
  'utf8',
)
const offlineRoute = readFileSync(
  new URL('../src/routes/offline-routes.ts', import.meta.url),
  'utf8',
)
const application = readFileSync(
  new URL('../src/application.ts', import.meta.url),
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

test('migração mantém identidade e resultado de todas as mutações do Ponto', () => {
  assert.match(migration, /create table if not exists operacoes_ponto_idempotentes/i)
  assert.match(migration, /operacao_id uuid primary key/i)
  assert.match(migration, /dispositivo_id uuid not null references dispositivos/i)
  assert.match(migration, /colaborador_id uuid not null references colaboradores/i)
  assert.match(migration, /REGISTRO_RAPIDO/)
  assert.match(migration, /INICIAR/)
  assert.match(migration, /FINALIZAR/)
  assert.match(migration, /resposta jsonb not null/i)
  assert.match(migration, /pausa_id uuid null references pausas_cafe/i)
})

test('backend serializa operationId e persiste replay junto da mutação rápida', () => {
  assert.match(idempotency, /pg_advisory_xact_lock/)
  assert.match(idempotency, /PontoOperationConflictError/)
  assert.match(idempotency, /findPontoOperationById/)
  assert.match(idempotency, /insert into operacoes_ponto_idempotentes/)
  assert.match(fastRoute, /operacaoId: uuidSchema\.optional\(\)/)
  assert.match(fastRoute, /findPontoOperation<FastSuccessResponse>/)
  assert.match(fastRoute, /savePontoOperation\(client, idempotencyIdentity, current\.id, success\)/)
  assert.match(fastRoute, /savePontoOperation\(client, idempotencyIdentity, pauseId, success\)/)
})

test('INICIAR e FINALIZAR confirmados também são exactly-once', () => {
  assert.match(mutationRoute, /operacaoId: uuidSchema\.optional\(\)/)
  assert.match(mutationRoute, /type: 'INICIAR' \| 'FINALIZAR'/)
  assert.match(mutationRoute, /compatibleMutationReplay<StartResponse>/)
  assert.match(mutationRoute, /compatibleMutationReplay<FinishResponse>/)
  assert.match(mutationRoute, /savePontoOperation\(client, operation, pauseId, result\)/)
  assert.match(mutationRoute, /savePontoOperation\(client, operation, open\.rows\[0\]\.id, result\)/)

  const protectedRoute = application.indexOf("app.route('/ponto', idempotentPontoMutationRoutes)")
  const legacyRoute = application.indexOf("app.route('/ponto', pontoRoutes)")
  assert.ok(protectedRoute >= 0 && legacyRoute > protectedRoute)
})

test('endpoint de reconciliação espera a mutação original e devolve o resultado autoritativo', () => {
  assert.match(mutationRoute, /post\('\/operacoes\/reconciliar'/)
  assert.match(mutationRoute, /lockPontoOperation\(client, body\.data\.operacaoId, device\.id\)/)
  assert.match(mutationRoute, /findPontoOperationById<unknown>/)
  assert.match(mutationRoute, /encontrada: false/)
  assert.match(mutationRoute, /stored\.type === 'INICIAR'/)
  assert.match(mutationRoute, /stored\.type === 'FINALIZAR'/)
  assert.match(mutationRoute, /fast\.status === 'INICIO'/)
  assert.match(mutationRoute, /fast\.status === 'RETORNO'/)
})

test('fila offline reconcilia primeiro qualquer operação online já commitada', () => {
  const operationLookup = offlineRoute.indexOf('findPontoOperationById<StoredOnlineResponse>')
  const biometricLookup = offlineRoute.indexOf('from templates_faciais')
  assert.ok(operationLookup >= 0)
  assert.ok(biometricLookup >= 0)
  assert.ok(operationLookup < biometricLookup)
  assert.match(offlineRoute, /committedOnline\.type !== 'REGISTRO_RAPIDO'/)
  assert.match(offlineRoute, /expectedLegacyType/)
  assert.match(offlineRoute, /O servidor já havia confirmado esta mesma operação online/)
})

test('Android mantém operationId cifrado e não persiste embedding bruto no diário', () => {
  assert.match(journal, /AndroidKeyStore/)
  assert.match(journal, /AES\/GCM\/NoPadding/)
  assert.match(journal, /MessageDigest\.getInstance\("SHA-256"\)/)
  assert.match(journal, /embeddingFingerprint/)
  assert.match(journal, /fun prepareAction\(collaboratorId: String, action: String\)/)
  assert.match(journal, /pendingUncertainOperationId/)
  assert.match(journal, /putString\(PAYLOAD_KEY, encoded\)\.commit\(\)/)
  assert.doesNotMatch(journal, /val embedding: List<Float>/)
  assert.doesNotMatch(journal, /val embedding: FloatArray/)
})

test('resposta incerta bloqueia nova leitura de estado e reaproveita UUID no offline', () => {
  assert.match(apiClient, /val operacaoId: String/)
  assert.match(apiClient, /operationJournal\.markUncertain\(operationId\)/)
  assert.match(apiClient, /operationJournal\.isUncertain\(colaboradorId\)/)
  assert.match(apiClient, /throw IOException\("O resultado do registro anterior ainda precisa ser reconciliado/)
  assert.doesNotMatch(apiClient, /RECONCILIATION_REPLAY_TOKEN/)
  assert.doesNotMatch(apiClient, /motivo = "OPERACAO_RECONCILIADA"/)
  assert.match(offlineStore, /val operationId = operationJournal\.prepare\(colaborador\.id, embedding\)/)
  assert.match(offlineStore, /eventId = operationId/)
})

test('caminho rápido reconcilia antes de criar uma nova operação', () => {
  const registrarRapido = apiClient.indexOf('suspend fun registrarRapido')
  const reconcile = apiClient.indexOf('reconciliarOperacaoPendente(colaboradorId)', registrarRapido)
  const prepare = apiClient.indexOf('val operationId = operationJournal.prepare(colaboradorId, embedding)', registrarRapido)
  assert.ok(registrarRapido >= 0)
  assert.ok(reconcile > registrarRapido)
  assert.ok(prepare > reconcile)
  assert.match(apiClient, /@POST\("ponto\/operacoes\/reconciliar"\)/)
})

test('operationId fica incerto antes de qualquer mutação de rede', () => {
  const fastPrepare = apiClient.indexOf('val operationId = operationJournal.prepare(colaboradorId, embedding)')
  const fastUncertain = apiClient.indexOf('operationJournal.markUncertain(operationId)', fastPrepare)
  const fastRequest = apiClient.indexOf('api.registroRapido(', fastPrepare)
  assert.ok(fastPrepare >= 0 && fastUncertain > fastPrepare && fastRequest > fastUncertain)

  const confirmedPrepare = apiClient.indexOf('val operationId = operationJournal.prepareAction(colaboradorId, acao)')
  const confirmedUncertain = apiClient.indexOf('operationJournal.markUncertain(operationId)', confirmedPrepare)
  const confirmedRequest = apiClient.indexOf('val response = request(operationId)', confirmedPrepare)
  assert.ok(
    confirmedPrepare >= 0 &&
    confirmedUncertain > confirmedPrepare &&
    confirmedRequest > confirmedUncertain,
  )
})

test('resposta mutante só libera operationId depois de snapshot local durável', () => {
  assert.match(apiClient, /"INICIO", "RETORNO" -> Unit/)
  assert.doesNotMatch(apiClient, /"INICIO", "RETORNO" -> operationJournal\.complete\(operationId\)/)
  assert.match(apiClient, /private suspend fun <T> executarMutacaoConfirmada/)
  assert.doesNotMatch(apiClient, /executarMutacaoConfirmada[\s\S]*?completeForCollaborator/)
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

test('release 1.0 preserva a integridade e as otimizações de produção', () => {
  assert.match(gradle, /versionCode = 100/)
  assert.match(gradle, /versionName = "1\.0\.0"/)
  assert.match(gradle, /isMinifyEnabled = true/)
  assert.match(gradle, /isShrinkResources = true/)
  assert.match(gradle, /play-services-tflite-java:16\.5\.0/)
  assert.doesNotMatch(gradle, /play-services-tflite-gpu/)
})
