import type { PoolClient } from 'pg'
import { config } from './config.js'
import { normalizeAccessCode } from './domain/access-code.js'
import { query } from './db.js'
import { newId, secureCodeEquals } from './security.js'

export type CollaboratorSummary = {
  id: string
  matricula: string | null
  nome: string
  setor: string | null
  turno: string | null
}

export type StartResponse = {
  id: string
  periodo: 'MANHA' | 'TARDE'
  limiteSegundos: number
  carenciaSegundos: number
  foraHorario: boolean
  inicioEm: string
  inicioLocal: string
  /** Instante em que a carência acaba e o limite começa a correr. */
  contagemInicioEm: string
  contagemInicioLocal: string
  retornoAteLocal: string
}

export type FinishResponse = {
  id: string
  inicioLocal: string
  fimEm: string
  fimLocal: string
  duracaoSegundos: number
  /** Segundos descontados da carência: é este valor que se compara ao limite. */
  tempoContadoSegundos: number
  limiteSegundos: number
  carenciaSegundos: number
  excedeuLimite: boolean
}

export type RegistrationOutcome =
  | { status: 'INICIO'; pauseId: string; colaborador: CollaboratorSummary; inicio: StartResponse }
  | { status: 'RETORNO'; pauseId: string; colaborador: CollaboratorSummary; retorno: FinishResponse }

export type AccessCodeErrorCode =
  | 'COLABORADOR_INVALIDO'
  | 'CODIGO_INVALIDO'
  | 'CODIGO_EXPIRADO'
  | 'PAUSA_PERIODO_JA_UTILIZADA'
  | 'PAUSA_JA_ABERTA'
  | 'PAUSA_NAO_ENCONTRADA'
  | 'SEM_REGRA_CAFE'

export class AccessCodeError extends Error {
  constructor(
    message: string,
    readonly status: 400 | 403 | 404 | 409,
    readonly codigo: AccessCodeErrorCode,
    readonly details?: { pauseId?: string; periodo?: 'MANHA' | 'TARDE' },
  ) {
    super(message)
    this.name = 'AccessCodeError'
  }
}

type LiveCodeRow = {
  id: string
  codigo: string
  expira_em: string
  saida_em: string | null
  pausa_id: string | null
  expirado: boolean
}

function periodoLabel(periodo: 'MANHA' | 'TARDE'): string {
  return periodo === 'MANHA' ? 'manhã' : 'tarde'
}

function durationLabel(seconds: number): string {
  const minutes = Math.floor(seconds / 60)
  const rest = seconds % 60
  if (minutes <= 0) return `${rest} s`
  return rest > 0 ? `${minutes} min ${rest} s` : `${minutes} min`
}

/**
 * Conta as tentativas erradas recentes de um colaborador.
 *
 * O código tem 32^6 combinações, então adivinhá-lo às cegas não é a ameaça
 * realista — o que este limite trava é o quiosque virar um oráculo onde alguém
 * testa em minutos os poucos códigos que viu de relance no papel do Supervisor.
 */
export async function recentFailedAttempts(collaboratorId: string): Promise<number> {
  const result = await query<{ total: number }>(
    `select count(*)::int as total
       from auditoria
      where acao='CODIGO_ACESSO_TENTATIVA_INVALIDA'
        and entidade='COLABORADOR'
        and entidade_id=$1
        and criado_em > now() - ($2::text || ' seconds')::interval`,
    [collaboratorId, config.accessCodeAttemptWindowSeconds],
  )
  return result.rows[0]?.total ?? 0
}

export async function recordFailedAttempt(params: {
  collaboratorId: string
  deviceId: string
  deviceName: string
  motivo: AccessCodeErrorCode
}): Promise<void> {
  await query(
    `insert into auditoria (ator_tipo,acao,entidade,entidade_id,detalhes)
     values ('DISPOSITIVO','CODIGO_ACESSO_TENTATIVA_INVALIDA','COLABORADOR',$1,$2::jsonb)`,
    [params.collaboratorId, JSON.stringify({
      dispositivoId: params.deviceId,
      dispositivoNome: params.deviceName,
      motivo: params.motivo,
      tentativaEm: new Date().toISOString(),
    })],
  )
}

async function auditRepeatedAttempt(
  client: PoolClient,
  params: {
    collaboratorId: string
    collaboratorName: string
    deviceId: string
    deviceName: string
    pauseId: string
    periodo: 'MANHA' | 'TARDE'
    origem: string
  },
): Promise<void> {
  await client.query(
    `insert into auditoria (ator_tipo,acao,entidade,entidade_id,detalhes)
     values ('DISPOSITIVO','TENTATIVA_PONTO_REPETIDA','PAUSA',$1,$2::jsonb)`,
    [params.pauseId, JSON.stringify({
      colaboradorId: params.collaboratorId,
      colaboradorNome: params.collaboratorName,
      dispositivoId: params.deviceId,
      dispositivoNome: params.deviceName,
      periodo: params.periodo,
      tentativaEm: new Date().toISOString(),
      origem: params.origem,
      motivo: 'PAUSA_PERIODO_JA_UTILIZADA',
    })],
  )
}

/**
 * Resolve o período (MANHÃ/TARDE) de uma pausa e o limite que se lhe aplica.
 *
 * O código de acesso libera o café a qualquer hora — a regra deixou de ser um
 * portão e passou a ser a etiqueta que os relatórios usam. Fora de qualquer
 * janela configurada, escolhe-se a regra temporalmente mais próxima e a pausa
 * fica marcada como `fora_horario`.
 */
async function resolvePeriod(
  client: PoolClient,
  occurredAt: string | null,
): Promise<{ periodo: 'MANHA' | 'TARDE'; limiteSegundos: number; foraHorario: boolean }> {
  const inside = await client.query<{ periodo: 'MANHA' | 'TARDE'; limite_segundos: number }>(
    `select periodo,limite_segundos
       from regras_cafe
      where ativo=true
        and (coalesce($1::timestamptz,now()) at time zone $2)::time>=inicio
        and (coalesce($1::timestamptz,now()) at time zone $2)::time<fim
      order by inicio
      limit 1`,
    [occurredAt, config.appTimezone],
  )
  if (inside.rows[0]) {
    return {
      periodo: inside.rows[0].periodo,
      limiteSegundos: inside.rows[0].limite_segundos,
      foraHorario: false,
    }
  }

  const nearest = await client.query<{ periodo: 'MANHA' | 'TARDE'; limite_segundos: number }>(
    `select periodo,limite_segundos
       from regras_cafe
      where ativo=true
      order by
        least(
          abs(extract(epoch from ((coalesce($1::timestamptz,now()) at time zone $2)::time - inicio))),
          abs(extract(epoch from ((coalesce($1::timestamptz,now()) at time zone $2)::time - fim)))
        ) asc,
        inicio asc
      limit 1`,
    [occurredAt, config.appTimezone],
  )
  const rule = nearest.rows[0]
  if (!rule) {
    throw new AccessCodeError(
      'Não existe regra de café ativa para classificar esta pausa. Configure os horários na área Admin.',
      409,
      'SEM_REGRA_CAFE',
    )
  }
  return { periodo: rule.periodo, limiteSegundos: rule.limite_segundos, foraHorario: true }
}

/**
 * Aplica um código de acesso: a primeira apresentação abre a pausa, a segunda
 * fecha-a. Quem decide qual das duas é o servidor — o quiosque só transporta o
 * par (pessoa, código), e é por isso que não existe forma de fechar uma pausa
 * com um código diferente do que a abriu.
 */
export async function applyAccessCode(
  client: PoolClient,
  params: {
    deviceId: string
    deviceName: string
    collaboratorId: string
    code: string
    /** ISO-8601. Usado pela fila offline; `null` significa "agora". */
    occurredAt?: string | null
    origem: 'QUIOSQUE' | 'OFFLINE'
  },
): Promise<RegistrationOutcome> {
  const normalized = normalizeAccessCode(params.code)
  if (!normalized) {
    throw new AccessCodeError('Código inválido. Confira os 6 caracteres com o Supervisor.', 403, 'CODIGO_INVALIDO')
  }
  const occurredAt = params.occurredAt ?? null

  const collaborator = (await client.query<CollaboratorSummary>(
    `select id,matricula,nome,setor,turno
       from colaboradores
      where id=$1 and ativo=true
      limit 1
      for update`,
    [params.collaboratorId],
  )).rows[0]
  if (!collaborator) {
    throw new AccessCodeError('Colaborador não encontrado ou inativo.', 404, 'COLABORADOR_INVALIDO')
  }

  // O índice ux_codigo_acesso_vivo garante no máximo uma linha viva por
  // (colaborador, código), e a emissão cancela a anterior — mas a comparação é
  // feita aqui, em tempo constante, e não no WHERE, para o banco nunca precisar
  // de receber o segredo digitado como parâmetro de busca.
  const liveCodes = await client.query<LiveCodeRow>(
    `select id,codigo,expira_em::text,saida_em::text,pausa_id,
            (expira_em <= coalesce($2::timestamptz,now())) as expirado
       from codigos_acesso
      where colaborador_id=$1
        and cancelado_em is null
        and retorno_em is null
      order by criado_em desc
      for update`,
    [params.collaboratorId, occurredAt],
  )

  const match = liveCodes.rows.find((row) => secureCodeEquals(row.codigo, normalized))
  if (!match) {
    throw new AccessCodeError(
      'Código inválido para este colaborador. Confira o nome selecionado e peça um código novo ao Supervisor.',
      403,
      'CODIGO_INVALIDO',
    )
  }

  if (match.saida_em) {
    return finishPause(client, { ...params, occurredAt, collaborator, code: match })
  }

  if (match.expirado) {
    throw new AccessCodeError(
      'Este código expirou antes de ser usado. Peça um código novo ao Supervisor.',
      403,
      'CODIGO_EXPIRADO',
    )
  }

  return startPause(client, { ...params, occurredAt, collaborator, code: match })
}

async function startPause(
  client: PoolClient,
  params: {
    deviceId: string
    deviceName: string
    collaborator: CollaboratorSummary
    code: LiveCodeRow
    occurredAt: string | null
    origem: 'QUIOSQUE' | 'OFFLINE'
  },
): Promise<RegistrationOutcome> {
  const open = await client.query<{ id: string }>(
    `select id from pausas_cafe
      where colaborador_id=$1 and fim_em is null
      order by inicio_em desc limit 1 for update`,
    [params.collaborator.id],
  )
  if (open.rows[0]) {
    throw new AccessCodeError(
      'Este colaborador já tem uma pausa aberta. Registre o retorno antes de sair de novo.',
      409,
      'PAUSA_JA_ABERTA',
      { pauseId: open.rows[0].id },
    )
  }

  const { periodo, limiteSegundos, foraHorario } = await resolvePeriod(client, params.occurredAt)
  const carenciaSegundos = config.coffeeGraceSeconds

  const alreadyUsed = await client.query<{ id: string }>(
    `select id from pausas_cafe
      where colaborador_id=$1 and periodo=$2
        and (inicio_em at time zone $3)::date=(coalesce($4::timestamptz,now()) at time zone $3)::date
      order by inicio_em desc limit 1 for update`,
    [params.collaborator.id, periodo, config.appTimezone, params.occurredAt],
  )
  if (alreadyUsed.rows[0]) {
    await auditRepeatedAttempt(client, {
      collaboratorId: params.collaborator.id,
      collaboratorName: params.collaborator.nome,
      deviceId: params.deviceId,
      deviceName: params.deviceName,
      pauseId: alreadyUsed.rows[0].id,
      periodo,
      origem: params.origem,
    })
    throw new AccessCodeError(
      `Pausa da ${periodoLabel(periodo)} já utilizada hoje. Esta nova tentativa ficou registrada.`,
      409,
      'PAUSA_PERIODO_JA_UTILIZADA',
      { pauseId: alreadyUsed.rows[0].id, periodo },
    )
  }

  const pauseId = newId()
  let inserted
  try {
    inserted = await client.query<{
      inicio_em: string
      inicio_local: string
      contagem_inicio_em: string
      contagem_inicio_local: string
      retorno_ate_local: string
    }>(
      `insert into pausas_cafe
         (id,colaborador_id,periodo,inicio_em,limite_segundos,carencia_segundos,fora_horario,
          dispositivo_inicio_id,codigo_acesso_id)
       values ($1,$2,$3,coalesce($4::timestamptz,now()),$5,$6,$7,$8,$9)
       returning inicio_em::text,
                 to_char(inicio_em at time zone $10,'HH24:MI') as inicio_local,
                 (inicio_em + (carencia_segundos * interval '1 second'))::text as contagem_inicio_em,
                 to_char((inicio_em + (carencia_segundos * interval '1 second')) at time zone $10,'HH24:MI')
                   as contagem_inicio_local,
                 to_char(
                   (inicio_em + ((carencia_segundos + limite_segundos) * interval '1 second')) at time zone $10,
                   'HH24:MI'
                 ) as retorno_ate_local`,
      [
        pauseId,
        params.collaborator.id,
        periodo,
        params.occurredAt,
        limiteSegundos,
        carenciaSegundos,
        foraHorario,
        params.deviceId,
        params.code.id,
        config.appTimezone,
      ],
    )
  } catch (error: unknown) {
    if (typeof error === 'object' && error !== null && (error as { code?: unknown }).code === '23505') {
      throw new AccessCodeError(
        'Este colaborador já registrou esta pausa hoje ou possui uma pausa aberta.',
        409,
        'PAUSA_PERIODO_JA_UTILIZADA',
        { periodo },
      )
    }
    throw error
  }

  const consumed = await client.query(
    `update codigos_acesso
        set saida_em=coalesce($2::timestamptz,now()),
            pausa_id=$3
      where id=$1 and saida_em is null and cancelado_em is null and retorno_em is null
      returning id`,
    [params.code.id, params.occurredAt, pauseId],
  )
  if (consumed.rowCount !== 1) {
    throw new AccessCodeError('Este código acabou de ser usado em outro registro.', 409, 'CODIGO_INVALIDO')
  }

  const row = inserted.rows[0]!
  return {
    status: 'INICIO',
    pauseId,
    colaborador: params.collaborator,
    inicio: {
      id: pauseId,
      periodo,
      limiteSegundos,
      carenciaSegundos,
      foraHorario,
      inicioEm: row.inicio_em,
      inicioLocal: row.inicio_local,
      contagemInicioEm: row.contagem_inicio_em,
      contagemInicioLocal: row.contagem_inicio_local,
      retornoAteLocal: row.retorno_ate_local,
    },
  }
}

async function finishPause(
  client: PoolClient,
  params: {
    deviceId: string
    deviceName: string
    collaborator: CollaboratorSummary
    code: LiveCodeRow
    occurredAt: string | null
    origem: 'QUIOSQUE' | 'OFFLINE'
  },
): Promise<RegistrationOutcome> {
  const open = await client.query<{
    id: string
    inicio_em: string
    limite_segundos: number
    carencia_segundos: number
  }>(
    `select id,inicio_em::text,limite_segundos,carencia_segundos
       from pausas_cafe
      where colaborador_id=$1 and fim_em is null
      order by inicio_em desc limit 1 for update`,
    [params.collaborator.id],
  )
  const pause = open.rows[0]
  if (!pause) {
    throw new AccessCodeError(
      'Este código já foi usado na saída e no retorno. Não há pausa aberta para fechar.',
      404,
      'PAUSA_NAO_ENCONTRADA',
    )
  }

  // `ux_pausa_aberta_por_colaborador` já garante uma pausa aberta por pessoa, e a
  // emissão garante um código vivo por pessoa — então estas duas linhas deveriam
  // sempre apontar uma para a outra. Se não apontarem, algo escreveu fora deste
  // caminho (fecho manual concorrente, correção direta no banco) e fechar a pausa
  // errada seria pior do que recusar.
  if (params.code.pausa_id && params.code.pausa_id !== pause.id) {
    throw new AccessCodeError(
      'Este código pertence a outra pausa. Chame o Supervisor para registrar o retorno.',
      409,
      'PAUSA_NAO_ENCONTRADA',
    )
  }

  const finished = await client.query<{
    fim_em: string
    inicio_local: string
    fim_local: string
    duracao_segundos: number
  }>(
    `update pausas_cafe
        set fim_em=greatest(inicio_em,coalesce($2::timestamptz,now())),
            dispositivo_fim_id=$3
      where id=$1
      returning fim_em::text,
                to_char(inicio_em at time zone $4,'HH24:MI') as inicio_local,
                to_char(fim_em at time zone $4,'HH24:MI') as fim_local,
                floor(extract(epoch from (fim_em-inicio_em)))::int as duracao_segundos`,
    [pause.id, params.occurredAt, params.deviceId, config.appTimezone],
  )
  const row = finished.rows[0]!

  const consumed = await client.query(
    `update codigos_acesso
        set retorno_em=greatest(saida_em,coalesce($2::timestamptz,now()))
      where id=$1 and saida_em is not null and retorno_em is null and cancelado_em is null
      returning id`,
    [params.code.id, params.occurredAt],
  )
  if (consumed.rowCount !== 1) {
    throw new AccessCodeError('Este código acabou de ser usado em outro registro.', 409, 'CODIGO_INVALIDO')
  }

  const tempoContadoSegundos = Math.max(0, row.duracao_segundos - pause.carencia_segundos)
  return {
    status: 'RETORNO',
    pauseId: pause.id,
    colaborador: params.collaborator,
    retorno: {
      id: pause.id,
      inicioLocal: row.inicio_local,
      fimEm: row.fim_em,
      fimLocal: row.fim_local,
      duracaoSegundos: row.duracao_segundos,
      tempoContadoSegundos,
      limiteSegundos: pause.limite_segundos,
      carenciaSegundos: pause.carencia_segundos,
      excedeuLimite: tempoContadoSegundos > pause.limite_segundos,
    },
  }
}

export { durationLabel, periodoLabel }
