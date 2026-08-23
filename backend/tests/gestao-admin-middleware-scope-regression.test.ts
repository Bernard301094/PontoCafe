import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const deletionRoutes = readFileSync(
  new URL('../src/routes/admin-biometric-deletion-routes.ts', import.meta.url),
  'utf8',
)
const avatarRoutes = readFileSync(
  new URL('../src/routes/avatar-routes.ts', import.meta.url),
  'utf8',
)
const biometricCalibrationRoutes = readFileSync(
  new URL('../src/routes/biometric-calibration-routes.ts', import.meta.url),
  'utf8',
)
const workforceRoutes = readFileSync(
  new URL('../src/routes/workforce-routes.ts', import.meta.url),
  'utf8',
)
const collaboratorRoutes = readFileSync(
  new URL('../src/routes/collaborator-management-routes.ts', import.meta.url),
  'utf8',
)
const application = readFileSync(
  new URL('../src/application.ts', import.meta.url),
  'utf8',
)

test('admin destructive gestao router has no router-wide ADMIN wildcard', () => {
  assert.doesNotMatch(
    deletionRoutes,
    /adminBiometricDeletionRoutes\.use\(\s*['"]\*['"]\s*,[\s\S]*?requireRole\(\s*['"]ADMIN['"]\s*\)/,
  )
})

test('both destructive collaborator routes remain explicitly ADMIN-only', () => {
  assert.match(
    deletionRoutes,
    /adminBiometricDeletionRoutes\.post\(\s*['"]\/colaboradores\/:id\/biometria\/excluir['"]\s*,\s*requireUser\s*,\s*requireRole\(\s*['"]ADMIN['"]\s*\)/,
  )
  assert.match(
    deletionRoutes,
    /adminBiometricDeletionRoutes\.post\(\s*['"]\/colaboradores\/:id\/excluir['"]\s*,\s*requireUser\s*,\s*requireRole\(\s*['"]ADMIN['"]\s*\)/,
  )
})

test('shared gestao routers that serve supervisors still allow ADMIN and SUPERVISOR', () => {
  for (const [name, source] of [
    ['avatarManagementRoutes', avatarRoutes],
    ['biometricCalibrationRoutes', biometricCalibrationRoutes],
    ['workforceRoutes', workforceRoutes],
    ['collaboratorManagementRoutes', collaboratorRoutes],
  ] as const) {
    assert.match(
      source,
      new RegExp(`${name}\\.use\\(\\s*['"]\\*['"]\\s*,\\s*requireUser\\s*,\\s*requireRole\\(\\s*['"]ADMIN['"]\\s*,\\s*['"]SUPERVISOR['"]\\s*\\)`),
      `${name} must continue to authorize Supervisor access`,
    )
  }
})

test('admin destructive router remains mounted before shared gestao routers without leaking authorization', () => {
  const adminIndex = application.indexOf("app.route('/gestao', adminBiometricDeletionRoutes)")
  const avatarIndex = application.indexOf("app.route('/gestao', avatarManagementRoutes)")
  const collaboratorIndex = application.indexOf("app.route('/gestao', collaboratorManagementRoutes)")

  assert.ok(adminIndex >= 0, 'adminBiometricDeletionRoutes mount must exist')
  assert.ok(avatarIndex > adminIndex, 'avatarManagementRoutes should remain after destructive route precedence')
  assert.ok(collaboratorIndex > adminIndex, 'collaboratorManagementRoutes should remain after destructive route precedence')
})
