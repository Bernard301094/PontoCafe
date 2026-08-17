import { Hono } from 'hono'
import { createMiddleware } from 'hono/factory'
import { z } from 'zod'
import type { AppEnv, Device } from '../auth-runtime.js'
import { config } from '../config.js'
import { query, transaction } from '../db.js'
import { cosineSimilarity, decryptEmbedding, hashToken, newId } from '../security.js'
import { embeddingSchema, parseJson, uuidSchema } from './shared.js'

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
  await query('update dispositivos set atualizado_em=now() where id=$1', [device.id])
  await next()
})

const offlineEventSchema = z.object({
  eventId: uuidSchema,
  acao: z.enum(['INICIAR', 'FINALIZAR']),
  colaboradorId: uuidSchema,
  ocorridoEm: z.string().datetime({ offset: true }),
  score: z.number().finite().min(0).max(1),
  embedding: embeddingSchema,
  appVersion: z.string().trim().min(1).max(40),
  modelo: z.string().trim().min(2).max(100),
  versaoModelo: z.string().trim().min(1).max(50),
})

type OfflineEvent = z.infer<typeof offlineEventSchema>
type SyncStatus = 'SINCRONIZADO' | 'RECONCILIADO' | 'ERRO'

class OfflineSyncError extends Error {}

async function auditOffline(
  client: import('pg').PoolClient,
  device: Device,
  event: OfflineEvent,
  entityId: string | null,
  reconciled: boolean,
  verifiedScore: number,
) {
  await client.query(
    `insert into auditoria (ator_tipo,acao,entidade,entidade_id,detalhes)
     values ('DISPOSITIVO','SINCRONIZAR_PONTO_OFFLINE','PAUSA',$1,$2::jsonb)`,
    [entityId, JSON.stringify({
      dispositivoId: device.id,
      dispositivoNome: device.nome,
      offlineEventId: event.eventId,
      acaoOffline: event.acao,
      ocorridoEm: event.ocorridoEm,
      scoreLocal: event.score,
      scoreRevalidado: Number(verifiedScore.toFixed(4)),
      appVersion: event.appVersion,
      modelo: event.modelo,
      versaoModelo: event.versaoModelo,
      reconciliado: reconciled,
    })],
  )
}

async function auditRepeatedAttempt(
  client: import('pg').PoolClient,
  device: Device,
  event: OfflineEvent,
  pauseId: string,
  periodo: 'MANHA' | 'TARDE',
  verifiedScore: number,
) {
  await client.query(
    `insert into auditoria (ator_tipo,acao,entidade,entidade_id,detalhes)
     values ('DISPOSITIVO','TENTATIVA_PONTO_REPETIDA','PAUSA',$1,$2::jsonb)`,
    [pauseId, JSON.stringify({
      colaboradorId: event.colaboradorId,
      dispositivoId: device.id,
      dispositivoNome: device.nome,
      periodo,
      tentativaEm: event.ocorridoEm,
      origem: 'OFFLINE',
      motivo: 'PAUSA_PERIODO_JA_UTILIZADA',
      offlineEventId: event.eventId,
      scoreLocal: event.score,
      scoreRevalidado: Number(verifiedScore.toFixed(4)),
      appVersion: event.appVersion,
      modelo: event.modelo,
      versaoModelo: event.versaoModelo,
    })],
  )
}

async function processOfflineEvent(device: Device, event: OfflineEvent): Promise<{ status: SyncStatus; pausaId?: string; mensagem?: string }> {
  const occurredMillis = Date.parse(event.ocorridoEm)
  const maxAgeMillis = config.offlineMaxEventAgeHours * 60 * 60 * 1000
  if (!Number.isFinite(occurredMillis)) throw new OfflineSyncError('Horário do evento offline inválido.')
  if (occurredMillis > Date.now() + 5 * 60 * 1000) throw new OfflineSyncError('O evento offline está no futuro.')
  if (Date.now() - occurredMillis > maxAgeMillis) {
    throw new OfflineSyncError(`O evento offline ultrapassou a janela máxima de ${config.offlineMaxEventAgeHours} horas.`)
  }

  return transaction(async (client) => {
    const markerHash = hashToken(`offline:${event.eventId}`)
    const alreadyProcessed = await client.query<{ id: string }>(
      'select id from verificacoes_faciais where token_hash=$1 limit 1',
      [markerHash],
    )
    if (alreadyProcessed.rows[0]) return { status: 'RECONCILIADO' as const }

    const biometric = await client.query<{
      colaborador_id: string
      template_cifrado: Buffer
      iv: Buffer
      auth_tag: Buffer
      dimensao: number
    }>(
      `select t.colaborador_id,t.template_cifrado,t.iv,t.auth_tag,t.dimensao
         from templates_faciais t
         join colaboradores c on c.id=t.colaborador_id
        where t.colaborador_id=$1
          and c.ativo=true
          and t.modelo=$2
          and t.versao_modelo=$3
        limit 1`,
      [event.colaboradorId, event.modelo, event.versaoModelo],
    )
    const stored = biometric.rows[0]
    if (!stored) throw new OfflineSyncError('Biometria inexistente, inativa ou incompatível com o modelo usado offline.')
    if (stored.dimensao !== event.embedding.length) throw new OfflineSyncError('Dimensão biométrica incompatível.')

    const template = decryptEmbedding(stored.template_cifrado, stored.iv, stored.auth_tag)
    const verifiedScore = cosineSimilarity(template, event.embedding)
    if (verifiedScore < config.faceThreshold) {
      throw new OfflineSyncError('A biometria do registro offline não foi confirmada pelo servidor.')
    }

    const verificationId = newId()
    await client.query(
      `insert into verificacoes_faciais
        (id,colaborador_id,dispositivo_id,token_hash,score,expira_em,usado_em,criado_em)
       values ($1,$2,$3,$4,$5,$6::timestamptz,$6::timestamptz,$6::timestamptz)`,
      [verificationId, event.colaboradorId, device.id, markerHash, verifiedScore, event.ocorridoEm],
    )

    if (event.acao === 'INICIAR') {
      const activeRule = await client.query<{ periodo: 'MANHA' | 'TARDE'; limite_segundos: number }>(
        `select periodo,limite_segundos
           from regras_cafe
          where ativo=true
            and ($1::timestamptz at time zone $2)::time>=inicio
            and ($1::timestamptz at time zone $2)::time<fim
          order by inicio limit 1`,
        [event.ocorridoEm, config.appTimezone],
      )
      const rule = activeRule.rows[0]
      if (!rule) throw new OfflineSyncError('Pausas iniciadas fora do horário não podem ser validadas offline.')

      const open = await client.query<{ id: string }>(
        `select id from pausas_cafe
          where colaborador_id=$1 and fim_em is null
          order by inicio_em desc limit 1`,
        [event.colaboradorId],
      )
      if (open.rows[0]) {
        await auditOffline(client, device, event, open.rows[0].id, true, verifiedScore)
        return { status: 'RECONCILIADO' as const, pausaId: open.rows[0].id }
      }

      const samePeriod = await client.query<{ id: string; periodo: 'MANHA' | 'TARDE' }>(
        `select id,periodo from pausas_cafe
          where colaborador_id=$1 and periodo=$2
            and (inicio_em at time zone $4)::date=($3::timestamptz at time zone $4)::date
          order by inicio_em desc limit 1`,
        [event.colaboradorId, rule.periodo, event.ocorridoEm, config.appTimezone],
      )
      if (samePeriod.rows[0]) {
        await auditRepeatedAttempt(client, device, event, samePeriod.rows[0].id, samePeriod.rows[0].periodo, verifiedScore)
        return {
          status: 'RECONCILIADO' as const,
          pausaId: samePeriod.rows[0].id,
          mensagem: 'Tentativa repetida registrada. A pausa deste período já havia sido utilizada.',
        }
      }

      const pauseId = newId()
      await client.query(
        `insert into pausas_cafe
          (id,colaborador_id,periodo,inicio_em,limite_segundos,fora_horario,dispositivo_inicio_id,verificacao_inicio_id)
         values ($1,$2,$3,$4::timestamptz,$5,false,$6,$7)`,
        [pauseId, event.colaboradorId, rule.periodo, event.ocorridoEm, rule.limite_segundos, device.id, verificationId],
      )
      await auditOffline(client, device, event, pauseId, false, verifiedScore)
      return { status: 'SINCRONIZADO' as const, pausaId: pauseId }
    }

    const open = await client.query<{ id: string; inicio_em: string }>(
      `select id,inicio_em::text from pausas_cafe
        where colaborador_id=$1 and fim_em is null
        order by inicio_em desc limit 1 for update`,
      [event.colaboradorId],
    )
    const pause = open.rows[0]
    if (!pause) {
      const closed = await client.query<{ id: string }>(
        `select id from pausas_cafe
          where colaborador_id=$1 and fim_em is not null
            and (inicio_em at time zone $3)::date=($2::timestamptz at time zone $3)::date
          order by fim_em desc limit 1`,
        [event.colaboradorId, event.ocorridoEm, config.appTimezone],
      )
      if (!closed.rows[0]) throw new OfflineSyncError('Não existe pausa aberta para reconciliar este retorno.')
      await auditOffline(client, device, event, closed.rows[0].id, true, verifiedScore)
      return { status: 'RECONCILIADO' as const, pausaId: closed.rows[0].id }
    }

    if (Date.parse(pause.inicio_em) > occurredMillis) {
      throw new OfflineSyncError('O retorno offline é anterior ao início da pausa.')
    }

    await client.query(
      `update pausas_cafe
          set fim_em=$2::timestamptz,
              dispositivo_fim_id=$3,
              verificacao_fim_id=$4
        where id=$1`,
      [pause.id, event.ocorridoEm, device.id, verificationId],
    )
    await auditOffline(client, device, event, pause.id, false, verifiedScore)
    return { status: 'SINCRONIZADO' as const, pausaId: pause.id }
  })
}

export const offlineRoutes = new Hono<AppEnv>()
offlineRoutes.use('*', requireDevice)

offlineRoutes.post('/offline/sincronizar', async (c) => {
  const body = await parseJson(c, z.object({
    eventos: z.array(offlineEventSchema).min(1).max(100),
  }))
  if (!body.ok) return body.response

  const device = c.get('device')
  const resultados: Array<{ eventId: string; status: SyncStatus; pausaId?: string; mensagem?: string }> = []

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
