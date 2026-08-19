import { Hono } from 'hono'
import { z } from 'zod'
import { requireRole, requireUser, type AppEnv } from '../auth-runtime.js'
import { query } from '../db.js'
import { errorPayload } from '../observability.js'
import { parseJson, periodoSchema } from './shared.js'

export const coffeeRuleRoutes = new Hono<AppEnv>()
coffeeRuleRoutes.use('*', requireUser, requireRole('ADMIN'))

const horaSchema = z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/)
const STANDARD_LIMIT_SECONDS = 15 * 60

coffeeRuleRoutes.get('/regras-cafe', async (c) => {
  const result = await query<{
    periodo: 'MANHA' | 'TARDE'
    inicio: string
    fim: string
    limite_segundos: number
    ativo: boolean
  }>(
    `select periodo,to_char(inicio,'HH24:MI') as inicio,to_char(fim,'HH24:MI') as fim,
            limite_segundos,ativo
       from regras_cafe
      order by inicio`,
  )

  return c.json({
    regras: result.rows.map((rule) => ({
      periodo: rule.periodo,
      inicio: rule.inicio,
      fim: rule.fim,
      limiteSegundos: rule.limite_segundos,
      // Compatibilidade temporária com APKs anteriores à 0.7.0.
      limiteMinutos: Math.floor(rule.limite_segundos / 60),
      ativo: rule.ativo,
      padraoAtual: rule.limite_segundos === STANDARD_LIMIT_SECONDS,
    })),
  })
})

coffeeRuleRoutes.put('/regras-cafe/:periodo', async (c) => {
  const periodo = c.req.param('periodo').toUpperCase()
  if (!periodoSchema.safeParse(periodo).success) {
    return c.json(errorPayload(c, 'Período inválido.', 'COFFEE_PERIOD_INVALID'), 400)
  }

  const body = await parseJson(c, z.object({
    inicio: horaSchema,
    fim: horaSchema,
    limiteSegundos: z.number().int().min(60).max(7200).optional(),
    limiteMinutos: z.number().int().min(1).max(120).optional(),
    ativo: z.boolean().default(true),
  }).refine((value) => value.inicio < value.fim, {
    message: 'O horário final deve ser posterior ao horário inicial.',
    path: ['fim'],
  }).refine((value) => value.limiteSegundos !== undefined || value.limiteMinutos !== undefined, {
    message: 'Informe o tempo permitido.',
    path: ['limiteSegundos'],
  }))
  if (!body.ok) return body.response

  const limitSeconds = body.data.limiteSegundos ?? (body.data.limiteMinutos! * 60)
  const updated = await query<{
    periodo: string
    inicio: string
    fim: string
    limite_segundos: number
    ativo: boolean
  }>(
    `update regras_cafe
        set inicio=$2::time,fim=$3::time,limite_segundos=$4,ativo=$5
      where periodo=$1
      returning periodo,to_char(inicio,'HH24:MI') as inicio,to_char(fim,'HH24:MI') as fim,
                limite_segundos,ativo`,
    [periodo, body.data.inicio, body.data.fim, limitSeconds, body.data.ativo],
  )
  const rule = updated.rows[0]
  if (!rule) return c.json(errorPayload(c, 'Regra de café não encontrada.', 'COFFEE_RULE_NOT_FOUND'), 404)

  const actor = c.get('user')
  await query(
    `insert into auditoria (ator_auth_id,ator_tipo,acao,entidade,entidade_id,detalhes)
     values ($1,'ADMIN','ALTERAR_REGRA_CAFE','REGRA_CAFE',$2,$3::jsonb)`,
    [actor.id, periodo, JSON.stringify({
      inicio: rule.inicio,
      fim: rule.fim,
      limiteSegundos: rule.limite_segundos,
      ativo: rule.ativo,
    })],
  )

  return c.json({
    regra: {
      periodo: rule.periodo,
      inicio: rule.inicio,
      fim: rule.fim,
      limiteSegundos: rule.limite_segundos,
      limiteMinutos: Math.floor(rule.limite_segundos / 60),
      ativo: rule.ativo,
      padraoAtual: rule.limite_segundos === STANDARD_LIMIT_SECONDS,
    },
  })
})
