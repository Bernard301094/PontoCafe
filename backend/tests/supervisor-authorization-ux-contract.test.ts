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

test('Supervisor usa liberação direta sem segredo visível ou digitável', () => {
  assert.match(ui, /Liberações fora do horário/)
  assert.match(ui, /Escolha apenas a pessoa e o motivo/)
  assert.match(ui, /reconhecimento facial encontrará esta liberação automaticamente/)
  assert.doesNotMatch(ui, /código|codigo|Gerar código de 6 dígitos|Código temporário/i)
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
  assert.doesNotMatch(ui, /selected = periodo == "MANHA"|selected = periodo == "TARDE"/)
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
  assert.match(ui, /uso único/i)
})

test('cancelamento é ação real de servidor e auditada', () => {
  assert.match(ui, /Cancelar liberação/)
  assert.match(viewModel, /fun cancelarAutorizacao/)
  assert.match(apiClient, /supervisor\/autorizacoes\/cancelar/)
  assert.match(routes, /post\('\/autorizacoes\/cancelar'/)
  assert.match(routes, /CANCELAR_LIBERACAO_FORA_HORARIO/)
  assert.match(routes, /cancelada_em=now\(\)/)
})

test('estado guarda apenas o identificador opaco da liberação', () => {
  assert.match(viewModel, /val authorizationId: String\? = null/)
  assert.match(viewModel, /authorizationId = authorization\.id/)
  assert.match(viewModel, /repository\.createAuthorization\(colaborador\.id, motivo\)/)
  assert.doesNotMatch(viewModel, /authorizationCode|codigoAutorizacao/)
  assert.doesNotMatch(apiClient, /authorizationCode|codigoAutorizacao/)
})
