import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const theme = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/PontoCafeTheme.kt', import.meta.url),
  'utf8',
)
const common = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/CommonComponents.kt', import.meta.url),
  'utf8',
)
const lock = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/RestrictedAreaLockScreen.kt', import.meta.url),
  'utf8',
)
const mainActivity = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/MainActivity.kt', import.meta.url),
  'utf8',
)

test('tema premium envolve toda a aplicação', () => {
  assert.match(mainActivity, /PontoCafeTheme\s*\{/)
  assert.match(theme, /PontoCafeAppBackground\(content = content\)/)
  assert.match(theme, /background = Color\.Transparent/)
  assert.match(theme, /PontoCafePremium\.backgroundTop/)
  assert.doesNotMatch(theme, /isSystemInDarkTheme/)
})

test('design system compartilhado usa vidro e borda premium', () => {
  assert.match(common, /PontoCafePremium\.glassStrong/)
  assert.match(common, /PontoCafePremium\.border/)
  assert.match(common, /shadowElevation = 4\.dp/)
})

test('área protegida usa a experiência premium de segurança', () => {
  assert.match(lock, /ÁREA PROTEGIDA/)
  assert.match(lock, /Sessão protegida/)
  assert.match(lock, /Icons\.Default\.Fingerprint/)
  assert.match(lock, /Continuar no Ponto Café/)
  assert.match(lock, /PontoCafePremium\.glassStrong/)
})
