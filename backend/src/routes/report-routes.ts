import { Hono } from 'hono'
import { z } from 'zod'
import { requireRole, requireUser, type AppEnv } from '../auth-runtime.js'
import { config } from '../config.js'
import { query } from '../db.js'

export const reportRoutes = new Hono<AppEnv>()
reportRoutes.use('*', requireUser, requireRole('ADMIN', 'SUPERVISOR'))
reportRoutes.get('/relatorios/resumo', async (c) => {
  const schema = z.object({ inicio: z.string().regex(/^\d{4}-\d{2}-\d{2}$/), fim: z.string().regex(/^\d{4}-\d{2}-\d{2}$/) }).refine((v) => v.inicio <= v.fim)
  const parsed = schema.safeParse({ inicio: c.req.query('inicio'), fim: c.req.query('fim') })
  if (!parsed.success) return c.json({ erro: 'Informe início e fim em YYYY-MM-DD.' }, 400)
  const values = [config.appTimezone, parsed.data.inicio, parsed.data.fim]
  const summary = await query(`select count(*)::int as total_pausas,count(distinct colaborador_id)::int as colaboradores,round(avg(extract(epoch from (coalesce(fim_em,now())-inicio_em))))::int as media_segundos,count(*) filter (where fim_em is not null and extract(epoch from (fim_em-inicio_em))>limite_segundos)::int as acima_limite,count(*) filter (where fora_horario)::int as fora_horario from pausas_cafe where (inicio_em at time zone $1)::date between $2::date and $3::date`, values)
  const byDay = await query(`select (inicio_em at time zone $1)::date::text as data,count(*)::int as pausas,count(*) filter (where fim_em is not null and extract(epoch from (fim_em-inicio_em))>limite_segundos)::int as acima_limite,count(*) filter (where fora_horario)::int as fora_horario from pausas_cafe where (inicio_em at time zone $1)::date between $2::date and $3::date group by 1 order by 1`, values)
  return c.json({ periodo: parsed.data, resumo: summary.rows[0], porDia: byDay.rows })
})
