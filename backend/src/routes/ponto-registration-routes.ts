import { Hono } from 'hono'
import { z } from 'zod'
import type { AppEnv } from '../auth-runtime.js'
import { config } from '../config.js'
import { query, transaction } from '../db.js'
import { ACCESS_CODE_LENGTH } from '../domain/access-code.js'
import {
  AccessCodeError,
  applyAccessCode,
  recentFailedAttempts,
  recordFailedAttempt,
  type CollaboratorSummary,
  type FinishResponse,
  type RegistrationOutcome,
  type StartResponse,
} from '../ponto-registration.js'
import {
  findPontoOperationById,
  lockPontoOperation,
  PontoOperationConflictError,
  savePontoOperation,
  type PontoOperationIdentity,
} from '../ponto-operation-idempotency.js'
import { deviceTokenMiddleware, parseJson, uuidSchema } from './shared.js'

type StoredRegistration = {
  status: 'INICIO' | 'RETORNO'
  colaborador: CollaboratorSummary
  inicio?: StartResponse
  retorno?: FinishResponse
}

/**
 * O quiosque tem uma única mutação. Ele nunca decide se está a registar uma
 * saída ou um retorno: envia o par (pessoa, código) e o servidor responde qual
 * das duas aconteceu, porque só o servidor sabe se aquele código já foi usado
 * para sair. Isso elimina a classe inteira de erros em que o aparelho, offline
 * ou desatualizado, tenta fechar uma pausa que nunca abriu.
 */
export const pontoRegistrationRoutes = new Hono<AppEnv>()
pontoRegistrationRoutes.use('*', deviceTokenMiddleware)

const accessCodeSchema = z.string().trim().min(ACCESS_CODE_LENGTH).max(24)

function toStored(outcome: RegistrationOutcome): StoredRegistration {
  return outcome.status === 'INICIO'
    ? { status: 'INICIO', colaborador: outcome.colaborador, inicio: outcome.inicio }
    : { status: 'RETORNO', colaborador: outcome.colaborador, retorno: outcome.retorno }
}

pontoRegistrationRoutes.post('/pausas/registrar', async (c) => {
  const body = await parseJson(c, z.object({
    operacaoId: uuidSchema.optional(),
    colaboradorId: uuidSchema,
    codigo: accessCodeSchema,
  }))
  if (!body.ok) return body.response

  const device = c.get('device')

  const attempts = await recentFailedAttempts(body.data.colaboradorId)
  if (attempts >= config.accessCodeMaxAttempts) {
    return c.json({
      erro: 'Muitas tentativas com código errado. Aguarde alguns minutos ou peça ajuda ao Supervisor.',
      codigo: 'CODIGO_BLOQUEADO_TEMPORARIAMENTE',
      tentativas: attempts,
      janelaSegundos: config.accessCodeAttemptWindowSeconds,
    }, 429)
  }

  const operation: PontoOperationIdentity | null = body.data.operacaoId
    ? {
        operationId: body.data.operacaoId,
        deviceId: device.id,
        collaboratorId: body.data.colaboradorId,
        type: 'REGISTRO',
      }
    : null

  try {
    const stored = await transaction(async (client): Promise<StoredRegistration> => {
      if (operation) {
        await lockPontoOperation(client, operation.operationId, device.id)
        const replay = await findPontoOperationById<StoredRegistration>(client, operation.operationId)
        if (replay) {
          if (replay.deviceId !== operation.deviceId || replay.collaboratorId !== operation.collaboratorId) {
            throw new PontoOperationConflictError()
          }
          return replay.response
        }
      }

      const outcome = await applyAccessCode(client, {
        deviceId: device.id,
        deviceName: device.nome,
        collaboratorId: body.data.colaboradorId,
        code: body.data.codigo,
        origem: 'QUIOSQUE',
      })
      const result = toStored(outcome)
      if (!operation) return result
      return (await savePontoOperation(client, operation, outcome.pauseId, result)).response
    })

    return c.json(stored, stored.status === 'INICIO' ? 201 : 200)
  } catch (error) {
    if (error instanceof PontoOperationConflictError) {
      return c.json({ erro: error.message, codigo: 'PONTO_OPERATION_ID_CONFLICT' }, 409)
    }
    if (error instanceof AccessCodeError) {
      if (error.codigo === 'CODIGO_INVALIDO' || error.codigo === 'CODIGO_EXPIRADO') {
        // Fora da transação de propósito: o ROLLBACK que rejeitou o código
        // levaria o registo da tentativa consigo, e o contador de força bruta
        // ficaria sempre em zero.
        await recordFailedAttempt({
          collaboratorId: body.data.colaboradorId,
          deviceId: device.id,
          deviceName: device.nome,
          motivo: error.codigo,
        })
      }
      return c.json({ erro: error.message, codigo: error.codigo }, error.status)
    }
    throw error
  }
})

pontoRegistrationRoutes.post('/operacoes/reconciliar', async (c) => {
  const body = await parseJson(c, z.object({
    operacaoId: uuidSchema,
    colaboradorId: uuidSchema,
  }))
  if (!body.ok) return body.response

  const device = c.get('device')
  const stored = await transaction(async (client) => {
    await lockPontoOperation(client, body.data.operacaoId, device.id)
    return findPontoOperationById<StoredRegistration>(client, body.data.operacaoId)
  })

  if (!stored) return c.json({ encontrada: false })
  if (stored.deviceId !== device.id || stored.collaboratorId !== body.data.colaboradorId) {
    return c.json({
      erro: 'O identificador desta operação pertence a outro registro do Ponto.',
      codigo: 'PONTO_OPERATION_ID_CONFLICT',
    }, 409)
  }

  const collaborator = (await query<CollaboratorSummary>(
    'select id,matricula,nome,setor,turno from colaboradores where id=$1 limit 1',
    [body.data.colaboradorId],
  )).rows[0]
  if (!collaborator) return c.json({ erro: 'Colaborador não encontrado para reconciliação.' }, 404)

  const response = stored.response
  if (response?.status === 'INICIO' && response.inicio) {
    return c.json({ encontrada: true, status: 'INICIO', colaborador: collaborator, inicio: response.inicio })
  }
  if (response?.status === 'RETORNO' && response.retorno) {
    return c.json({ encontrada: true, status: 'RETORNO', colaborador: collaborator, retorno: response.retorno })
  }

  return c.json({
    erro: 'O resultado persistido desta operação não pode ser reconciliado.',
    codigo: 'PONTO_OPERATION_REPLAY_INVALID',
  }, 409)
})
