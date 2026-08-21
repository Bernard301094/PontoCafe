import { Hono } from 'hono'
import { createMiddleware } from 'hono/factory'
import { z } from 'zod'
import type { AppEnv, Device } from '../auth-runtime.js'
import { config } from '../config.js'
import { query, transaction } from '../db.js'
import {
  findPontoOperation,
  lockPontoOperation,
  PontoOperationConflictError,
  savePontoOperation,
  type PontoOperationIdentity,
} from '../ponto-operation-idempotency.js'
import { cosineSimilarity, decryptEmbedding, hashToken, newId, newToken } from '../security.js'
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
  await next()
})

type TemplateRow = {
  id: string
  colaborador_id: string
  matricula: string | null
  nome: string
  setor: string | null
  turno: string | null
  template_cifrado: Buffer
  iv: Buffer
  auth_tag: Buffer
  dimensao: number
}

type Candidate = TemplateRow & { score: number }

type StartPayload = {
  id: string
  periodo: 'MANHA' | 'TARDE'
  limiteSegundos: number
  foraHorario: boolean
  inicioEm: string
  inicioLocal: string
  retornoAteLocal: string
}

type FinishPayload = {
  id: string
  inicioLocal: string
  fimEm: string
  fimLocal: string
  duracaoSegundos: number
  limiteSegundos: number
  excedeuLimite: boolean
}

type FastCollaborator = {
  id: string
  matricula: string | null
  nome: string
  setor: string | null
  turno: string | null
}

type FastSuccessResponse =
  | {
      status: 'INICIO'
      score: number
      colaborador: FastCollaborator
      inicio: StartPayload
    }
  | {
      status: 'RETORNO'
      score: number
      colaborador: FastCollaborator
      retorno: FinishPayload
    }

function fastThreshold(): number {
  return Math.min(0.98, config.faceThreshold + 0.03)
}

function fastMargin(): number {
  return Math.max(config.faceIdentificationMargin + 0.02, config.faceIdentificationMargin * 1.5)
}

function operationIdentity(
  operationId: string,
  deviceId: string,
  collaboratorId: string,
): PontoOperationIdentity {
  return {
    operationId,
    deviceId,
    collaboratorId,
    type: 'REGISTRO_RAPIDO',
  }
}

export const fastPontoRoutes = new Hono<AppEnv>()
fastPontoRoutes.use('*', requireDevice)

/**
 * Caminho rápido para filas no Ponto.
 *
 * Segurança preservada/reforçada:
 * - o cliente nunca informa o score confiável: o servidor recalcula tudo;
 * - todos os colaboradores compatíveis competem entre si no servidor;
 * - o fast-path exige limiar e margem MAIS estritos que o fluxo normal;
 * - casos não inequívocos ou repetidos seguem para a confirmação biométrica
 *   autoritativa, sem confirmação manual;
 * - exceções fora do horário usam somente liberação prévia do Supervisor;
 * - início/retorno e consumo da verificação acontecem na mesma transação;
 * - quando operacaoId é enviado, resultado + mutação são COMMITados juntos e
 *   retries recebem exatamente o mesmo resultado em vez de alternar início/retorno.
 */
fastPontoRoutes.post('/registro-rapido', async (c) => {
  const body = await parseJson(c, z.object({
    operacaoId: uuidSchema.optional(),
    colaboradorId: uuidSchema,
    embedding: embeddingSchema,
    modelo: z.string().trim().min(2).max(100),
    versaoModelo: z.string().trim().min(1).max(50),
  }))
  if (!body.ok) return body.response

  const device = c.get('device')
  const idempotencyIdentity = body.data.operacaoId
    ? operationIdentity(body.data.operacaoId, device.id, body.data.colaboradorId)
    : null

  // Replay curto antes do trabalho biométrico caro. A segunda verificação dentro
  // da transação de mutação continua obrigatória para fechar a corrida entre
  // duas requisições simultâneas com o mesmo UUID.
  if (idempotencyIdentity) {
    try {
      const replay = await transaction(async (client) => {
        await lockPontoOperation(client, idempotencyIdentity.operationId, device.id)
        return findPontoOperation<FastSuccessResponse>(client, idempotencyIdentity)
      })
      if (replay) return c.json(replay.response)
    } catch (error) {
      if (error instanceof PontoOperationConflictError) {
        return c.json({ erro: error.message, codigo: 'PONTO_OPERATION_ID_CONFLICT' }, 409)
      }
      throw error
    }
  }

  const templates = await query<TemplateRow>(
    `select t.id,t.colaborador_id,col.matricula,col.nome,col.setor,col.turno,
            t.template_cifrado,t.iv,t.auth_tag,t.dimensao
       from templates_faciais t
       join colaboradores col on col.id=t.colaborador_id
      where col.ativo=true and t.modelo=$1 and t.versao_modelo=$2`,
    [body.data.modelo, body.data.versaoModelo],
  )

  const bestByCollaborator = new Map<string, Candidate>()
  for (const template of templates.rows) {
    if (template.dimensao !== body.data.embedding.length) continue
    try {
      const stored = decryptEmbedding(template.template_cifrado, template.iv, template.auth_tag)
      if (stored.length !== template.dimensao) continue
      const candidate = { ...template, score: cosineSimilarity(stored, body.data.embedding) }
      const current = bestByCollaborator.get(template.colaborador_id)
      if (!current || candidate.score > current.score) {
        bestByCollaborator.set(template.colaborador_id, candidate)
      }
    } catch (error) {
      console.error(JSON.stringify({
        evento: 'template_ignorado_registro_rapido',
        colaboradorId: template.colaborador_id,
        templateId: template.id,
        erro: error instanceof Error ? error.message : 'erro desconhecido',
      }))
    }
  }

  const candidates = [...bestByCollaborator.values()].sort((a, b) => b.score - a.score)
  const best = candidates[0]
  const second = candidates[1]

  if (!best || best.colaborador_id !== body.data.colaboradorId) {
    return c.json({
      status: 'INTERACAO_NECESSARIA',
      motivo: 'IDENTIDADE_NAO_INEQUIVOCA',
      mensagem: 'A validação reforçada não foi conclusiva. Continuando pela verificação biométrica segura.',
    })
  }

  const scoreGap = second ? best.score - second.score : 1
  if (best.score < fastThreshold() || scoreGap < fastMargin()) {
    return c.json({
      status: 'INTERACAO_NECESSARIA',
      motivo: 'CONFIANCA_REFORCADA_NAO_ATINGIDA',
      mensagem: 'A validação reforçada não foi conclusiva. Continuando pela verificação biométrica segura.',
      score: Number(best.score.toFixed(4)),
    })
  }

  const collaborator: FastCollaborator = {
    id: best.colaborador_id,
    matricula: best.matricula,
    nome: best.nome,
    setor: best.setor,
    turno: best.turno,
  }

  try {
    const result = await transaction(async (client) => {
      if (idempotencyIdentity) {
        await lockPontoOperation(client, idempotencyIdentity.operationId, device.id)
        const replay = await findPontoOperation<FastSuccessResponse>(client, idempotencyIdentity)
        if (replay) return replay.response
      }

      const lockedCollaborator = await client.query<{ id: string }>(
        'select id from colaboradores where id=$1 and ativo=true for update',
        [best.colaborador_id],
      )
      if (!lockedCollaborator.rows[0]) {
        return {
          status: 'INTERACAO_NECESSARIA' as const,
          motivo: 'COLABORADOR_INDISPONIVEL',
          mensagem: 'Não foi possível concluir automaticamente. Tente novamente.',
          score: Number(best.score.toFixed(4)),
          colaborador: collaborator,
        }
      }

      const open = await client.query<{ id: string; inicio_em: string; limite_segundos: number }>(
        `select id,inicio_em::text,limite_segundos
           from pausas_cafe
          where colaborador_id=$1 and fim_em is null
          order by inicio_em desc limit 1 for update`,
        [best.colaborador_id],
      )

      if (open.rows[0]) {
        const current = open.rows[0]
        const verificationId = newId()
        const verificationToken = newToken()
        await client.query(
          `insert into verificacoes_faciais
             (id,colaborador_id,dispositivo_id,token_hash,score,expira_em,usado_em)
           values ($1,$2,$3,$4,$5,now()+($6*interval '1 second'),now())`,
          [
            verificationId,
            best.colaborador_id,
            device.id,
            hashToken(verificationToken),
            best.score,
            config.verificationTtlSeconds,
          ],
        )

        const finished = await client.query<{ fim_em: string; duracao_segundos: number }>(
          `update pausas_cafe
              set fim_em=now(),dispositivo_fim_id=$2,verificacao_fim_id=$3
            where id=$1
          returning fim_em::text,
                    floor(extract(epoch from (fim_em-inicio_em)))::int as duracao_segundos`,
          [current.id, device.id, verificationId],
        )
        const row = finished.rows[0]!
        const times = await client.query<{ inicio_local: string; fim_local: string }>(
          `select to_char($1::timestamptz at time zone $3,'HH24:MI') as inicio_local,
                  to_char($2::timestamptz at time zone $3,'HH24:MI') as fim_local`,
          [current.inicio_em, row.fim_em, config.appTimezone],
        )
        const timeRow = times.rows[0]!
        const retorno: FinishPayload = {
          id: current.id,
          inicioLocal: timeRow.inicio_local,
          fimEm: row.fim_em,
          fimLocal: timeRow.fim_local,
          duracaoSegundos: row.duracao_segundos,
          limiteSegundos: current.limite_segundos,
          excedeuLimite: row.duracao_segundos > current.limite_segundos,
        }
        const success: FastSuccessResponse = {
          status: 'RETORNO',
          score: Number(best.score.toFixed(4)),
          colaborador: collaborator,
          retorno,
        }
        if (idempotencyIdentity) {
          const stored = await savePontoOperation(client, idempotencyIdentity, current.id, success)
          return stored.response
        }
        return success
      }

      const usedToday = await client.query<{ periodo: 'MANHA' | 'TARDE' }>(
        `select distinct periodo
           from pausas_cafe
          where colaborador_id=$1
            and fim_em is not null
            and (inicio_em at time zone $2)::date=(now() at time zone $2)::date
            and periodo in ('MANHA','TARDE')`,
        [best.colaborador_id, config.appTimezone],
      )
      const usedPeriods = new Set(usedToday.rows.map((row) => row.periodo))
      if (usedPeriods.has('MANHA') && usedPeriods.has('TARDE')) {
        return {
          status: 'INTERACAO_NECESSARIA' as const,
          motivo: 'PAUSAS_DO_DIA_JA_UTILIZADAS',
          mensagem: 'Pausas de hoje já utilizadas (2/2). Não há mais pausa disponível para hoje.',
          score: Number(best.score.toFixed(4)),
          colaborador: collaborator,
        }
      }

      const activeRule = await client.query<{ periodo: 'MANHA' | 'TARDE'; limite_segundos: number }>(
        `select periodo,limite_segundos
           from regras_cafe
          where ativo=true
            and (now() at time zone $1)::time>=inicio
            and (now() at time zone $1)::time<fim
          order by inicio limit 1`,
        [config.appTimezone],
      )

      let periodo: 'MANHA' | 'TARDE'
      let limiteSegundos: number
      let foraHorario = false
      let authorizationId: string | null = null

      if (activeRule.rows[0]) {
        periodo = activeRule.rows[0].periodo
        limiteSegundos = activeRule.rows[0].limite_segundos
      } else {
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
          [best.colaborador_id],
        )
        const release = authorization.rows[0]
        if (!release) {
          return {
            status: 'INTERACAO_NECESSARIA' as const,
            motivo: 'FORA_HORARIO',
            mensagem: 'Fora do horário permitido. Verificando o estado da pausa.',
            score: Number(best.score.toFixed(4)),
            colaborador: collaborator,
          }
        }
        periodo = release.periodo
        limiteSegundos = release.limite_segundos
        foraHorario = true
        authorizationId = release.id
      }

      const alreadyUsed = await client.query<{ id: string }>(
        `select id from pausas_cafe
          where colaborador_id=$1 and periodo=$2
            and (inicio_em at time zone $3)::date=(now() at time zone $3)::date
          order by inicio_em desc limit 1 for update`,
        [best.colaborador_id, periodo, config.appTimezone],
      )
      if (alreadyUsed.rows[0]) {
        return {
          status: 'INTERACAO_NECESSARIA' as const,
          motivo: 'PAUSA_PERIODO_JA_UTILIZADA',
          mensagem: 'Esta pausa já foi utilizada hoje. Verificando o registro existente.',
          score: Number(best.score.toFixed(4)),
          colaborador: collaborator,
        }
      }

      const verificationId = newId()
      const verificationToken = newToken()
      await client.query(
        `insert into verificacoes_faciais
           (id,colaborador_id,dispositivo_id,token_hash,score,expira_em,usado_em)
         values ($1,$2,$3,$4,$5,now()+($6*interval '1 second'),now())`,
        [
          verificationId,
          best.colaborador_id,
          device.id,
          hashToken(verificationToken),
          best.score,
          config.verificationTtlSeconds,
        ],
      )

      const pauseId = newId()
      const inserted = await client.query<{ inicio_em: string }>(
        `insert into pausas_cafe
           (id,colaborador_id,periodo,limite_segundos,fora_horario,autorizacao_id,dispositivo_inicio_id,verificacao_inicio_id)
         values ($1,$2,$3,$4,$5,$6,$7,$8)
         returning inicio_em::text`,
        [
          pauseId,
          best.colaborador_id,
          periodo,
          limiteSegundos,
          foraHorario,
          authorizationId,
          device.id,
          verificationId,
        ],
      )
      if (authorizationId) {
        const consumed = await client.query(
          `update autorizacoes
              set usado_em=now()
            where id=$1 and colaborador_id=$2
              and usado_em is null and cancelada_em is null
          returning id`,
          [authorizationId, best.colaborador_id],
        )
        if (consumed.rowCount !== 1) {
          throw new Error('A liberação prévia expirou ou já foi utilizada.')
        }
      }
      const inicioEm = inserted.rows[0]!.inicio_em
      const times = await client.query<{ inicio_local: string; retorno_local: string }>(
        `select to_char($1::timestamptz at time zone $3,'HH24:MI') as inicio_local,
                to_char(($1::timestamptz + ($2 * interval '1 second')) at time zone $3,'HH24:MI') as retorno_local`,
        [inicioEm, limiteSegundos, config.appTimezone],
      )
      const timeRow = times.rows[0]!
      const inicio: StartPayload = {
        id: pauseId,
        periodo,
        limiteSegundos,
        foraHorario,
        inicioEm,
        inicioLocal: timeRow.inicio_local,
        retornoAteLocal: timeRow.retorno_local,
      }
      const success: FastSuccessResponse = {
        status: 'INICIO',
        score: Number(best.score.toFixed(4)),
        colaborador: collaborator,
        inicio,
      }
      if (idempotencyIdentity) {
        const stored = await savePontoOperation(client, idempotencyIdentity, pauseId, success)
        return stored.response
      }
      return success
    })

    return c.json(result)
  } catch (error) {
    if (error instanceof PontoOperationConflictError) {
      return c.json({ erro: error.message, codigo: 'PONTO_OPERATION_ID_CONFLICT' }, 409)
    }
    throw error
  }
})
