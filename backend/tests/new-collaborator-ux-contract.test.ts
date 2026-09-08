import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const screen = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/AdminNewCollaboratorScreen.kt', import.meta.url),
  'utf8',
)

test('cadastro de colaborador mantém rascunho e leva ao código de acesso', () => {
  assert.match(screen, /FormDraftRegistry\.adminCollaborator\(viewModel\)/)
  assert.match(screen, /trackCollaboratorDraftSubmission/)
  assert.match(screen, /viewModel\.criarColaborador\(cleanName, cleanSector, cleanShift\)/)
  assert.match(screen, /Salvar colaborador/)
})

test('cadastro usa UX guiada para setor e turno', () => {
  assert.match(screen, /sectorSuggestions/)
  assert.match(screen, /state\.colaboradores/)
  assert.match(screen, /LazyRow/)
  assert.match(screen, /CollaboratorShiftOptions = listOf\("A", "B", "C"\)/)
  assert.match(screen, /items\(CollaboratorShiftOptions, key = \{ "shift-\$it" \}\)/)
  assert.match(screen, /FilterChip\(/)
})

test('ação principal informa o que falta e fica separada do conteúdo', () => {
  assert.match(screen, /Scaffold\(/)
  assert.match(screen, /bottomBar = \{/)
  assert.match(screen, /CollaboratorBottomActions/)
  assert.match(screen, /Informe o nome completo para continuar/)
  assert.match(screen, /Tudo pronto\. Depois é só gerar um código/)
})

test('supervisor continua como conta de acesso, não colaborador facial', () => {
  // Confundir as duas coisas é o erro clássico desta tela: um Supervisor não
  // bate ponto, e um colaborador não faz login.
  assert.match(screen, /Contas de Supervisor e Administrador ficam separadas/)
  assert.match(screen, /Cadastrar Supervisor \/ conta de acesso/)
  assert.match(screen, /onSupervisor = viewModel::abrirNovaConta/)
})
