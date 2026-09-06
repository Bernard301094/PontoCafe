import { Hono } from 'hono'
import { z } from 'zod'
import type { AppEnv, Device } from '../auth-runtime.js'
import { config } from '../config.js'
import { transaction } from '../db.js'
import { ACCESS_CODE_LENGTH } from '../domain/access-code.js'
import { findPontoOperationById, lockPontoOperation } from '../ponto-operation-idempotency.js'
import {
  AccessCodeError,
  applyAccessCode,
  type CollaboratorSummary,
  type FinishResponse,
  type StartResponse,
} from '../ponto-registration.js'
import { deviceTokenMiddleware, parseJson, uuidSchema } from './shared.js'

/**
 * Fila offline do quiosque.
 *
 * Sem rede o aparelho não consegue validar o código — só o servidor sabe quais
 * estão vivos. O que ele faz é aceitar o registo, guardar o código digitado e
 * deixar a validação para o momento da sincronização: é a mesma função que a
 * rota online usa, com o instante em que a pessoa realmente passou pelo
 * quiosque em vez de `now()`.
 *
 * A consequência aceite é que um código errado digitado offline só é recusado
 * mais tarde. O evento fica com estado ERRO e visível na central de sincronismo,
 * em vez de silenciosamente virar uma pausa que ninguém autorizou.
 */
const offlineEventSchema = z.object({
  eventId: uuidSchema,
  colaboradorId: uuidSchema,
  codigo: z.string().trim().min(ACCESS_CODE_LENGTH).max(24),
  ocorridoEm: z.string().datetime({ offset: true }),
  appVersion: z.string().trim().min(1).max(40),
})

type OfflineEvent = z.infer<typeof offlineEventSchema>
type SyncStatus = 'SINCRONIZADO' | 'RECONCILIADO' | 'ERRO'

type StoredRegistration = {
  status: 'INICIO' | 'RETORNO'
  colaborador: CollaboratorSummary
  inicio?: StartResponse
  retorno?: FinishResponse
}

class OfflineSyncError extends Error {}

async function auditOffline(
  client: import('pg').PoolClient,
  device: Device,
  event: OfflineEvent,
  entityId: string | null,
  reconciled: boolean,
  tipo: 'INICIO' | 'RETORNO' | null,
) {
  await client.query(
    `insert into auditoria (ator_tipo,acao,entidade,entidade_id,detalhes)
     values ('DISPOSITIVO','SINCRONIZAR_PONTO_OFFLINE','PAUSA',$1,$2::jsonb)`,
    [entityId, JSON.stringify({
      dispositivoId: device.id,
      dispositivoNome: device.nome,
      offlineEventId: event.eventId,
      tipoResolvido: tipo,
      ocorridoEm: event.ocorridoEm,
      appVersion: event.appVersion,
      reconciliado: reconciled,
    })],
  )
}

async function processOfflineEvent(
  device: Device,
  event: OfflineEvent,
): Promise<{ status: SyncStatus; pausaId?: string; tipo?: 'INICIO' | 'RETORNO'; mensagem?: string }> {
  const occurredMillis = Date.parse(event.ocorridoEm)
  const maxAgeMillis = config.offlineMaxEventAgeHours * 60 * 60 * 1000
  if (!Number.isFinite(occurredMillis)) throw new OfflineSyncError('Horário do evento offline inválido.')
  if (occurredMillis > Date.now() + 5 * 60 * 1000) throw new OfflineSyncError('O evento offline está no futuro.')
  if (Date.now() - occurredMillis > maxAgeMillis) {
    throw new OfflineSyncError(`O evento offline ultrapassou a janela máxima de ${config.offlineMaxEventAgeHours} horas.`)
  }

  return transaction(async (client) => {
    // O mesmo UUID pode já ter alterado o Ponto pela rota online, quando a
    // resposta se perdeu depois do COMMIT. Nesse caso reconcilia-se o resultado
    // original em vez de executar uma segunda ação.
    await lockPontoOperation(client, event.eventId, device.id)
    const committed = await findPontoOperationById<StoredRegistration>(client, event.eventId)
    if (committed) {
      if (committed.deviceId !== device.id || committed.collaboratorId !== event.colaboradorId) {
        throw new OfflineSyncError('O identificador offline pertence a outro dispositivo ou colaborador.')
      }
      await auditOffline(client, device, event, committed.pauseId, true, committed.response?.status ?? null)
      return {
        status: 'RECONCILIADO' as const,
        pausaId: committed.pauseId ?? undefined,
        tipo: committed.response?.status,
        mensagem: 'O servidor já havia confirmado esta mesma operação online.',
      }
    }

    try {
      const outcome = await applyAccessCode(client, {
        deviceId: device.id,
        deviceName: device.nome,
        collaboratorId: event.colaboradorId,
        code: event.codigo,
        occurredAt: event.ocorridoEm,
        origem: 'OFFLINE',
      })
      await auditOffline(client, device, event, outcome.pauseId, false, outcome.status)
      return { status: 'SINCRONIZADO' as const, pausaId: outcome.pauseId, tipo: outcome.status }
    } catch (error) {
      if (error instanceof AccessCodeError) throw new OfflineSyncError(error.message)
      throw error
    }
  })
}

export const offlineRoutes = new Hono<AppEnv>()
offlineRoutes.use('*', deviceTokenMiddleware)

offlineRoutes.post('/offline/sincronizar', async (c) => {
  const body = await parseJson(c, z.object({
    eventos: z.array(offlineEventSchema).min(1).max(100),
  }))
  if (!body.ok) return body.response

  const device = c.get('device')
  const resultados: Array<{
    eventId: string
    status: SyncStatus
    pausaId?: string
    tipo?: 'INICIO' | 'RETORNO'
    mensagem?: string
  }> = []

  for (const event of body.data.eventos) {
    try {
      const result = await processOfflineEvent(device, event)
      resultados.push({ eventId: event.eventId, ...result })
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Falha ao sincronizar evento offline.'
      resultados.push({ eventId: event.eventId, status: 'ERRO', mensagem: message })
    }
  }

  return c.json({
    resultados,
    processados: resultados.filter((item) => item.status !== 'ERRO').map((item) => item.eventId),
    pendentesComErro: resultados.filter((item) => item.status === 'ERRO').map((item) => item.eventId),
  })
})
