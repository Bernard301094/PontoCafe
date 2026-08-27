import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const adminApi = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/data/AdminApiClient.kt', import.meta.url),
  'utf8',
)
const adminViewModel = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/AdminViewModel.kt', import.meta.url),
  'utf8',
)

test('admin sign-in ends after server authentication and token persistence', () => {
  const match = adminApi.match(
    /suspend fun signIn\(email: String, senha: String\) \{([\s\S]*?)\n    \}\n\n    suspend fun signOut/,
  )
  assert.ok(match, 'AdminRepository.signIn should be discoverable')
  const body = match[1]

  assert.match(body, /api\.signIn\(SignInRequest\(email = email, password = senha\)\)/)
  assert.match(body, /sessionStore\.save\(bearer\)/)
  assert.doesNotMatch(body, /api\.users\(\)/)
})

test('admin authorization is checked separately and auth failures clear only the active session', () => {
  const match = adminApi.match(
    /suspend fun users\(\): List<AdminUser> \{([\s\S]*?)\n    \}\n\n    suspend fun createUser/,
  )
  assert.ok(match, 'AdminRepository.users should be discoverable')
  const body = match[1]

  assert.match(body, /api\.users\(\)\.usuarios/)
  assert.match(body, /if \(isAuthFailure\(error\)\)/)
  assert.match(body, /sessionStore\.clear\(\)/)
  assert.match(body, /clearCaches\(\)/)
})

test('credential sign-in never inherits a bearer from a previously selected account', () => {
  assert.match(adminApi, /SIGN_IN_PATH = "\/api\/auth\/sign-in\/email"/)
  assert.match(adminApi, /if \(original\.url\.encodedPath != SIGN_IN_PATH\)/)
  assert.match(adminApi, /header\("Authorization", "Bearer \$it"\)/)
})

test('admin view model reports the real sign-in error and loads authorization only after success', () => {
  assert.match(
    adminViewModel,
    /runCatching \{ repository\.signIn\(email\.trim\(\)\.lowercase\(\), senha\) \}[\s\S]*\.onSuccess \{ carregarUsuariosInterno\(\) \}/,
  )
  assert.match(adminViewModel, /AdminRepository\.message\(it\)/)
})
