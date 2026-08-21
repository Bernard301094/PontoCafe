import { Hono } from 'hono'
import { createMiddleware } from 'hono/factory'
import { z } from 'zod'
import type { PoolClient } from 'pg'
import type { AppEnv, Device } from '../auth-runtime.js'
import { config } from '../config.js'
import { query, transaction } from '../db.js'
import {
  findPontoOperationById,
  lockPontoOperation,
  PontoOperationConflictError,
  savePontoOperation,
  type PontoOperationIdentity,
} from '../ponto-operation-idempotency.js'
import { hashToken, newId } from '../security.js'
import { parseJson, uuidSchema } from './shared.js'

const requireDevice = createMiddleware<AppEnv>(async (c, next) => {
  const token = c.req.header('X-Device-Token')?.trim()
  if (!token) return c.json({ erro: 'Dispositivo não autenticado.' }, 401)
  const result = await query<Device>(
    'select id,nome from dispositivos where token_hash=$1 and ativo=true limit 1',
    [hashToken(token)],
  )
  const device = result.rows[0]
  if (!device) return c.json({ erro: 'Dispositivo inválido.' }, 401)
  c.set('device', device)
  await next()
})

class AppError extends Error {
  constructor(
    message: string,
    readonly status: 401 | 403 | 404 | 409,
    readonly details?: { pauseId?: string; periodo?: 'MANHA' | 'TARDE' },
  ) {
    super(message)
  }
}

type StartResponse = {
  id: string
  periodo: 'MANHA' | 'TARDE'
  limiteSegundos: number
  foraHorario: boolean
  inicioEm: string
  inicioLocal: string
  retornoAteLocal: string
}

type FinishResponse = {
  id: string
  inicioLocal: string
  fimEm: string
  fimLocal: string
  duracaoSegundos: number
  limiteSegundos: number
  excedeuLimite: boolean
}

type FastStoredResponse = {
  status?: 'INICIO' | 'RETORNO'
  inicio?: StartResponse
  retorno?: FinishResponse
}

type ReconcileCollaborator = {
  id: string
  matricula: string | null
  nome: string
  setor: string | null
  turno: string | null
}

function identity(
  operationId: string | undefined,
  device: Device,
  collaboratorId: string,
  type: 'INICIAR' | 'FINALIZAR',
): PontoOperationIdentity | null {
  if (!operationId) return null
  return {
    operationId,
    deviceId: device.id,
    collaboratorId,
    type,
  }
}

async function compatibleMutationReplay<T extends StartResponse | FinishResponse>(
  client: PoolClient,
  operation: PontoOperationIdentity,
  fastStatus: 'INICIO' | 'RETORNO',
): Promise<T | null> {
  const stored = await findPontoOperationById<unknown>(client, operation.operationId)
  if (!stored) return null
  if (
    stored.deviceId !== operation.deviceId ||
    stored.collaboratorId !== operation.collaboratorId
  ) {
    throw new PontoOperationConflictError()
  }

  if (stored.type === operation.type) return stored.response as T
  if (stored.type !== 'REGISTRO_RAPIDO') throw new PontoOperationConflictError()

  const fast = stored.response as FastStoredResponse
  if (fastStatus === 'INICIO' && fast.status === 'INICIO' && fast.inicio) {
    return fast.inicio as T
  }
  if (fastStatus === 'RETORNO' && fast.status === 'RETORNO' && fast.retorno) {
    return fast.retorno as T
  }
  throw new PontoOperationConflictError()
}

async function auditRepeatedAttempt(params: {
  colaboradorId: string
  device: Device
  pauseId?: string | null
  periodo?: 'MANHA' | 'TARDE' | null
}) {
  await query(
    `insert into auditoria (ator_tipo,acao,entidade,entidade_id,detalhes)
     values ('DISPOSITIVO','TENTATIVA_PONTO_REPETIDA','PAUSA',$1,$2::jsonb)`,
    [params.pauseId ?? null, JSON.stringify({
      colaboradorId: params.colaboradorId,
      dispositivoId: params.device.id,
      dispositivoNome: params.device.nome,
      periodo: params.periodo ?? null,
      tentativaEm: new Date().toISOString(),
      origem: 'ONLINE_INICIAR',
      motivo: 'PAUSA_PERIODO_JA_UTILIZADA',
    })],
  )
}

/**
 * Intercepta somente as mutações confirmadas do fluxo tradicional e oferece
 * consulta de reconciliação para um UUID que ficou incerto no Android.
 * APKs antigos, sem operacaoId, mantêm o mesmo comportamento.
 */
export const idempotentPontoMutationRoutes = new Hono<AppEnv>()
idempotentPontoMutationRoutes.use('*', requireDevice)

idempotentPontoMutationRoutes.post('/operacoes/reconciliar', async (c) => {
  const body = await parseJson(c, z.object({
    operacaoId: uuidSchema,
    colaboradorId: uuidSchema,
  }))
  if (!body.ok) return body.response

  const device = c.get('device')
  const stored = await transaction(async (client) => {
    await lockPontoOperation(client, body.data.operacaoId, device.id)
    return findPontoOperationById<unknown>(client, body.data.operacaoId)
  })

  if (!stored) return c.json({ encontrada: false })
  if (stored.deviceId !== device.id || stored.collaboratorId !== body.data.colaboradorId) {
    return c.json({
      erro: 'O identificador desta operação pertence a outro registro do Ponto.',
      codigo: 'PONTO_OPERATION_ID_CONFLICT',
    }, 409)
  }

  const collaborator = (await query<ReconcileCollaborator>(
    `select id,matricula,nome,setor,turno
       from colaboradores
      where id=$1
      limit 1`,
    [body.data.colaboradorId],
  )).rows[0]
  if (!collaborator) return c.json({ erro: 'Colaborador não encontrado para reconciliação.' }, 404)

  if (stored.type === 'INICIAR') {
    return c.json({
      encontrada: true,
      tipo: stored.type,
      colaborador: collaborator,
      inicio: stored.response as StartResponse,
    })
  }
  if (stored.type === 'FINALIZAR') {
    return c.json({
      encontrada: true,
      tipo: stored.type,
      colaborador: collaborator,
      retorno: stored.response as FinishResponse,
    })
  }

  const fast = stored.response as FastStoredResponse
  if (fast.status === 'INICIO' && fast.inicio) {
    return c.json({
      encontrada: true,
      tipo: stored.type,
      colaborador: collaborator,
      inicio: fast.inicio,
    })
  }
  if (fast.status === 'RETORNO' && fast.retorno) {
    return c.json({
      encontrada: true,
      tipo: stored.type,
      colaborador: collaborator,
      retorno: fast.retorno,
    })
  }

  return c.json({
    erro: 'O resultado persistido desta operação não pode ser reconciliado.',
    codigo: 'PONTO_OPERATION_REPLAY_INVALID',
  }, 409)
})

idempotentPontoMutationRoutes.post('/pausas/iniciar', async (c) => {
  const body = await parseJson(c, z.object({
    operacaoId: uuidSchema.optional(),
    colaboradorId: uuidSchema,
    verificacaoToken: z.string().min(20),
  }))
  if (!body.ok) return body.response

  const device = c.get('device')
  const operation = identity(body.data.operacaoId, device, body.data.colaboradorId, 'INICIAR')

  try {
    const pausa = await transaction(async (client): Promise<StartResponse> => {
      if (operation) {
        await lockPontoOperation(client, operation.operationId, device.id)
        const replay = await compatibleMutationReplay<StartResponse>(client, operation, 'INICIO')
        if (replay) return replay
      }

      const verification = await client.query<{ id: string }>(
        `select id from verificacoes_faciais
          where token_hash=$1 and colaborador_id=$2 and dispositivo_id=$3
            and usado_em is null and expira_em>now()
          for update`,
        [hashToken(body.data.verificacaoToken), body.data.colaboradorId, device.id],
      )
      if (!verification.rows[0]) throw new AppError('Verificação facial inválida ou expirada.', 401)

      const activeRule = await client.query<{ periodo: 'MANHA' | 'TARDE'; limite_segundos: number }>(
        `select periodo,limite_segundos from regras_cafe
          where ativo=true
            and (now() at time zone $1)::time>=inicio
            and (now() at time zone $1)::time<fim
          order by inicio limit 1`,
        [config.appTimezone],
      )

      let periodo: 'MANHA' | 'TARDE'
      let limiteSegundos: number
      let foraHorario = false
      let autorizacaoId: string | null = null

      if (activeRule.rows[0]) {
        periodo = activeRule.rows[0].periodo
        limiteSegundos = activeRule.rows[0].limite_segundos
      } else {
        foraHorario = true
        const authorization = await client.query<{
          id: string
          periodo: 'MANHA' | 'TARDE'
          limite_segundos: number
        }>(
          `select a.id,a.periodo,r.limite_segundos
             from autorizacoes a
             join regras_cafe r on r.periodo=a.periodo and r.ativo=true
            where a.colaborador_id=$1
              and a.usado_em is null
              and a.cancelada_em is null
              and a.expira_em>now()
            order by a.criado_em desc
            limit 1 for update`,
          [body.data.colaboradorId],
        )
        const liberacao = authorization.rows[0]
        if (!liberacao) {
          throw new AppError(
            'Pausa não liberada. Você está fora do horário permitido. Solicite a liberação prévia ao Supervisor.',
            403,
          )
        }
        periodo = liberacao.periodo
        limiteSegundos = liberacao.limite_segundos
        autorizacaoId = liberacao.id
      }

      const alreadyUsed = await client.query<{ id: string }>(
        `select id from pausas_cafe
          where colaborador_id=$1 and periodo=$2
            and (inicio_em at time zone $3)::date=(now() at time zone $3)::date
          order by inicio_em desc limit 1 for update`,
        [body.data.colaboradorId, periodo, config.appTimezone],
      )
      if (alreadyUsed.rows[0]) {
        throw new AppError(
          'Este colaborador já registrou esta pausa hoje. A nova tentativa foi registrada para auditoria.',
          409,
          { pauseId: alreadyUsed.rows[0].id, periodo },
        )
      }

      const pauseId = newId()
      try {
        const inserted = await client.query<{ inicio_em: string }>(
          `insert into pausas_cafe
             (id,colaborador_id,periodo,limite_segundos,fora_horario,autorizacao_id,dispositivo_inicio_id,verificacao_inicio_id)
           values ($1,$2,$3,$4,$5,$6,$7,$8)
           returning inicio_em::text`,
          [
            pauseId,
            body.data.colaboradorId,
            periodo,
            limiteSegundos,
            foraHorario,
            autorizacaoId,
            device.id,
            verification.rows[0].id,
          ],
        )
        if (autorizacaoId) {
          const consumed = await client.query(
            `update autorizacoes
                set usado_em=now()
              where id=$1 and colaborador_id=$2
                and usado_em is null and cancelada_em is null
            returning id`,
            [autorizacaoId, body.data.colaboradorId],
          )
          if (consumed.rowCount !== 1) {
            throw new AppError('A liberação prévia expirou ou já foi utilizada.', 409, { periodo })
          }
        }
        const inicioEm = inserted.rows[0]!.inicio_em
        const horario = await client.query<{ inicio_local: string; retorno_local: string }>(
          `select to_char($1::timestamptz at time zone $3,'HH24:MI') as inicio_local,
                  to_char(($1::timestamptz + ($2 * interval '1 second')) at time zone $3,'HH24:MI') as retorno_local`,
          [inicioEm, limiteSegundos, config.appTimezone],
        )
        await client.query('update verificacoes_faciais set usado_em=now() where id=$1', [verification.rows[0].id])

        const result: StartResponse = {
          id: pauseId,
          periodo,
          limiteSegundos,
          foraHorario,
          inicioEm,
          inicioLocal: horario.rows[0]!.inicio_local,
          retornoAteLocal: horario.rows[0]!.retorno_local,
        }
        if (!operation) return result
        return (await savePontoOperation(client, operation, pauseId, result)).response
      } catch (error: unknown) {
        if (
          typeof error === 'object' &&
          error !== null &&
          'code' in error &&
          (error as { code?: unknown }).code === '23505'
        ) {
          throw new AppError(
            'Este colaborador já registrou esta pausa hoje ou possui uma pausa aberta.',
            409,
            { periodo },
          )
        }
        throw error
      }
    })
    return c.json(pausa, 201)
  } catch (error) {
    if (error instanceof PontoOperationConflictError) {
      return c.json({ erro: error.message, codigo: 'PONTO_OPERATION_ID_CONFLICT' }, 409)
    }
    if (error instanceof AppError) {
      if (error.status === 409) {
        await auditRepeatedAttempt({
          colaboradorId: body.data.colaboradorId,
          device,
          pauseId: error.details?.pauseId,
          periodo: error.details?.periodo,
        })
      }
      return c.json({ erro: error.message }, error.status)
    }
    throw error
  }
})

idempotentPontoMutationRoutes.post('/pausas/finalizar', async (c) => {
  const body = await parseJson(c, z.object({
    operacaoId: uuidSchema.optional(),
    colaboradorId: uuidSchema,
    verificacaoToken: z.string().min(20),
  }))
  if (!body.ok) return body.response

  const device = c.get('device')
  const operation = identity(body.data.operacaoId, device, body.data.colaboradorId, 'FINALIZAR')

  try {
    const pausa = await transaction(async (client): Promise<FinishResponse> => {
      if (operation) {
        await lockPontoOperation(client, operation.operationId, device.id)
        const replay = await compatibleMutationReplay<FinishResponse>(client, operation, 'RETORNO')
        if (replay) return replay
      }

      const verification = await client.query<{ id: string }>(
        `select id from verificacoes_faciais
          where token_hash=$1 and colaborador_id=$2 and dispositivo_id=$3
            and usado_em is null and expira_em>now()
          for update`,
        [hashToken(body.data.verificacaoToken), body.data.colaboradorId, device.id],
      )
      if (!verification.rows[0]) throw new AppError('Verificação facial inválida ou expirada.', 401)

      const open = await client.query<{ id: string; inicio_em: string; limite_segundos: number }>(
        `select id,inicio_em::text,limite_segundos from pausas_cafe
          where colaborador_id=$1 and fim_em is null
          order by inicio_em desc limit 1 for update`,
        [body.data.colaboradorId],
      )
      if (!open.rows[0]) throw new AppError('Nenhuma pausa aberta para este colaborador.', 404)

      const finished = await client.query<{ fim_em: string; duracao_segundos: number }>(
        `update pausas_cafe
            set fim_em=now(),dispositivo_fim_id=$2,verificacao_fim_id=$3
          where id=$1
          returning fim_em::text,
                    floor(extract(epoch from (fim_em-inicio_em)))::int as duracao_segundos`,
        [open.rows[0].id, device.id, verification.rows[0].id],
      )
      const row = finished.rows[0]!
      const horario = await client.query<{ inicio_local: string; fim_local: string }>(
        `select to_char($1::timestamptz at time zone $3,'HH24:MI') as inicio_local,
                to_char($2::timestamptz at time zone $3,'HH24:MI') as fim_local`,
        [open.rows[0].inicio_em, row.fim_em, config.appTimezone],
      )
      await client.query('update verificacoes_faciais set usado_em=now() where id=$1', [verification.rows[0].id])

      const result: FinishResponse = {
        id: open.rows[0].id,
        inicioLocal: horario.rows[0]!.inicio_local,
        fimEm: row.fim_em,
        fimLocal: horario.rows[0]!.fim_local,
        duracaoSegundos: row.duracao_segundos,
        limiteSegundos: open.rows[0].limite_segundos,
        excedeuLimite: row.duracao_segundos > open.rows[0].limite_segundos,
      }
      if (!operation) return result
      return (await savePontoOperation(client, operation, open.rows[0].id, result)).response
    })
    return c.json(pausa)
  } catch (error) {
    if (error instanceof PontoOperationConflictError) {
      return c.json({ erro: error.message, codigo: 'PONTO_OPERATION_ID_CONFLICT' }, 409)
    }
    if (error instanceof AppError) return c.json({ erro: error.message }, error.status)
    throw error
  }
})
