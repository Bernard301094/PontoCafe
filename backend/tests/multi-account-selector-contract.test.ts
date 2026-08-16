import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const sessionStore = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/data/SecureAdminSessionStore.kt', import.meta.url),
  'utf8',
)
const adminLogin = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/AdminLoginScreen.kt', import.meta.url),
  'utf8',
)
const supervisorLogin = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/SupervisorLoginScreenV2.kt', import.meta.url),
  'utf8',
)
const selector = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/RestrictedLoginModeScreen.kt', import.meta.url),
  'utf8',
)
const mainActivity = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/MainActivity.kt', import.meta.url),
  'utf8',
)

test('sessões salvas suportam múltiplas contas cifradas sem senha persistida', () => {
  assert.match(sessionStore, /data class SavedRestrictedAccount/)
  assert.match(sessionStore, /fun savedAccounts\(\)/)
  assert.match(sessionStore, /fun activate\(accountId: String\)/)
  assert.match(sessionStore, /fun forgetAccount\(accountId: String\)/)
  assert.match(sessionStore, /AndroidKeyStore/)
  assert.match(sessionStore, /AES\/GCM\/NoPadding/)
  assert.match(sessionStore, /MessageDigest\.getInstance\("SHA-256"\)/)
  assert.doesNotMatch(sessionStore, /putString\([^\n]*(senha|password)/i)
})

test('login associa a próxima sessão ao perfil correto sem salvar a senha', () => {
  assert.match(adminLogin, /prepareLogin\(email, "ADMIN"\)/)
  assert.match(supervisorLogin, /prepareLogin\(email, "SUPERVISOR"\)/)
  assert.match(adminLogin, /senha nunca é armazenada/)
  assert.match(supervisorLogin, /senha nunca é salva/)
})

test('seletor mostra contas salvas, permite esquecer e iniciar outra conta', () => {
  assert.match(selector, /Escolha uma conta/)
  assert.match(selector, /Sessão pronta/)
  assert.match(selector, /Senha necessária/)
  assert.match(selector, /forgetAccount\(entry\.account\.id\)/)
  assert.match(selector, /Outra conta de Administrador/)
  assert.match(selector, /Outra conta de Supervisor/)
  assert.doesNotMatch(selector, /\.recreate\(\)/)
})

test('Ponto sempre roteia acesso protegido pelo seletor e isola ViewModel por conta', () => {
  assert.match(mainActivity, /fun openAccountSelector\(\)/)
  assert.match(mainActivity, /onAdminClick = ::openAccountSelector/)
  assert.match(mainActivity, /onSupervisorClick = ::openAccountSelector/)
  assert.match(mainActivity, /onLoginModeClick = ::openAccountSelector/)
  assert.match(mainActivity, /key = "admin:\$adminAccountScope"/)
  assert.match(mainActivity, /key = "admin-devices:\$adminAccountScope"/)
  assert.match(mainActivity, /key = "admin-reliability:\$adminAccountScope"/)
  assert.match(mainActivity, /key = "supervisor:\$supervisorAccountScope"/)
})
