import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const routes = readFileSync(new URL('../src/routes/manual-pause-routes.ts', import.meta.url), 'utf8')
const migration = readFileSync(new URL('../../database/011_manual_pause_open.sql', import.meta.url), 'utf8')
const application = readFileSync(new URL('../src/application.ts', import.meta.url), 'utf8')
const adminClient = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/data/AdminApiClient.kt', import.meta.url),
  'utf8',
)

test('a rota de abertura manual existe e esta montada nos dois prefixos', () => {
  assert.match(routes, /routes\.post\('\/pausas\/manual\/iniciar'/)
  assert.match(application, /app\.route\('\/admin', adminManualPauseRoutes\)/)
  assert.match(application, /app\.route\('\/supervisor', supervisorManualPauseRoutes\)/)
})

test('a abertura manual exige motivo e grava quem a fez', () => {
  assert.match(routes, /const iniciarManualSchema = z\.object\(/)
  assert.match(routes, /motivo: z\.string\(\)\.trim\(\)\.min\(MOTIVO_MINIMO\)\.max\(200\)/)
  assert.match(routes, /inicio_registrado_manualmente,inicio_motivo_manual/)
  assert.match(routes, /inicio_ator_auth_id,inicio_ator_tipo,inicio_registrado_em/)
  assert.match(routes, /'PAUSA_INICIADA_MANUALMENTE','PAUSA'/)
})

test('a abertura manual nao faz verificacao previa; quem recusa e a base', () => {
  // Decisao explicita: quem usa esta rota esta a corrigir algo ja quebrado, e as
  // recusas do fluxo biometrico bloqueiam justamente essa correcao. O que NAO se
  // aceita e um erro de base a sair como 500 na cara do operador.
  const encontrada = routes.match(/async function iniciarPausaManual\([\s\S]*?\r?\n\}\r?\n/)
  assert.ok(encontrada, 'iniciarPausaManual deve ser localizavel')
  const corpo = encontrada[0]

  assert.doesNotMatch(corpo, /from colaboradores/, 'nao deve pre-verificar o colaborador')
  assert.doesNotMatch(corpo, /select id from pausas_cafe/, 'nao deve pre-verificar pausas')

  assert.match(corpo, /error\?\.code === '23505'/, 'unique violation tem de virar mensagem')
  assert.match(corpo, /error\?\.code === '23503'/, 'FK violation tem de virar mensagem')
  assert.match(corpo, /ja registrou a pausa deste periodo hoje/)
  assert.match(corpo, /Colaborador nao encontrado/)
})

test('o invariante de prova nao evapora: NOT NULL sai, check disjuntivo entra', () => {
  assert.match(migration, /alter column dispositivo_inicio_id drop not null/)
  assert.match(migration, /alter column verificacao_inicio_id drop not null/)
  assert.match(migration, /add constraint ck_pausa_inicio_coerente check/)

  // Ramo biométrico: exige as duas provas e proíbe campos manuais.
  assert.match(migration, /inicio_registrado_manualmente = false[\s\S]*?verificacao_inicio_id is not null/)
  // Ramo manual: proíbe as provas e exige ator + motivo não vazio.
  assert.match(migration, /inicio_registrado_manualmente = true[\s\S]*?verificacao_inicio_id is null/)
  assert.match(migration, /length\(btrim\(inicio_motivo_manual\)\) > 0/)
  assert.match(migration, /inicio_ator_auth_id is not null/)
})

test('o corpo que o Android envia bate com o schema da rota', () => {
  assert.match(adminClient, /data class RegistrarPausaManualRequest\(\s*\n\s*val colaboradorId: String,\s*\n\s*val motivo: String,/)
  assert.match(routes, /const iniciarManualSchema = z\.object\(\{\s*\n\s*colaboradorId: uuidSchema,\s*\n\s*motivo:/)
})

test('a resposta cobre os campos que o Android desserializa', () => {
  for (const campo of ['periodo', 'limiteSegundos', 'inicioEm', 'inicioLocal', 'retornoAteLocal']) {
    assert.match(adminClient, new RegExp(`val ${campo}:`), `RegistrarPausaManualResponse sem ${campo}`)
    assert.match(routes, new RegExp(`${campo}[,:]`), `ManualStartResponse sem ${campo}`)
  }
  assert.match(routes, /registradoPor: \{ atorTipo: ator\.papel, atorNome: ator\.nome \}/)
})

test('os comentarios do cliente nao dizem mais que a rota nao existe', () => {
  assert.doesNotMatch(adminClient, /iniciar NÃO existe/)
  assert.doesNotMatch(adminClient, /Endpoints ainda não existem no backend/)
})

test('o minimo do motivo e o mesmo no campo e na rota', () => {
  const admin = readFileSync(
    new URL('../../app/src/main/java/com/pontocafe/app/AdminViewModel.kt', import.meta.url),
    'utf8',
  )
  const supervisor = readFileSync(
    new URL('../../app/src/main/java/com/pontocafe/app/SupervisorViewModel.kt', import.meta.url),
    'utf8',
  )

  // O cliente validava 2 e o servidor exigia 3: um motivo de duas letras passava
  // no campo e voltava recusado pela rede. Agora o limite é um só, escrito uma
  // vez, e são 20 — porque 3 aceita "esq", e quem lê a auditoria seis meses
  // depois precisa de uma frase.
  assert.ok(admin.includes('internal const val MOTIVO_MANUAL_MINIMO = 20'))
  assert.ok(routes.includes('const MOTIVO_MINIMO = 20'), 'a rota exige uma frase, não uma sigla')

  for (const [label, fonte] of [['admin', admin], ['supervisor', supervisor]] as const) {
    // O fecho do parêntese importa: sem ele, "< 2" casa dentro de "< 20".
    assert.ok(
      !fonte.includes('motivo.trim().length < 2)'),
      `${label} não pode validar um mínimo diferente do da rota`,
    )
    assert.ok(
      fonte.includes('motivo.trim().length < MOTIVO_MANUAL_MINIMO'),
      `${label} precisa usar o limite compartilhado`,
    )
    // A rota existe e está montada nos dois prefixos desde a 011; o comentário
    // dizia o contrário e mandava quem lesse procurar um problema inexistente.
    assert.ok(
      !fonte.includes('Endpoint ainda'),
      `${label} tem comentário dizendo que a rota não existe`,
    )
    assert.ok(
      !fonte.includes('verificacaoToken'),
      `${label} ainda descreve o fluxo biométrico, que foi removido`,
    )
  }
})
