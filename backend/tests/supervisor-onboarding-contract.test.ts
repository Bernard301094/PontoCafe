import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { generateTemporaryPassword, isStrongPassword } from '../src/supervisor-onboarding.js'

const migration = readFileSync(
  new URL('../../database/009_supervisor_onboarding.sql', import.meta.url),
  'utf8',
)
const userRoutes = readFileSync(
  new URL('../src/routes/user-management-routes.ts', import.meta.url),
  'utf8',
)
const authRoutes = readFileSync(
  new URL('../src/routes/auth-routes.ts', import.meta.url),
  'utf8',
)
const authRuntime = readFileSync(
  new URL('../src/auth-runtime.ts', import.meta.url),
  'utf8',
)
const adminRoutes = readFileSync(
  new URL('../src/routes/admin-routes.ts', import.meta.url),
  'utf8',
)
const androidAccountForm = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/AdminAccountForm.kt', import.meta.url),
  'utf8',
)
const androidSupervisorApi = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/data/SupervisorApiClient.kt', import.meta.url),
  'utf8',
)
const androidSupervisorShell = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/SupervisorNavigationShell.kt', import.meta.url),
  'utf8',
)


test('temporary supervisor passwords are strong, random and policy compatible', () => {
  const generated = Array.from({ length: 32 }, () => generateTemporaryPassword())
  assert.equal(new Set(generated).size, generated.length)
  for (const password of generated) {
    assert.equal(password.length, 16)
    assert.equal(isStrongPassword(password), true)
    assert.match(password, /[A-Z]/)
    assert.match(password, /[a-z]/)
    assert.match(password, /\d/)
    assert.match(password, /[!@#$%*\-_]/)
  }
})

test('database migration stores shift and mandatory password-change state without plaintext password fields', () => {
  assert.match(migration, /add column if not exists turno text/i)
  assert.match(migration, /add column if not exists "mustChangePassword" boolean not null default false/i)
  assert.match(migration, /turno is null or turno in \('A','B','C','D'\)/i)
  assert.doesNotMatch(migration, /temporary.?password|senha_temporaria/i)
})

test('supervisor creation requires a shift and persists only the Better Auth password hash', () => {
  assert.match(userRoutes, /SUPERVISOR_A/)
  assert.match(userRoutes, /SUPERVISOR_B/)
  assert.match(userRoutes, /SUPERVISOR_C/)
  assert.match(userRoutes, /SUPERVISOR_D/)
  assert.match(userRoutes, /authContext\.password\.hash\(plaintextPassword\)/)
  assert.match(userRoutes, /"mustChangePassword"/)
  assert.match(userRoutes, /senhaTemporariaGerada: supervisor/)
  assert.doesNotMatch(userRoutes, /console\.(?:log|info|error)[\s\S]{0,250}plaintextPassword/)
})

test('first supervisor login is gated until temporary password is replaced', () => {
  assert.match(authRoutes, /mustChangePassword/)
  assert.match(authRuntime, /codigo: 'PASSWORD_CHANGE_REQUIRED'/)
  assert.match(authRoutes, /post\('\/change-temporary-password'/)
  assert.match(authRoutes, /authContext\.password\.verify/)
  assert.match(authRoutes, /authContext\.password\.hash\(body\.data\.newPassword\)/)
  assert.match(authRoutes, /set "mustChangePassword"=false/)
  assert.match(authRoutes, /delete from session where "userId"=\$1 and token<>\$2/)
})

test('role-specific denial no longer looks like account revocation on Android', () => {
  assert.match(authRuntime, /codigo: 'AUTH_ROLE_DENIED'/)
  assert.match(androidSupervisorApi, /"AUTH_ROLE_DENIED" -> false/)
  assert.match(androidSupervisorApi, /"PASSWORD_CHANGE_REQUIRED" -> \{/)
  assert.match(androidSupervisorApi, /SupervisorPasswordChangeRuntime\.requireChange\(\)/)
})

test('Android Supervisor area is blocked by mandatory password screen until change completes', () => {
  assert.match(androidSupervisorShell, /if \(SupervisorPasswordChangeRuntime\.required\)/)
  assert.match(androidSupervisorShell, /SupervisorInitialPasswordChangeScreen/)
  assert.match(androidSupervisorApi, /api\/auth\/change-temporary-password/)
})

test('Admin creation UI requires an explicit Supervisor shift and generates a temporary credential', () => {
  for (const shift of ['A', 'B', 'C', 'D']) {
    assert.match(androidAccountForm, new RegExp(`SUPERVISOR_${shift}`))
  }
  assert.match(androidAccountForm, /Gerar senha temporária/)
  assert.match(androidAccountForm, /SecureRandom/)
  assert.match(androidAccountForm, /Copiar senha temporária/)
})

test('Supervisor password reset re-enters mandatory first-access flow and does not log plaintext credentials', () => {
  assert.match(adminRoutes, /temporaryPassword = supervisor/)
  assert.match(adminRoutes, /body\.data\.novaSenha \?\? generateTemporaryPassword\(\)/)
  assert.match(adminRoutes, /"mustChangePassword"=\$2/)
  assert.match(adminRoutes, /revokeUserSessions/)
  assert.doesNotMatch(adminRoutes, /JSON\.stringify\([\s\S]{0,180}(?:newPassword|temporaryPassword)/)
})
