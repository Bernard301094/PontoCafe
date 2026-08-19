import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const motion = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/PontoCafeMotion.kt', import.meta.url),
  'utf8',
)
const common = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/CommonComponents.kt', import.meta.url),
  'utf8',
)
const adminArea = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/AdminArea.kt', import.meta.url),
  'utf8',
)
const supervisorShell = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/SupervisorNavigationShell.kt', import.meta.url),
  'utf8',
)
const live = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/SupervisorLiveScreenV2.kt', import.meta.url),
  'utf8',
)
const biometric = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/AdminBiometricEnrollmentScreen.kt', import.meta.url),
  'utf8',
)

test('motion centraliza durações e easing compartilhados', () => {
  assert.match(motion, /object PontoCafeMotion/)
  assert.match(motion, /const val Quick = 140/)
  assert.match(motion, /const val Standard = 240/)
  assert.match(motion, /const val Emphasized = 360/)
  assert.match(motion, /CubicBezierEasing/)
})

test('componentes compartilhados animam entrada, métricas e estados', () => {
  assert.match(common, /MotionReveal/)
  assert.match(common, /animatedMetricValue\(value\)/)
  assert.match(common, /AnimatedContent\(/)
  assert.match(common, /animateContentSize/)
})

test('navegação Admin e Supervisor usa transições de conteúdo', () => {
  assert.match(adminArea, /AnimatedContent\(/)
  assert.match(adminArea, /admin-primary-navigation/)
  assert.match(adminArea, /admin-detail-navigation/)
  assert.match(supervisorShell, /supervisor-primary-navigation/)
  assert.match(supervisorShell, /slideInHorizontally/)
})

test('monitor ao vivo interpola progresso e estados sem relógio por card', () => {
  assert.match(live, /animatedProgress\(rawProgress\)/)
  assert.match(live, /animateColorAsState/)
  assert.match(live, /motionScale\(overdue/)
  assert.match(live, /AnimatedVisibility\(/)
  assert.doesNotMatch(live, /remember\(pause\.id\) \{ mutableLongStateOf/)
})

test('cadastro biométrico mostra cinco etapas com feedback animado', () => {
  assert.match(biometric, /BiometricSampleProgress\(/)
  assert.match(biometric, /repeat\(total\)/)
  assert.match(biometric, /sample-scale-/)
  assert.match(biometric, /biometric-pose/)
  assert.match(biometric, /biometric-hint/)
})
