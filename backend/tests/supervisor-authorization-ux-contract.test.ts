import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'

const root = path.resolve(process.cwd(), '..')
const ui = fs.readFileSync(
  path.join(root, 'app/src/main/java/com/pontocafe/app/ui/SupervisorAuthorizationScreen.kt'),
  'utf8',
)
const viewModel = fs.readFileSync(
  path.join(root, 'app/src/main/java/com/pontocafe/app/SupervisorViewModel.kt'),
  'utf8',
)
const apiClient = fs.readFileSync(
  path.join(root, 'app/src/main/java/com/pontocafe/app/data/SupervisorApiClient.kt'),
  'utf8',
)
const routes = fs.readFileSync(
  path.join(process.cwd(), 'src/routes/authorization-routes.ts'),
  'utf8',
)

test('Supervisor usa fluxo de liberação prévia sem código visível', () => {
  assert.match(ui, /Liberações fora do horário/)
  assert.match(ui, /nenhum código é digitado no terminal/)
  assert.doesNotMatch(ui, /Gerar código de 6 dígitos/)
  assert.doesNotMatch(ui, /Código temporário/)
})

test('seleção colapsa para configuração e CTA fica persistente', () => {
  assert.match(ui, /else if \(selecionado == null\)/)
  assert.match(ui, /A lista foi recolhida para reduzir erros de seleção/)
  assert.match(ui, /PontoCafePremium\.glassStrong/)
  assert.match(ui, /Liberar pausa/)
})

test('Supervisor não escolhe manualmente manhã ou tarde', () => {
  assert.match(ui, /Período automático/)
  assert.match(ui, /Você não precisa escolher Manhã ou Tarde/)
  assert.doesNotMatch(ui, /selected = periodo == "MANHA"/)
  assert.doesNotMatch(ui, /selected = periodo == "TARDE"/)
  assert.match(routes, /async function inferirPeriodoAtual/)
  assert.match(routes, /periodoDefinidoAutomaticamente: true/)
  assert.match(routes, /now\(\) at time zone \$1/)
})

test('UX exige confirmação, oferece motivos rápidos e mostra vencimento', () => {
  assert.match(ui, /AlertDialog/)
  assert.match(ui, /Confirmar liberação/)
  assert.match(ui, /Necessidade operacional/)
  assert.match(ui, /Atraso na produção/)
  assert.match(ui, /Orientação do Supervisor/)
  assert.match(ui, /Liberada até/)
})

test('cancelamento é ação real de servidor e auditada', () => {
  assert.match(ui, /Cancelar liberação/)
  assert.match(viewModel, /fun cancelarAutorizacao/)
  assert.match(apiClient, /supervisor\/autorizacoes\/cancelar/)
  assert.match(routes, /post\('\/autorizacoes\/cancelar'/)
  assert.match(routes, /CANCELAR_LIBERACAO_FORA_HORARIO/)
  assert.match(routes, /cancelada_em=now\(\)/)
})

test('segredo legado não é guardado como código visível no estado do Supervisor', () => {
  assert.match(viewModel, /authorizationCode = authorization\.id/)
  assert.match(routes, /Campo legado mantido para compatibilidade/)
})
