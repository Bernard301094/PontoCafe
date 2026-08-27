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
  assert.match(routes, /motivo: z\.string\(\)\.trim\(\)\.min\(3\)\.max\(200\)/)
  assert.match(routes, /inicio_registrado_manualmente,inicio_motivo_manual/)
  assert.match(routes, /inicio_ator_auth_id,inicio_ator_tipo,inicio_registrado_em/)
  assert.match(routes, /'PAUSA_INICIADA_MANUALMENTE','PAUSA'/)
})

test('a abertura manual nao e um atalho para furar as regras do quiosque', () => {
  // Sem estas tres recusas a rota manual vira o caminho fácil para o que o fluxo
  // biométrico bloqueia, e o ux_pausa_periodo_dia estoura como 500.
  assert.match(routes, /ja tem uma pausa aberta/)
  assert.match(routes, /ja registrou esta pausa hoje/)
  assert.match(routes, /Colaborador inativo/)
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
