import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const route = readFileSync(new URL('../src/routes/collaborator-management-routes.ts', import.meta.url), 'utf8')
const enrollmentScreen = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/AdminBiometricEnrollmentScreen.kt', import.meta.url),
  'utf8',
)
const kioskScreen = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/FaceKioskScreen.kt', import.meta.url),
  'utf8',
)

test('unicidade biométrica nunca usa limiar mais permissivo que o reconhecimento', () => {
  assert.match(route, /Math\.min\(config\.faceThreshold, config\.faceEnrollmentDuplicateThreshold\)/)
  assert.doesNotMatch(route, /Math\.max\(config\.faceThreshold, config\.faceEnrollmentDuplicateThreshold\)/)
})

test('atualização de rosto valida continuidade com a biometria anterior', () => {
  assert.match(route, /where colaborador_id=\$1\s+limit 1/s)
  assert.match(route, /evaluateDuplicateBiometric\(consolidatedScore, sampleScores, config\.faceThreshold\)/)
  assert.match(route, /BIOMETRIC_IDENTITY_CHANGED/)
  assert.match(route, /A atualização foi bloqueada para impedir troca de identidade/)
})

test('substituição de identidade exige exclusão biométrica explícita', () => {
  assert.match(route, /post\('\/colaboradores\/:id\/biometria\/excluir'/)
  assert.match(route, /Exclua a biometria atual explicitamente/)
})

test('Android confirma visualmente a pessoa antes de iniciar o cadastro', () => {
  assert.match(enrollmentScreen, /Confirme a pessoa/)
  assert.match(enrollmentScreen, /Confirmar pessoa e iniciar/)
  assert.match(enrollmentScreen, /identityConfirmed/)
})

test('modo Ponto captura o embedding frontal somente depois da prova de vida', () => {
  assert.match(kioskScreen, /challengeCompleted/)
  assert.match(kioskScreen, /stableRecognitionFrames/)
  assert.match(kioskScreen, /if \(observation\.isFrontal\)/)
  assert.match(kioskScreen, /showPositionGuide = false/)
})
