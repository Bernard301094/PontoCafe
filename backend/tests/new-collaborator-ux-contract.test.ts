import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const screen = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/AdminNewCollaboratorScreen.kt', import.meta.url),
  'utf8',
)

test('cadastro de colaborador mantém rascunho e fluxo facial existente', () => {
  assert.match(screen, /FormDraftRegistry\.adminCollaborator\(viewModel\)/)
  assert.match(screen, /trackCollaboratorDraftSubmission/)
  assert.match(screen, /viewModel\.criarColaborador\(cleanName, cleanSector, cleanShift\)/)
  assert.match(screen, /Salvar e cadastrar rosto/)
})

test('cadastro usa UX guiada para setor e turno', () => {
  assert.match(screen, /sectorSuggestions/)
  assert.match(screen, /state\.colaboradores/)
  assert.match(screen, /LazyRow/)
  assert.match(screen, /CollaboratorShiftOptions = listOf\("A", "B", "C"\)/)
  assert.match(screen, /ShiftOptionCard/)
})

test('ação principal informa o que falta e fica separada do conteúdo', () => {
  assert.match(screen, /Scaffold\(/)
  assert.match(screen, /bottomBar = \{/)
  assert.match(screen, /CollaboratorBottomActions/)
  assert.match(screen, /Informe o nome completo para continuar/)
  assert.match(screen, /Tudo pronto\. O próximo passo será o cadastro facial/)
})

test('supervisor continua como conta de acesso, não colaborador facial', () => {
  assert.match(screen, /Supervisor e Administrador são contas de acesso/)
  assert.match(screen, /Cadastrar supervisor \/ conta de acesso/)
  assert.match(screen, /onSupervisor = viewModel::abrirNovaConta/)
})
