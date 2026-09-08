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

test('motion centraliza durações e easing compartilhados', () => {
  assert.match(motion, /object PontoCafeMotion/)
  assert.match(motion, /const val Quick = \d+/)
  assert.match(motion, /const val Standard = \d+/)
  assert.match(motion, /const val Emphasized = \d+/)
  assert.match(motion, /Easing/)
})

test('componentes compartilhados animam entrada, métricas e estados', () => {
  assert.match(motion, /fun MotionReveal\(/)
  assert.match(common, /animatedMetricValue\(value\)/)
})

test('navegação Admin e Supervisor usa transições de conteúdo', () => {
  assert.match(adminArea, /AnimatedContent\(/)
  assert.match(adminArea, /admin-primary-navigation/)
  assert.match(adminArea, /admin-detail-navigation/)
  assert.match(supervisorShell, /supervisor-primary-navigation/)
  assert.match(supervisorShell, /slideInHorizontally/)
})
