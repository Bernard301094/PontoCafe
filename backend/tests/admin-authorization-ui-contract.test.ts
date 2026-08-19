import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const screen = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/AdminAuthorizationScreen.kt', import.meta.url),
  'utf8',
)

test('autorização usa fluxo progressivo e recolhe a lista após seleção', () => {
  assert.match(screen, /if \(selecionado == null\)/)
  assert.match(screen, /Escolha o colaborador/)
  assert.match(screen, /Período da pausa/)
  assert.match(screen, /Motivo/)
  assert.match(screen, /SelectedCollaboratorCard/)
  assert.match(screen, /Text\("Alterar"\)/)
})

test('busca cobre nome setor e turno e não limita arbitrariamente os resultados', () => {
  assert.match(screen, /colaborador\.nome\.contains\(query, ignoreCase = true\)/)
  assert.match(screen, /colaborador\.setor\?\.contains\(query, ignoreCase = true\)/)
  assert.match(screen, /colaborador\.turno\?\.contains\(query, ignoreCase = true\)/)
  assert.doesNotMatch(screen, /filtrados\.take\(30\)/)
})

test('ação principal permanece fora da lista e exige formulário válido', () => {
  assert.match(screen, /if \(state\.authorizationCode == null && selecionado != null\)/)
  assert.match(screen, /val podeGerar = selecionado != null && motivoValido && !state\.carregando/)
  assert.match(screen, /Gerar código de 6 dígitos/)
})

test('resultado apresenta confirmação, validade e regra de uso único', () => {
  assert.match(screen, /Autorização criada/)
  assert.match(screen, /Código de uso único/)
  assert.match(screen, /Expira em aproximadamente/)
  assert.match(screen, /Gerar outra autorização/)
})
