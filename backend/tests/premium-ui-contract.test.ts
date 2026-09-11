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

test('o tema envolve toda a aplicação e fica travado no modo claro', () => {
  assert.match(mainActivity, /PontoCafeTheme\s*\{/)
  assert.match(theme, /fun PontoCafeTheme\(/)
  // O design entregue especifica só o modo claro, e o quiosque vive numa
  // parede: seguir o modo do sistema mudaria o totem ao anoitecer. O esquema
  // escuro continua definido como contraparte, mas ninguém o liga sozinho.
  assert.match(theme, /darkTheme: Boolean = false/)
  assert.doesNotMatch(theme, /isSystemInDarkTheme/)
  // O fundo é aplicado uma vez, por dentro do tema: nenhuma tela precisa
  // (nem deve) desenhar o seu próprio gradiente de fundo.
  assert.match(theme, /PontoCafeAppBackground\(darkTheme = darkTheme, content = content\)/)
  assert.match(theme, /colorScheme = if \(darkTheme\) PontoCafeDarkColors else PontoCafeLightColors/)
  assert.match(theme, /LocalPontoCafeSemanticColors provides semanticColors/)
})

test('área protegida deixa claro o que está bloqueado e como sair', () => {
  assert.match(lock, /ÁREA PROTEGIDA/)
  assert.match(lock, /Sessão ativa neste dispositivo/)
  assert.match(lock, /Icons\.Default\.Fingerprint/)
  assert.match(lock, /Desbloquear Ponto Café/)
  // A saída para o Ponto tem de estar sempre visível: sem ela, um quiosque
  // com sessão de Admin salva ficaria preso na tela de bloqueio.
  assert.match(lock, /Voltar ao Ponto Café/)
})
