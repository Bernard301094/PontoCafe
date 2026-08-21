import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const read = (relative: string) => readFileSync(new URL(relative, import.meta.url), 'utf8')

const adminViewModel = read('../../app/src/main/java/com/pontocafe/app/AdminViewModel.kt')
const supervisorViewModel = read('../../app/src/main/java/com/pontocafe/app/SupervisorViewModel.kt')
const selector = read('../../app/src/main/java/com/pontocafe/app/avatar/EnrollmentAvatarCapture.kt')
const optimizer = read('../../app/src/main/java/com/pontocafe/app/avatar/AvatarImageOptimizer.kt')
const completionUi = read('../../app/src/main/java/com/pontocafe/app/ui/BiometricEnrollmentAvatarResult.kt')
const adminEnrollment = read('../../app/src/main/java/com/pontocafe/app/ui/AdminBiometricEnrollmentScreen.kt')
const supervisorEnrollment = read('../../app/src/main/java/com/pontocafe/app/ui/SupervisorBiometricEnrollmentScreenV2.kt')
const adminPeople = read('../../app/src/main/java/com/pontocafe/app/ui/AdminPeopleScreenV4.kt')
const supervisorPeople = read('../../app/src/main/java/com/pontocafe/app/ui/SupervisorPeopleScreenV3.kt')
const avatarRoutes = read('../src/routes/avatar-routes.ts')
const pontoViewModel = read('../../app/src/main/java/com/pontocafe/app/PontoCafeViewModel.kt')
const adminRepository = read('../../app/src/main/java/com/pontocafe/app/data/AdminApiClient.kt')
const supervisorRepository = read('../../app/src/main/java/com/pontocafe/app/data/SupervisorApiClient.kt')

function enrollmentFunction(source: string): string {
  const start = source.indexOf('fun processarAmostraBiometrica')
  const end = source.indexOf('fun tentarNovamenteAvatarDoCadastro', start)
  assert.ok(start >= 0 && end > start)
  return source.slice(start, end)
}

for (const [role, source] of [['Admin', adminViewModel], ['Supervisor', supervisorViewModel]] as const) {
  test(`${role} saves biometric before independently uploading avatar`, () => {
    const enrollment = enrollmentFunction(source)
    const saveBiometric = enrollment.indexOf('repository.saveBiometric(')
    const uploadAvatar = enrollment.indexOf('repository.uploadAvatar(')
    assert.ok(saveBiometric >= 0)
    assert.ok(uploadAvatar > saveBiometric)
    assert.match(enrollment, /biometricEnrollmentCompleted = true/)
    assert.match(enrollment, /avatarFailure/)
    assert.match(enrollment, /EnrollmentAvatarUploadStatus\.FAILED/)
  })

  test(`${role} avatar-only retry never calls biometric enrollment`, () => {
    const start = source.indexOf('fun tentarNovamenteAvatarDoCadastro')
    const end = source.indexOf('fun voltarColaboradores', start)
    const retry = source.slice(start, end)
    assert.match(retry, /repository\.uploadAvatar/)
    assert.doesNotMatch(retry, /saveBiometric/)
    assert.match(retry, /A biometria continua salva/)
  })
}

test('best-frame selector requires a frontal unambiguous face and ranks image quality', () => {
  assert.match(selector, /faceCount == 1/)
  assert.match(selector, /facts\.eyesOpen/)
  assert.match(selector, /MAX_YAW = 10f/)
  assert.match(selector, /fullyVisible/)
  assert.match(selector, /reliableLandmarks/)
  assert.match(selector, /FaceImageQualityAnalyzer\.analyzeFrame/)
  assert.match(selector, /if \(score <= bestScore/)
})

test('enrollment reuses the captured frame and emits a bounded mirrored square WebP', () => {
  assert.match(selector, /stage\(frame: FaceFrame\)/)
  assert.match(selector, /NaturalAvatarCropPolicy\.square/)
  assert.match(selector, /setScale\(-1f, 1f\)/)
  assert.match(selector, /takeBestWebp/)
  assert.match(optimizer, /TARGET_SIZE = 256/)
  assert.match(optimizer, /WEBP_LOSSY/)
  assert.match(optimizer, /MAX_BYTES = 28 \* 1024/)
  assert.match(optimizer, /applyExifOrientation/)
  assert.match(optimizer, /ORIENTATION_ROTATE_90/)
})

test('Admin and Supervisor explain avatar capture and expose preview plus avatar-only replacement', () => {
  for (const screen of [adminEnrollment, supervisorEnrollment]) {
    assert.match(screen, /BiometricEnrollmentAvatarResult/)
    assert.match(screen, /melhor imagem frontal também será usada como foto de perfil/)
    assert.match(screen, /Foto de perfil capturada/)
  }
  assert.match(completionUi, /Foto de perfil capturada/)
  assert.match(completionUi, /Tentar salvar novamente/)
  assert.match(completionUi, /Trocar somente a foto/)
  assert.match(completionUi, /não é usada para reconhecer ou validar a identidade/)
})

test('existing employees retain manual camera gallery replace and removal without enrollment', () => {
  for (const people of [adminPeople, supervisorPeople]) {
    assert.match(people, /CollaboratorAvatarSourceDialog/)
    assert.match(people, /uploadAvatar/)
    assert.match(people, /deleteAvatar/)
  }
})

test('avatar refresh and mutations remain independent from biometric catalog metadata', () => {
  assert.doesNotMatch(avatarRoutes, /update templates_faciais set atualizado_em/)
  assert.match(pontoViewModel, /private fun sincronizarAvatares/)
  assert.match(pontoViewModel, /repository\.avatarCatalog\(\)/)
  assert.match(pontoViewModel, /Falhas aqui não alteram o catálogo facial/)
  assert.match(adminRepository, /PontoAvatarRuntime\.avatarUpdated/)
  assert.match(supervisorRepository, /PontoAvatarRuntime\.avatarUpdated/)
})

test('existing avatar endpoint keeps authorization validation size format and versioned cache URL', () => {
  assert.match(avatarRoutes, /requireRole\('ADMIN', 'SUPERVISOR'\)/)
  assert.match(avatarRoutes, /contentType !== 'image\/webp'/)
  assert.match(avatarRoutes, /AVATAR_MAX_BYTES/)
  assert.match(avatarRoutes, /isWebP\(new Uint8Array\(payload\)\)/)
  assert.match(avatarRoutes, /avatarUrl\(new URL\(c\.req\.url\)\.origin, collaboratorId, version\)/)
})
