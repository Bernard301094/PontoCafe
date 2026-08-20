import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const shared = readFileSync(new URL('../../app/src/main/java/com/pontocafe/app/ui/PeopleExperienceComponents.kt', import.meta.url), 'utf8')
const admin = readFileSync(new URL('../../app/src/main/java/com/pontocafe/app/ui/AdminPeopleScreenV4.kt', import.meta.url), 'utf8')
const supervisor = readFileSync(new URL('../../app/src/main/java/com/pontocafe/app/ui/SupervisorPeopleScreenV3.kt', import.meta.url), 'utf8')

test('Pessoas usa componentes compartilhados entre admin e supervisor', () => {
  assert.match(admin, /PeoplePersonCard/)
  assert.match(supervisor, /PeoplePersonCard/)
  assert.match(admin, /PeopleFilterSheet/)
  assert.match(supervisor, /PeopleFilterSheet/)
  assert.match(shared, /internal fun PeoplePersonCard/)
  assert.match(shared, /internal fun PersonActionBottomSheet/)
})

test('celular usa bottom sheet em vez de expandir cards na lista', () => {
  assert.match(shared, /ModalBottomSheet/)
  assert.match(admin, /PersonActionBottomSheet/)
  assert.match(supervisor, /PersonActionBottomSheet/)
  assert.doesNotMatch(admin, /AnimatedVisibility\(expanded/)
  assert.doesNotMatch(supervisor, /SupervisorPersonCardV3/)
})

test('tela expandida usa master-detail', () => {
  assert.match(admin, /expandedLayout/)
  assert.match(admin, /PersonDetailPanel/)
  assert.match(supervisor, /expandedLayout/)
  assert.match(supervisor, /PersonDetailPanel/)
  assert.match(shared, /Selecione uma pessoa/)
})

test('admin separa colaboradores de acessos e reduz ações permanentes', () => {
  assert.match(admin, /AdminPeopleSection\.COLLABORATORS/)
  assert.match(admin, /AdminPeopleSection\.ACCESS/)
  assert.match(admin, /PeopleSectionSwitch/)
  assert.match(admin, /ExtendedFloatingActionButton/)
  assert.match(admin, /Importar CSV/)
  assert.match(admin, /Selecionar pessoas/)
  assert.doesNotMatch(admin, /PeopleSummaryV4/)
})

test('filtros adicionais cobrem setor e turno sem alterar regra de negócio', () => {
  assert.match(shared, /Setor/)
  assert.match(shared, /Turno/)
  assert.match(admin, /sectorFilter/)
  assert.match(admin, /shiftFilter/)
  assert.match(supervisor, /sectorFilter/)
  assert.match(supervisor, /shiftFilter/)
})

test('ações destrutivas ficam atrás de Mais opções', () => {
  assert.match(shared, /Mais opções/)
  assert.match(shared, /AnimatedVisibility\(showMore\)/)
  assert.match(shared, /Excluir biometria/)
  assert.match(shared, /Excluir colaborador/)
})
