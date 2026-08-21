import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const read = (path: string) => readFileSync(new URL(path, import.meta.url), 'utf8')
const screen = read('../../app/src/main/java/com/pontocafe/app/ui/AdminAuthorizationScreen.kt')
const viewModel = read('../../app/src/main/java/com/pontocafe/app/AdminViewModel.kt')
const api = read('../../app/src/main/java/com/pontocafe/app/data/AdminApiClient.kt')

test('autorização usa fluxo progressivo e recolhe a lista após seleção', () => {
  assert.match(screen, /if \(selecionado == null\)/)
  assert.match(screen, /Escolha o colaborador/)
  assert.match(screen, /Motivo/)
  assert.match(screen, /SelectedCollaboratorCard/)
  assert.match(screen, /Text\("Alterar"\)/)
  assert.doesNotMatch(screen, /PeriodChip|selected = periodo ==/)
})

test('busca cobre nome setor e turno e não limita arbitrariamente os resultados', () => {
  assert.match(screen, /colaborador\.nome\.contains\(query, ignoreCase = true\)/)
  assert.match(screen, /colaborador\.setor\?\.contains\(query, ignoreCase = true\)/)
  assert.match(screen, /colaborador\.turno\?\.contains\(query, ignoreCase = true\)/)
  assert.doesNotMatch(screen, /filtrados\.take\(30\)/)
})

test('ação principal autoriza diretamente e exige formulário válido', () => {
  assert.match(screen, /state\.authorizationId == null && selecionado != null/)
  assert.match(screen, /val podeAutorizar = selecionado != null && motivoValido && !state\.carregando/)
  assert.match(screen, /text = "Autorizar pausa"/)
  assert.match(screen, /selecionado\?\.let \{ viewModel\.autorizarPausa\(it, motivo\) \}/)
  assert.doesNotMatch(screen, /Gerar código de 6 dígitos|Código de uso único/)
})

test('resultado confirma liberação facial, validade, uso único e cancelamento', () => {
  assert.match(screen, /Autorização concedida/)
  assert.match(screen, /O colaborador já pode bater o ponto/)
  assert.match(screen, /Disponível por aproximadamente/)
  assert.match(screen, /uso único/i)
  assert.match(screen, /Cancelar autorização/)
  assert.match(screen, /Autorizar outra pessoa/)
})

test('estado e cliente Android não guardam nem enviam código de autorização', () => {
  const requestStart = api.indexOf('data class CreateAuthorizationRequest(')
  const requestEnd = api.indexOf('data class AuthorizationCreatedResponse(', requestStart)
  const responseEnd = api.indexOf('data class AdminCoffeeRule(', requestEnd)
  assert.ok(requestStart >= 0 && requestEnd > requestStart && responseEnd > requestEnd)

  assert.doesNotMatch(api.slice(requestStart, requestEnd), /codigo|periodo/i)
  assert.doesNotMatch(api.slice(requestEnd, responseEnd), /codigo/i)
  assert.match(viewModel, /val authorizationId: String\? = null/)
  assert.match(viewModel, /authorizationId = auth\.id/)
  assert.doesNotMatch(viewModel, /authorizationCode|codigoAutorizacao/)
})

test('cancelamento do Admin chama o servidor e limpa somente após sucesso', () => {
  assert.match(api, /@POST\("admin\/autorizacoes\/cancelar"\)/)
  assert.match(viewModel, /repository\.cancelAuthorization\(colaborador\.id\)/)
  assert.match(viewModel, /\.onSuccess[\s\S]*authorizationId = null/)
})
