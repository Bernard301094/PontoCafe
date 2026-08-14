import { Hono } from 'hono'
import { z } from 'zod'
import { requireRole, requireUser, type AppEnv } from '../auth-runtime.js'
import { config } from '../config.js'
import { query } from '../db.js'

export const liveRoutes = new Hono<AppEnv>()
liveRoutes.use('*', requireUser, requireRole('ADMIN', 'SUPERVISOR'))

liveRoutes.get('/pausas/ativas', async (c) => {
  const result = await query(
    `select p.id,
            p.periodo,
            to_char(p.inicio_em at time zone $1,'HH24:MI') as "inicioLocal",
            p.limite_segundos as "limiteSegundos",
            p.fora_horario as "foraHorario",
            greatest(0,floor(extract(epoch from (now()-p.inicio_em)))::int) as "tempoSegundos",
            col.id as "colaboradorId",
            col.nome,
            col.matricula,
            col.setor
     from pausas_cafe p
     join colaboradores col on col.id=p.colaborador_id
     where p.fim_em is null
     order by p.inicio_em`,
    [config.appTimezone],
  )
  return c.json({ pausas: result.rows })
})

liveRoutes.get('/pausas', async (c) => {
  const dataParam = c.req.query('data')
  const parsed = dataParam
    ? z.string().regex(/^\d{4}-\d{2}-\d{2}$/).safeParse(dataParam)
    : null

  if (parsed && !parsed.success) {
    return c.json({ erro: 'Informe a data no formato YYYY-MM-DD.' }, 400)
  }

  const data = parsed?.data ?? null
  const result = await query(
    `select p.id,
            p.periodo,
            (p.inicio_em at time zone $1)::date::text as data,
            to_char(p.inicio_em at time zone $1,'HH24:MI') as "inicioLocal",
            case when p.fim_em is null then null else to_char(p.fim_em at time zone $1,'HH24:MI') end as "fimLocal",
            p.limite_segundos as "limiteSegundos",
            p.fora_horario as "foraHorario",
            case
              when p.fim_em is null then greatest(0,floor(extract(epoch from (now()-p.inicio_em)))::int)
              else greatest(0,floor(extract(epoch from (p.fim_em-p.inicio_em)))::int)
            end as "duracaoSegundos",
            case
              when p.fim_em is null then extract(epoch from (now()-p.inicio_em)) > p.limite_segundos
              else extract(epoch from (p.fim_em-p.inicio_em)) > p.limite_segundos
            end as "excedeuLimite",
            col.id as "colaboradorId",
            col.nome,
            col.matricula,
            col.setor
     from pausas_cafe p
     join colaboradores col on col.id=p.colaborador_id
     where (p.inicio_em at time zone $1)::date = coalesce($2::date,(now() at time zone $1)::date)
     order by p.inicio_em desc
     limit 300`,
    [config.appTimezone, data],
  )

  return c.json({ pausas: result.rows })
})
