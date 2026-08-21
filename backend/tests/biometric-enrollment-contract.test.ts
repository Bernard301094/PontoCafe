import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const route = readFileSync(new URL('../src/routes/collaborator-management-routes.ts', import.meta.url), 'utf8')
const localBiometricRoute = readFileSync(new URL('../src/routes/local-biometric-routes.ts', import.meta.url), 'utf8')
const pontoRoute = readFileSync(new URL('../src/routes/ponto-routes.ts', import.meta.url), 'utf8')
const migration = readFileSync(new URL('../../database/005_multi_face_templates.sql', import.meta.url), 'utf8')
const faceCatalog = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/data/SecureFaceCatalogStore.kt', import.meta.url),
  'utf8',
)
const camera = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/camera/FaceCamera.kt', import.meta.url),
  'utf8',
)
const engine = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/camera/LiteRtFaceEmbeddingEngine.kt', import.meta.url),
  'utf8',
)
const enrollmentScreen = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/AdminBiometricEnrollmentScreen.kt', import.meta.url),
  'utf8',
)
const adminShell = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/AdminArea.kt', import.meta.url),
  'utf8',
)
const kioskScreen = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/FaceKioskScreen.kt', import.meta.url),
  'utf8',
)

test('unicidade biométrica entre pessoas mantém o limiar do reconhecimento', () => {
  assert.match(route, /Math\.min\(config\.faceThreshold, config\.faceEnrollmentDuplicateThreshold\)/)
  assert.doesNotMatch(route, /Math\.max\(config\.faceThreshold, config\.faceEnrollmentDuplicateThreshold\)/)
})

test('nova aparência valida continuidade com qualquer template anterior da mesma pessoa', () => {
  assert.match(route, /const currentBiometrics = await client\.query/)
  assert.match(route, /compatiblePrevious/)
  assert.match(route, /validateBiometricVector\(storedCurrent\)\.valid/)
  assert.match(route, /continuityThreshold = Math\.max\(0\.60, config\.faceThreshold - CONTINUITY_THRESHOLD_DELTA\)/)
  assert.match(route, /evaluateDuplicateBiometric\(consolidatedScore, sampleScores, continuityThreshold\)/)
  assert.match(route, /BIOMETRIC_IDENTITY_CHANGED/)
  assert.match(route, /expansão foi bloqueada para impedir troca de identidade/)
})

test('cadastro passa a acumular múltiplas aparências sem substituir o rosto anterior', () => {
  assert.match(migration, /drop constraint if exists templates_faciais_colaborador_id_key/)
  assert.match(route, /z\.array\(embeddingSchema\)\.length\(5\)\.optional\(\)/)
  assert.match(route, /evaluateEnrollmentConsistency\(body\.data\.embedding, samples\)/)
  assert.match(route, /validateBiometricVector\(stored\)\.valid/)
  assert.match(migration, /add column if not exists tipo/)
  assert.match(migration, /add column if not exists lote_id/)
  assert.match(route, /MAX_TEMPLATES_PER_COLLABORATOR = 24/)
  assert.match(route, /tipo: 'CONSOLIDADO'/)
  assert.match(route, /tipo: 'AMOSTRA'/)
  assert.match(route, /lote_id/)
  assert.doesNotMatch(route, /on conflict \(colaborador_id\) do update/)
})

test('matching local compara pessoas, não variantes da mesma pessoa', () => {
  assert.match(faceCatalog, /PreparedCatalog/)
  assert.match(faceCatalog, /PreparedCollaborator/)
  assert.match(faceCatalog, /template\.colaborador\.id/)
  assert.match(faceCatalog, /sourceTemplates === catalog\.templates/)
  assert.match(faceCatalog, /winner\.score - secondScore < catalog\.margem/)
})

test('servidor confirma identidade usando o melhor template da pessoa', () => {
  assert.match(localBiometricRoute, /evaluateBiometricIdentification/)
  assert.match(localBiometricRoute, /stored\.colaborador_id !== body\.data\.colaboradorId/)
  assert.match(localBiometricRoute, /config\.faceIdentificationMargin/)
  assert.match(pontoRoute, /bestByCollaborator = new Map<string, Candidato>/)
  assert.match(pontoRoute, /bestByCollaborator\.set\(template\.colaborador_id, candidate\)/)
  assert.match(pontoRoute, /evaluateBiometricIdentification/)
})

test('landmarks faciais ficam disponíveis sem quebrar embeddings já cadastrados', () => {
  assert.match(camera, /LANDMARK_MODE_ALL/)
  assert.match(camera, /FaceLandmark\.LEFT_EYE/)
  assert.match(camera, /FaceLandmark\.RIGHT_EYE/)
  assert.match(camera, /FaceLandmark\.NOSE_BASE/)
  assert.match(engine, /FACE_MARGIN = 0\.18f/)
  assert.match(engine, /canonicalRect\(source, frame\.faceBounds\)/)
})

test('liveness troca o piscar por giro de cabeça quando os olhos não ficam legíveis', () => {
  assert.match(kioskScreen, /BLINK_FALLBACK_FRAMES = 36/)
  assert.match(kioskScreen, /blinkPendingFrames/)
  assert.match(kioskScreen, /challengeAdjustedForEyes/)
  assert.match(kioskScreen, /KioskLivenessChallenge\.TURN_LEFT/)
  assert.match(kioskScreen, /KioskLivenessChallenge\.TURN_RIGHT/)
  assert.match(kioskScreen, /Siga o movimento de cabeça indicado/)
})

test('substituição de identidade continua exigindo exclusão biométrica explícita', () => {
  assert.match(route, /post\('\/colaboradores\/:id\/biometria\/excluir'/)
  assert.match(route, /Exclua a biometria antiga explicitamente/)
})

test('Android confirma visualmente a pessoa antes de iniciar o cadastro', () => {
  assert.match(enrollmentScreen, /Confirme a pessoa/)
  assert.match(enrollmentScreen, /Confirmar e iniciar/)
  assert.match(enrollmentScreen, /identityConfirmed/)
})

test('cadastro facial concluído mantém confirmação dinâmica e temporária', () => {
  assert.match(adminShell, /BiometricRegistrationSuccessFeedback\(/)
  assert.match(adminShell, /message = state\.mensagem/)
  assert.match(adminShell, /onDismiss = viewModel::limparFeedback/)
})

test('modo Ponto captura o embedding frontal somente depois da prova de vida', () => {
  assert.match(kioskScreen, /challengeCompleted/)
  assert.match(kioskScreen, /stableRecognitionFrames/)
  assert.match(kioskScreen, /if \(observation\.isFrontal\)/)
  assert.match(kioskScreen, /showPositionGuide = false/)
})
