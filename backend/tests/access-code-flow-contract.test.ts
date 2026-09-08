import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const here = dirname(fileURLToPath(import.meta.url))
const repoRoot = resolve(here, '..', '..')
const read = (relativePath: string) => readFileSync(resolve(repoRoot, relativePath), 'utf8')

const registration = read('backend/src/ponto-registration.ts')
const registrationRoutes = read('backend/src/routes/ponto-registration-routes.ts')
const accessCodeRoutes = read('backend/src/routes/access-code-routes.ts')
const pontoRoutes = read('backend/src/routes/ponto-routes.ts')
const offlineRoutes = read('backend/src/routes/offline-routes.ts')
const migration = read('database/012_access_codes.sql')
const application = read('backend/src/application.ts')
const liveRoutes = read('backend/src/routes/live-routes.ts')
const reportRoutes = read('backend/src/routes/report-routes.ts')

const kiosk = read('app/src/main/java/com/pontocafe/app/ui/PontoFlowHost.kt')
const kioskViewModel = read('app/src/main/java/com/pontocafe/app/PontoCafeViewModel.kt')
const pontoApi = read('app/src/main/java/com/pontocafe/app/data/ApiClient.kt')
const journal = read('app/src/main/java/com/pontocafe/app/data/PontoOperationJournal.kt')
const offlineStore = read('app/src/main/java/com/pontocafe/app/data/SecurePontoOfflineStore.kt')
const voiceGuidance = read('app/src/main/java/com/pontocafe/app/voice/PontoVoiceGuidance.kt')
const config = read('backend/src/config.ts')
const wranglerConfig = read('backend/wrangler.jsonc')
const accessCodeScreen = read('app/src/main/java/com/pontocafe/app/ui/AccessCodeScreen.kt')

test('o mesmo código abre e fecha a pausa, e quem decide é o servidor', () => {
  // Uma única mutação no quiosque. Se o aparelho pudesse escolher entre
  // "iniciar" e "finalizar", um cliente offline ou desatualizado tentaria
  // fechar pausas que nunca abriu.
  assert.match(registrationRoutes, /pausas\/registrar/)
  assert.doesNotMatch(registrationRoutes, /pausas\/iniciar/)
  assert.doesNotMatch(registrationRoutes, /pausas\/finalizar/)
  assert.match(registration, /if \(match\.saida_em\) \{[\s\S]*?return finishPause/)
  assert.match(registration, /return startPause/)
  assert.match(pontoApi, /ponto\/pausas\/registrar/)
})

test('o código é comparado em tempo constante e nunca vai para o WHERE', () => {
  assert.match(registration, /secureCodeEquals\(row\.codigo, normalized\)/)
  // A busca traz os códigos vivos do colaborador; o segredo digitado não é
  // usado como parâmetro de consulta.
  assert.match(registration, /where colaborador_id=\$1\s*\n\s*and cancelado_em is null\s*\n\s*and retorno_em is null/)
})

test('um código só vale para o colaborador a quem foi emitido', () => {
  assert.match(registration, /CODIGO_INVALIDO/)
  assert.match(registration, /Código inválido para este colaborador/)
  assert.match(migration, /colaborador_id uuid not null references colaboradores\(id\)/)
  assert.match(migration, /ux_codigo_acesso_vivo/)
  assert.match(migration, /on codigos_acesso \(colaborador_id, codigo\)/)
})

test('o esquema garante uma saída e um retorno, nesta ordem', () => {
  assert.match(migration, /ck_codigo_acesso_sequencia/)
  assert.match(migration, /retorno_em is null or \(saida_em is not null and retorno_em >= saida_em\)/)
  // Cancelar um código já usado na saída deixaria a pausa sem como ser fechada.
  assert.match(migration, /ck_codigo_acesso_cancelamento/)
  assert.match(migration, /cancelado_em is null or saida_em is null/)
  assert.match(accessCodeRoutes, /Cancelá-lo agora deixaria a pausa sem como ser fechada/)
})

test('a janela de saída é curta e a mesma no servidor, na tela e na voz', () => {
  // Dois minutos obriga a emitir o código com a pessoa já diante do quiosque,
  // em vez de virar um papel guardado no bolso.
  assert.match(config, /ACCESS_CODE_TTL_SECONDS', 120/)
  assert.match(wranglerConfig, /"ACCESS_CODE_TTL_SECONDS": "120"/)
  // O número nunca é escrito à mão no cliente: desce por /app-status.
  assert.match(application, /codigoValidadeSegundos: config\.accessCodeTtlSeconds/)
  assert.match(pontoApi, /val codigoValidadeSegundos: Int/)
  assert.match(kioskViewModel, /validadeCodigoSegundos = appStatus\?\.codigoValidadeSegundos/)
  assert.match(kiosk, /formatValidade\(state\.validadeCodigoSegundos\)/)
  assert.match(voiceGuidance, /kiosk\([\s\S]{0,400}state\.validadeCodigoSegundos/)
  assert.match(voiceGuidance, /blocked\(state\.erroCodigo, state\.validadeCodigoSegundos\)/)
  // A fala precisa dizer o prazo e o que fazer quando ele passa.
  assert.match(voiceGuidance, /Ele vale \$\{spokenDuration\(validadeSegundos\)\}/)
  assert.match(voiceGuidance, /Este código expirou/)
  assert.match(voiceGuidance, /Peça um código novo ao supervisor/)
  // No retorno não há prazo, e a voz diz isso para ninguém correr à toa.
  assert.match(voiceGuidance, /Ele não expira para o retorno/)
})

test('a expiração trava a saída, nunca o retorno', () => {
  // Recusar o retorno de quem já saiu deixaria a pausa aberta para sempre.
  assert.match(registration, /if \(match\.saida_em\) \{/)
  const departureBranch = registration.slice(registration.indexOf('if (match.saida_em)'))
  const expiryCheck = departureBranch.indexOf('match.expirado')
  const finishCall = departureBranch.indexOf('finishPause')
  assert.ok(finishCall >= 0 && expiryCheck > finishCall, 'a checagem de expiração precisa vir depois do retorno')
  assert.match(accessCodeRoutes, /Um código EM_PAUSA nunca expira/)
})

test('emitir um código enquanto a pessoa está fora é recusado', () => {
  assert.match(accessCodeRoutes, /saida_em is not null and retorno_em is null/)
  assert.match(accessCodeRoutes, /erro: 'EM_PAUSA' as const/)
  assert.match(accessCodeRoutes, /O código que ela levou continua válido para o retorno/)
})

test('um código pendente é substituído, não acumulado', () => {
  assert.match(accessCodeRoutes, /Uma pessoa só pode ter um código pendente por vez/)
  assert.match(accessCodeRoutes, /update codigos_acesso set cancelado_em=now\(\)/)
})

test('a colisão de sorteio é reaproveitada em vez de estourar', () => {
  assert.match(accessCodeRoutes, /CODE_GENERATION_ATTEMPTS/)
  assert.match(accessCodeRoutes, /code\?: unknown \}\)\.code === '23505'\) continue/)
})

test('a carência é gravada por pausa e entra em todo cálculo de limite', () => {
  assert.match(migration, /carencia_segundos integer not null default 0/)
  assert.match(migration, /alter column carencia_segundos set default 60/)
  assert.match(registration, /carenciaSegundos = config\.coffeeGraceSeconds/)
  assert.match(registration, /tempoContadoSegundos > pause\.limite_segundos/)
  assert.match(registration, /inicio_em \+ \(\(carencia_segundos \+ limite_segundos\)/)
  // Relatórios e a tela ao vivo comparam contra limite + carência.
  assert.match(liveRoutes, /p\.carencia_segundos \+ p\.limite_segundos/)
  assert.match(reportRoutes, /limite_segundos\+carencia_segundos/)
})

test('a tolerância aparece no comprovante do quiosque', () => {
  assert.match(kioskViewModel, /contagemComecaAs/)
  assert.match(kiosk, /Seu tempo começa a contar/)
  assert.match(kiosk, /O tempo de café começa em/)
})

test('força bruta é contada fora da transação que recusou o código', () => {
  // Registar a tentativa dentro da transação faria o ROLLBACK apagá-la, e o
  // contador ficaria sempre em zero.
  assert.match(registrationRoutes, /recentFailedAttempts/)
  assert.match(registrationRoutes, /CODIGO_BLOQUEADO_TEMPORARIAMENTE/)
  assert.match(registrationRoutes, /Fora da transação de propósito/)
  assert.match(registration, /CODIGO_ACESSO_TENTATIVA_INVALIDA/)
  assert.match(migration, /ix_auditoria_codigo_tentativa/)
})

test('a mesma tentativa repetida reaproveita o UUID da operação', () => {
  assert.match(journal, /fun prepareCode\(collaboratorId: String, code: String\)/)
  assert.match(journal, /fingerprint\("codigo:\$normalized"\)/)
  assert.doesNotMatch(journal, /embedding/)
  assert.match(pontoApi, /operationJournal\.prepareCode/)
  assert.match(pontoApi, /markUncertain/)
  assert.match(registrationRoutes, /savePontoOperation/)
})

test('a fila offline guarda o código e deixa a validação para o servidor', () => {
  assert.match(offlineStore, /val codigo: String/)
  assert.doesNotMatch(offlineStore, /val embedding: List<Float>/)
  assert.match(offlineRoutes, /applyAccessCode/)
  assert.match(offlineRoutes, /occurredAt: event\.ocorridoEm/)
  assert.match(offlineRoutes, /origem: 'OFFLINE'/)
})

test('nenhuma rota de dispositivo devolve o código em claro', () => {
  // O quiosque envia o que a pessoa digitou e recebe de volta um comprovante.
  // Nenhuma rota autenticada por token de dispositivo lê a coluna do segredo.
  for (const [label, source] of [
    ['ponto-routes', pontoRoutes],
    ['ponto-registration-routes', registrationRoutes],
    ['offline-routes', offlineRoutes],
  ] as const) {
    assert.doesNotMatch(source, /from codigos_acesso/, `${label} não pode ler codigos_acesso`)
    assert.doesNotMatch(source, /ca\.codigo/, `${label} não pode selecionar o código`)
  }
  // Só as rotas autenticadas de Admin/Supervisor leem a coluna.
  assert.match(accessCodeRoutes, /requireRole\('ADMIN', 'SUPERVISOR'\)/)
  assert.match(accessCodeRoutes, /ca\.codigo/)
  assert.match(application, /app\.route\('\/admin', accessCodeRoutes\)/)
  assert.match(application, /app\.route\('\/supervisor', accessCodeRoutes\)/)
})

test('a biometria não sobrevive em nenhuma camada', () => {
  assert.match(migration, /drop table if exists templates_faciais/)
  assert.match(migration, /drop table if exists verificacoes_faciais/)
  assert.match(migration, /drop table if exists autorizacoes/)
  assert.doesNotMatch(registration, /embedding|template|biometri/i)
  assert.doesNotMatch(kioskViewModel, /embedding|FaceFrame|biometri/i)
  assert.doesNotMatch(pontoApi, /embedding|verificacaoToken/i)
})

test('o primeiro passo do quiosque diz que é o primeiro, e não despeja a lista', () => {
  // "Encontre o seu nome" não avisava que havia um segundo passo. E despejar os
  // noventa e seis nomes obrigava a rolar antes de qualquer coisa: o seletor
  // abre uma folha com a busca já em foco, que é o gesto real de quem sabe o
  // próprio nome. A fala do passo e o título dizem a mesma frase.
  assert.match(kiosk, /PASSO 1 DE 2/)
  assert.match(kiosk, /"Toque no seu nome"/)
  assert.match(voiceGuidance, /Toque no seu nome/)
  assert.match(kiosk, /PcCollaboratorPickerField\(/)
  assert.match(kiosk, /PcCollaboratorPickerSheet\(/)
  assert.doesNotMatch(kiosk, /GridCells\.Adaptive/, 'a grade de todos os nomes saiu do primeiro passo')
})

test('quem fechou a pausa do período sai das listas até o período seguinte', () => {
  const period = read('backend/src/ponto-period.ts')
  const pontoRoutes = read('backend/src/routes/ponto-routes.ts')
  const management = read('backend/src/routes/collaborator-management-routes.ts')
  const viewModel = read('app/src/main/java/com/pontocafe/app/PontoCafeViewModel.kt')

  // A regra do período tem de ser a mesma que decide a abertura da pausa:
  // janela que contém a hora, e a mais próxima como recurso. Se divergirem, o
  // quiosque esconde alguém que o servidor deixaria sair.
  assert.match(period, /and \(now\(\) at time zone \$1\)::time>=inicio/)
  assert.match(period, /and \(now\(\) at time zone \$1\)::time<fim/)
  assert.match(period, /order by least\(/)
  // Fechou, não abriu: quem está no café agora continua na lista, porque é essa
  // pessoa que ainda precisa do quiosque para registar o retorno.
  assert.match(period, /p\.fim_em is not null/)

  // No quiosque o corte é no servidor -- lista mais curta em vez de um
  // sinalizador que o aparelho filtraria e que lhe diria quem tomou café.
  assert.match(pontoRoutes, /and not \$\{periodPauseDoneSql\('col\.id'\)\}/)
  // Na gestão vem como sinalizador, porque Pessoas usa a mesma rota e não pode
  // perder gente da lista.
  assert.match(management, /as "pausaPeriodoConcluida"/)

  // E o quiosque precisa recarregar ao voltar do comprovante: com o cache de
  // cinco minutos, quem acabou de voltar continuaria visível.
  assert.match(viewModel, /carregarColaboradores\(force = true\)[\s\S]{0,200}atualizarConectividadeESincronizar/)
})

test('o teclado do quiosque só oferece o alfabeto do código', () => {
  assert.match(kiosk, /AccessCode\.ALPHABET\.toList\(\)/)
  assert.match(kiosk, /AccessCode\.LENGTH/)
  assert.match(accessCodeScreen, /codigoFormatado/)
})
