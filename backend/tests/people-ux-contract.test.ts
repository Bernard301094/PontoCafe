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

test('Pessoas separa colaboradores de acessos e mantém pendências como filtro', () => {
  assert.match(screen, /AdminPeopleSection\.COLLABORATORS/)
  assert.match(screen, /AdminPeopleSection\.ACCESS/)
  assert.match(screen, /PeopleFaceFilter\.PENDING/)
  assert.match(screen, /pendingFaces/)
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

test('telas grandes usam master-detail', () => {
  assert.match(screen, /expandedLayout/)
  assert.match(screen, /PersonDetailPanel/)
  assert.match(shared, /Selecione uma pessoa/)
})
