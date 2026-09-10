import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const screen = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/AdminPeopleScreenV4.kt', import.meta.url),
  'utf8',
)
const shared = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/PeopleExperienceComponents.kt', import.meta.url),
  'utf8',
)
const adminArea = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/AdminArea.kt', import.meta.url),
  'utf8',
)

test('Pessoas usa a experiência V4 no shell administrativo', () => {
  assert.match(adminArea, /AdminPrimaryDestination\.PEOPLE/)
  assert.match(adminArea, /AdminPeopleScreenV4\(/)
})

test('Pessoas separa colaboradores de acessos e filtra quem está em pausa', () => {
  assert.match(screen, /AdminPeopleSection\.COLLABORATORS/)
  assert.match(screen, /AdminPeopleSection\.ACCESS/)
  assert.match(screen, /PeopleFaceFilter\.EM_PAUSA/)
  assert.match(screen, /emPausaAgora/)
  assert.match(screen, /PeopleSectionSwitch/)
})

test('lista compacta abre ações fora do card no celular', () => {
  assert.match(screen, /PeoplePersonCard/)
  assert.match(screen, /PersonActionBottomSheet/)
  assert.match(shared, /ModalBottomSheet/)
  assert.doesNotMatch(screen, /AnimatedVisibility\(expanded/)
})

test('busca cobre dados operacionais e acessos', () => {
  assert.match(screen, /it\.setor\.orEmpty\(\)\.contains/)
  assert.match(screen, /it\.turno\.orEmpty\(\)\.contains/)
  assert.match(screen, /it\.email\.contains/)
  assert.match(screen, /it\.perfil\.contains/)
})

test('edição em lote mantém barra de ação persistente e seleção total', () => {
  assert.match(screen, /if \(selectionMode\)/)
  assert.match(screen, /Alterar setor, turno ou status/)
  assert.match(screen, /showBulkDialog = true/)
  assert.match(screen, /collaborators\.mapTo\(linkedSetOf\(\)\)/)
})

test('gerar um código mostra o código', () => {
  // As duas telas de Pessoas chamavam emitirCodigo e ficavam pela mensagem de
  // sucesso: os seis caracteres iam para o estado e nunca apareciam. O
  // Supervisor precisa lê-los em voz alta.
  const supervisor = readFileSync(
    new URL('../../app/src/main/java/com/pontocafe/app/ui/SupervisorPeopleScreenV3.kt', import.meta.url),
    'utf8',
  )
  for (const [label, source] of [['admin', screen], ['supervisor', supervisor]] as const) {
    assert.match(source, /emitirCodigo/, `${label} emite código`)
    assert.match(source, /PcIssuedCodeDialog/, `${label} precisa mostrar o código emitido`)
    assert.match(source, /limparCodigoEmitido/, `${label} precisa poder fechar o código`)
  }
})

test('a linha da lista é densa e a emissão não domina o card', () => {
  // Um botão primário de largura total por pessoa transformava uma lista de
  // quase cem colaboradores numa coluna de botões laranja. A ação passa a ser
  // curta e à direita do nome.
  assert.match(shared, /PeoplePersonCard/)
  assert.doesNotMatch(shared, /PcPrimaryButton\([\s\S]{0,200}Modifier\.fillMaxWidth\(\)[\s\S]{0,200}Icons\.Default\.Coffee/)
  assert.match(shared, /PcCompactAction\(/)
  // E o chip "Ordenar: …" que ficava cortado na borda saiu da faixa.
  assert.doesNotMatch(shared, /Ordenar: \$\{sort\.label\}/)
})

test('telas grandes usam master-detail', () => {
  assert.match(screen, /expandedLayout/)
  assert.match(screen, /PersonDetailPanel/)
  assert.match(shared, /Selecione uma pessoa/)
})
