import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const entry = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/AdminArea.kt', import.meta.url),
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

test('Início é chamado direto pelo shell, sem invólucro de compatibilidade', () => {
  assert.match(entry, /AdminHomeScreenV2\(/)
  assert.match(home, /fun AdminHomeScreenV2\(/)
  assert.match(home, /title = "Início"/)
})

test('dashboard prioriza operação atual e atenção', () => {
  assert.match(home, /OperationalPauseFilter\.ATENCAO/)
  assert.match(home, /OperationalPauseFilter\.EXCEDIDOS/)
  assert.match(home, /livePreviewLimit/)
  assert.match(home, /showAllLive/)
})

test('emitir um código é a primeira coisa que o Início oferece', () => {
  // Emitir um passe é o gesto mais frequente do dia e era o único que obrigava
  // a sair do Início: "Códigos" era um ladrilho entre outros, e a tela inicial
  // só informava. O cartão de emissão passa a ser o primeiro item da folha,
  // acima de qualquer painel.
  assert.match(home, /AccessCodeQuickIssueCard\(/)
  const atalho = home.indexOf('item("quick-code")')
  const atencao = home.indexOf('AdminHomeAttentionPanel(')
  assert.ok(atalho >= 0, 'o Início precisa do cartão de emissão')
  assert.ok(atencao > atalho, 'o cartão de emissão precisa vir antes dos painéis')

  // Sem esta carga o cartão abriria vazio: as pessoas só eram buscadas ao
  // navegar para Pessoas ou para Códigos.
  assert.match(home, /carregarAtalhoDeCodigos/)

  // Gerar sem mostrar não serve para nada: o Supervisor lê os seis caracteres
  // em voz alta para quem está do outro lado do balcão.
  assert.match(home, /PcIssuedCodeDialog/)
})

test('as outras áreas continuam a um toque', () => {
  assert.match(home, /"Ir para"/)
  assert.match(home, /title = "Pessoas"/)
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
