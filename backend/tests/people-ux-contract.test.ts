import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const screen = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/AdminPeopleScreenV3.kt', import.meta.url),
  'utf8',
)
const adminArea = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/AdminArea.kt', import.meta.url),
  'utf8',
)

test('Pessoas usa a experiência V3 no shell administrativo', () => {
  assert.match(adminArea, /AdminPrimaryDestination\.PEOPLE -> AdminPeopleScreenV3\(/)
})

test('Pessoas separa equipe, pendências biométricas e acessos', () => {
  assert.match(screen, /TEAM\("Equipe"\)/)
  assert.match(screen, /PENDING_FACE\("Rosto pendente"\)/)
  assert.match(screen, /ACCESS\("Acessos"\)/)
  assert.match(screen, /pendingFaces/)
  assert.match(screen, /readyFaces/)
})

test('lista de colaboradores é compacta e revela ações sob demanda', () => {
  assert.match(screen, /expandedId/)
  assert.match(screen, /AnimatedVisibility\(visible = expanded && !selectionMode\)/)
  assert.match(screen, /Cadastrar rosto/)
  assert.match(screen, /Atualizar rosto/)
  assert.match(screen, /Histórico/)
  assert.match(screen, /Editar/)
})

test('busca cobre dados operacionais e acessos', () => {
  assert.match(screen, /collaborator\.setor\.orEmpty\(\)\.contains/)
  assert.match(screen, /collaborator\.turno\.orEmpty\(\)\.contains/)
  assert.match(screen, /user\.email\.contains/)
  assert.match(screen, /user\.perfil\.contains/)
})

test('edição em lote mantém barra de ação persistente', () => {
  assert.match(screen, /if \(selectionMode\) \{\s*Surface\(/)
  assert.match(screen, /Alterar setor, turno ou status/)
  assert.match(screen, /showBulkDialog = true/)
})
