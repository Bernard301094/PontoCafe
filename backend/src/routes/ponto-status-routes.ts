import { Hono } from 'hono'
import { createMiddleware } from 'hono/factory'
import type { AppEnv, Device } from '../auth-runtime.js'
import { config } from '../config.js'
import { query } from '../db.js'
import { hashToken } from '../security.js'

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

export const pontoStatusRoutes = new Hono<AppEnv>()
pontoStatusRoutes.use('*', requireDevice)

pontoStatusRoutes.get('/horario', async (c) => {
  const activeRule = await query<{
    periodo: 'MANHA' | 'TARDE'
    inicio: string
    fim: string
    limite_segundos: number
  }>(
    `select periodo,inicio::text,fim::text,limite_segundos
     from regras_cafe
     where ativo=true
       and (now() at time zone $1)::time>=inicio
       and (now() at time zone $1)::time<fim
     order by inicio limit 1`,
    [config.appTimezone],
  )

  const rules = await query<{
    periodo: 'MANHA' | 'TARDE'
    inicio: string
    fim: string
    limite_segundos: number
  }>(
    `select periodo,inicio::text,fim::text,limite_segundos
     from regras_cafe where ativo=true order by inicio`,
  )

  const nowResult = await query<{ agora_local: string }>(
    `select to_char(now() at time zone $1,'YYYY-MM-DD HH24:MI:SS') as agora_local`,
    [config.appTimezone],
  )

  return c.json({
    dentroHorario: Boolean(activeRule.rows[0]),
    periodoAtual: activeRule.rows[0]?.periodo ?? null,
    limiteSegundos: activeRule.rows[0]?.limite_segundos ?? null,
    agoraLocal: nowResult.rows[0]?.agora_local,
    regras: rules.rows.map((rule) => ({
      periodo: rule.periodo,
      inicio: rule.inicio,
      fim: rule.fim,
      limiteSegundos: rule.limite_segundos,
    })),
  })
})
