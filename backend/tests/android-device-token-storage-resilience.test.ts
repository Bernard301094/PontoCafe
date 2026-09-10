import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const pontoTokenStore = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/data/SecureDeviceTokenStore.kt', import.meta.url),
  'utf8',
)
const adminActivationTokenStore = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/data/SecureAdminDeviceActivationTokenStore.kt', import.meta.url),
  'utf8',
)

test('credencial longa do Ponto valida a cifra e se recupera de chave AndroidKeyStore inválida', () => {
  assert.match(pontoTokenStore, /fun hasToken\(\): Boolean = read\(\) != null/)
  assert.match(pontoTokenStore, /fun save\(token: String\): Boolean/)
  assert.match(pontoTokenStore, /encryptWithRecovery/)
  assert.match(pontoTokenStore, /resetCredentialMaterial\(deleteKeystoreKey = true\)/)
  assert.match(pontoTokenStore, /keyStore\.deleteEntry\(keyAlias\)/)
  assert.match(pontoTokenStore, /\.commit\(\)/)
  assert.doesNotMatch(pontoTokenStore, /Log\./)
})

test('tokens curtos do Administrador não derrubam a tela quando o AndroidKeyStore precisa ser recriado', () => {
  assert.match(adminActivationTokenStore, /fun save\(deviceId: String, token: String\): Boolean/)
  assert.match(adminActivationTokenStore, /fun reconcile\(pendingDeviceIds: Set<String>\): Boolean/)
  assert.match(adminActivationTokenStore, /encryptWithRecovery/)
  assert.match(adminActivationTokenStore, /resetEncryptedState\(deleteKeystoreKey = true\)/)
  assert.match(adminActivationTokenStore, /keyStore\.deleteEntry\(keyAlias\)/)
  assert.match(adminActivationTokenStore, /TOKEN_PATTERN = Regex\("\^\[A-Za-z0-9\]\{10\}\$"\)/)
  assert.doesNotMatch(adminActivationTokenStore, /Log\./)
})

test('nenhum store degrada para token em texto puro quando a persistência segura falha', () => {
  assert.doesNotMatch(pontoTokenStore, /putString\([^,]+,\s*normalized\)/)
  assert.doesNotMatch(adminActivationTokenStore, /putString\([^,]+,\s*cleanToken\)/)
})
