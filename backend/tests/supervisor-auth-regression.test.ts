import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { scryptSync } from 'node:crypto'
import test from 'node:test'
import {
  hashPassword,
  isRecognizedPasswordHash,
  verifyPassword,
} from '../src/password-crypto.js'

const SCRYPT_OPTIONS = {
  N: 16_384,
  r: 16,
  p: 1,
  maxmem: 128 * 16_384 * 16 * 2,
}

function betterAuthCompatibleFixture(password: string, saltHex: string): string {
  const key = scryptSync(
    password.normalize('NFKC'),
    Buffer.from(saltHex, 'hex'),
    64,
    SCRYPT_OPTIONS,
  )
  return `${saltHex}:${key.toString('hex')}`
}

test('native provider verifies the existing Better Auth scrypt format', async () => {
  const password = 'Supervisor-Teste-123!'
  const fixture = betterAuthCompatibleFixture(password, '00112233445566778899aabbccddeeff')

  assert.equal(fixture.length, 161)
  assert.equal(isRecognizedPasswordHash(fixture), true)
  assert.equal(await verifyPassword({ hash: fixture, password }), true)
  assert.equal(await verifyPassword({ hash: fixture, password: 'Senha-Incorreta-123!' }), false)
})

test('new hashes remain compatible and use independent random salts', async () => {
  const password = 'Nova-Senha-Supervisor-456!'
  const first = await hashPassword(password)
  const second = await hashPassword(password)

  assert.equal(first.length, 161)
  assert.equal(second.length, 161)
  assert.equal(isRecognizedPasswordHash(first), true)
  assert.equal(isRecognizedPasswordHash(second), true)
  assert.notEqual(first, second)
  assert.equal(await verifyPassword({ hash: first, password }), true)
  assert.equal(await verifyPassword({ hash: second, password }), true)
})

test('password normalization stays compatible with Better Auth NFKC behavior', async () => {
  const original = 'Supervisor１２３!Senha'
  const normalized = original.normalize('NFKC')
  const hash = await hashPassword(original)

  assert.notEqual(original, normalized)
  assert.equal(await verifyPassword({ hash, password: normalized }), true)
})

test('malformed hashes fail closed instead of bypassing verification', async () => {
  const malformed = [
    '',
    'salt:key',
    '00'.repeat(16),
    `${'zz'.repeat(16)}:${'00'.repeat(64)}`,
    `${'00'.repeat(16)}:${'00'.repeat(63)}`,
  ]

  for (const hash of malformed) {
    assert.equal(isRecognizedPasswordHash(hash), false)
    assert.equal(await verifyPassword({ hash, password: 'qualquer-senha' }), false)
  }
})

test('auth runtime binds creation reset and login to the explicit provider', () => {
  const runtime = readFileSync(new URL('../src/auth-runtime.ts', import.meta.url), 'utf8')
  const login = readFileSync(new URL('../src/routes/auth-routes.ts', import.meta.url), 'utf8')
  const users = readFileSync(new URL('../src/routes/user-management-routes.ts', import.meta.url), 'utf8')
  const admin = readFileSync(new URL('../src/routes/admin-routes.ts', import.meta.url), 'utf8')

  assert.match(runtime, /password:\s*\{\s*hash:\s*hashPassword,\s*verify:\s*verifyPassword/s)
  assert.match(users, /authContext\.password\.hash\(body\.data\.senha\)/)
  assert.match(login, /authContext\.password\.verify\(\{[\s\S]*hash: account\.password,[\s\S]*password: body\.data\.password/)
  assert.match(admin, /auth\.api\.setUserPassword/)
})

test('supervisor lookup is role-neutral while inactive and unknown-role accounts remain blocked', () => {
  const login = readFileSync(new URL('../src/routes/auth-routes.ts', import.meta.url), 'utf8')
  const runtime = readFileSync(new URL('../src/auth-runtime.ts', import.meta.url), 'utf8')

  assert.match(login, /where lower\(u\.email\)=lower\(\$1\)/)
  assert.doesNotMatch(login, /where[^`]*(?:role|perfil)[^`]*SUPERVISOR/i)
  assert.match(login, /account\?\.role === 'admin' \|\| account\?\.role === 'user'/)
  assert.match(login, /if \(account\.banned\)/)
  assert.match(login, /if \(!roleRecognized\)/)
  assert.match(login, /Esta conta está desativada\./)
  assert.match(login, /Perfil de acesso inválido\./)
  assert.match(login, /E-mail ou senha inválidos\./)
  assert.match(runtime, /user\.role !== 'admin' && user\.role !== 'user'/)
})

test('safe auth diagnostics never log passwords hashes or tokens', () => {
  const login = readFileSync(new URL('../src/routes/auth-routes.ts', import.meta.url), 'utf8')
  const logBodies = [...login.matchAll(/(?:console\.info|console\.error)\(JSON\.stringify\(\{([\s\S]*?)\}\)\)/g)]
    .map((match) => match[1])
    .join('\n')

  assert.match(logBodies, /accountFound|passwordVerified|sessionCreated/)
  assert.doesNotMatch(logBodies, /body\.data\.password|account\.password|\btoken\b|Authorization/)
})

test('android supervisor flow uses the shared email sign-in endpoint and keeps backend authoritative', () => {
  const api = readFileSync(
    new URL('../../app/src/main/java/com/pontocafe/app/data/SupervisorApiClient.kt', import.meta.url),
    'utf8',
  )
  const viewModel = readFileSync(
    new URL('../../app/src/main/java/com/pontocafe/app/SupervisorViewModel.kt', import.meta.url),
    'utf8',
  )

  assert.match(api, /@POST\("api\/auth\/sign-in\/email"\)/)
  assert.match(api, /SignInRequest\(email = email, password = senha\)/)
  assert.match(api, /response\.headers\(\)\["set-auth-token"\]/)
  assert.match(viewModel, /repository\.signIn\(email\.trim\(\)\.lowercase\(\), senha\)/)
  assert.match(viewModel, /atualizarAoVivoInterno\(\)/)
})
