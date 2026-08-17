import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const feedback = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/BiometricRegistrationSuccessFeedback.kt', import.meta.url),
  'utf8',
)
const adminShell = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/AdminArea.kt', import.meta.url),
  'utf8',
)
const supervisorShell = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/SupervisorNavigationShell.kt', import.meta.url),
  'utf8',
)

test('cadastro biométrico exibe confirmação animada, háptica e identificada', () => {
  assert.match(feedback, /PontoCafeSuccessAnimation/)
  assert.match(feedback, /HapticFeedbackConstants\.CONFIRM/)
  assert.match(feedback, /Rosto cadastrado com sucesso!/)
  assert.match(feedback, /Biometria pronta/)
  assert.match(feedback, /employeeName/)
  assert.match(feedback, /delay\(BIOMETRIC_SUCCESS_VISIBLE_MILLIS\)/)
})

test('Admin e Supervisor mostram o mesmo feedback depois de salvar o rosto', () => {
  assert.match(adminShell, /BiometricRegistrationSuccessFeedback\(/)
  assert.match(adminShell, /message = state\.mensagem/)
  assert.match(adminShell, /onDismiss = viewModel::limparFeedback/)

  assert.match(supervisorShell, /BiometricRegistrationSuccessFeedback\(/)
  assert.match(supervisorShell, /message = state\.mensagem/)
  assert.match(supervisorShell, /onDismiss = viewModel::limparAviso/)
})
