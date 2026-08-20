import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const entry = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/AdminPanelScreen.kt', import.meta.url),
  'utf8',
)
const home = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/AdminHomeScreenV2.kt', import.meta.url),
  'utf8',
)
const gradle = readFileSync(
  new URL('../../app/build.gradle.kts', import.meta.url),
  'utf8',
)

test('Início usa a experiência V2 mantendo o entrypoint do shell', () => {
  assert.match(entry, /AdminHomeScreenV2\(/)
  assert.match(home, /fun AdminHomeScreenV2\(/)
  assert.match(home, /title = "Início"/)
})

test('dashboard prioriza operação atual e atenção', () => {
  assert.match(home, /"Operação agora"/)
  assert.match(home, /"Centro de atenção"/)
  assert.match(home, /OperationalPauseFilter\.ATENCAO/)
  assert.match(home, /OperationalPauseFilter\.EXCEDIDOS/)
  assert.match(home, /livePreviewLimit/)
  assert.match(home, /showAllLive/)
})

test('ações rápidas ficam próximas do topo', () => {
  assert.match(home, /"Ações rápidas"/)
  assert.match(home, /title = "Pessoas"/)
  assert.match(home, /title = "Autorizar"/)
  assert.match(home, /title = "Dispositivos"/)
})

test('Início usa painel responsivo em duas colunas no layout expandido', () => {
  assert.match(home, /if \(responsive\.isExpanded\)/)
  assert.match(home, /AdminHomeAttentionPanel/)
  assert.match(home, /AdminHomeReadinessPanel/)
  assert.match(home, /PontoCafeWindowSizeClass\.COMPACT/)
  assert.match(home, /PontoCafeWindowSizeClass\.MEDIUM/)
  assert.match(home, /PontoCafeWindowSizeClass\.EXPANDED/)
})

test('histórico continua disponível com seleção de data e preview adaptativo', () => {
  assert.match(home, /DatePickerDialog/)
  assert.match(home, /historyPreviewLimit/)
  assert.match(home, /showAllHistory/)
  assert.match(home, /HistoryPauseCard/)
})

test('release do redesign de Início é 0.12.0', () => {
  assert.match(gradle, /versionCode = 33/)
  assert.match(gradle, /versionName = "0\.12\.0"/)
})
