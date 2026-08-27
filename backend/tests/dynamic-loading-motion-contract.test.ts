import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const skeleton = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/PontoCafeSkeleton.kt', import.meta.url),
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
const kiosk = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/FaceKioskScreen.kt', import.meta.url),
  'utf8',
)

test('skeleton reutilizável preserva a estrutura visual de listas', () => {
  assert.match(skeleton, /fun PontoCafeListSkeletonScreen\(/)
  assert.match(skeleton, /fun PontoCafeSkeletonRow\(/)
  assert.match(skeleton, /rememberInfiniteTransition/)
  assert.match(skeleton, /PontoCafePremium\.glassStrong/)
})

test('Admin usa skeleton na carga inicial de Pessoas e Dispositivos', () => {
  assert.match(adminArea, /initialPeopleLoading/)
  assert.match(adminArea, /PontoCafeListSkeletonScreen\(\s*title = "Pessoas"/s)
  assert.match(adminArea, /deviceState\.carregando && deviceState\.dispositivos\.isEmpty\(\)/)
  assert.match(adminArea, /title = "Dispositivos"/)
})

test('Supervisor usa skeleton antes da primeira sincronização e em Pessoas', () => {
  assert.match(supervisorShell, /loadingInitialLive/)
  assert.match(supervisorShell, /ultimaAtualizacaoAoVivoEmMillis == null/)
  assert.match(supervisorShell, /loadingInitialPeople/)
  assert.match(supervisorShell, /PontoCafeListSkeletonScreen/)
})

test('guia facial anima apenas enquanto procura e reage aos estados', () => {
  assert.match(kiosk, /val searching = active && !warning && !ready/)
  assert.match(kiosk, /Animatable\(1f\)/)
  assert.match(kiosk, /animateColorAsState/)
  assert.match(kiosk, /ready -> 1\.022f/)
  assert.match(kiosk, /warning -> 0\.986f/)
  assert.match(kiosk, /while \(true\)/)
})
