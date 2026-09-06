import { Hono } from 'hono'
import { z } from 'zod'
import { requireRole, requireUser, type AppEnv } from '../auth-runtime.js'
import { config } from '../config.js'
import { query } from '../db.js'

export const liveRoutes = new Hono<AppEnv>()
liveRoutes.use('*', requireUser, requireRole('ADMIN', 'SUPERVISOR'))

type PauseRow = {
  id: string
  periodo: string
  data?: string | null
  inicioLocal: string
  fimLocal?: string | null
  limiteSegundos: number
  carenciaSegundos: number
  foraHorario: boolean
  tempoSegundos?: number | null
  duracaoSegundos?: number | null
  /** Segundos já descontados da carência: é este valor que se compara ao limite. */
  tempoContadoSegundos?: number | null
  excedeuLimite?: boolean | null
  emCarencia?: boolean | null
  colaboradorId: string
  nome: string
  matricula: string | null
  setor: string | null
}

liveRoutes.get('/pausas/ativas', async (c) => {
  const result = await query<PauseRow>(
    `select p.id,
            p.periodo,
            to_char(p.inicio_em at time zone $1,'HH24:MI') as "inicioLocal",
            p.limite_segundos as "limiteSegundos",
            p.carencia_segundos as "carenciaSegundos",
            p.fora_horario as "foraHorario",
            greatest(0,floor(extract(epoch from (now()-p.inicio_em)))::int) as "tempoSegundos",
            greatest(
              0,
              floor(extract(epoch from (now()-p.inicio_em)))::int - p.carencia_segundos
            ) as "tempoContadoSegundos",
            (now() < p.inicio_em + (p.carencia_segundos * interval '1 second')) as "emCarencia",
            (now() > p.inicio_em + ((p.carencia_segundos + p.limite_segundos) * interval '1 second'))
              as "excedeuLimite",
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
  const result = await query<PauseRow>(
    `select p.id,
            p.periodo,
            (p.inicio_em at time zone $1)::date::text as data,
            to_char(p.inicio_em at time zone $1,'HH24:MI') as "inicioLocal",
            case when p.fim_em is null then null else to_char(p.fim_em at time zone $1,'HH24:MI') end as "fimLocal",
            p.limite_segundos as "limiteSegundos",
            p.carencia_segundos as "carenciaSegundos",
            p.fora_horario as "foraHorario",
            greatest(0,floor(extract(epoch from (coalesce(p.fim_em,now())-p.inicio_em)))::int) as "duracaoSegundos",
            greatest(
              0,
              floor(extract(epoch from (coalesce(p.fim_em,now())-p.inicio_em)))::int - p.carencia_segundos
            ) as "tempoContadoSegundos",
            (coalesce(p.fim_em,now()) > p.inicio_em + ((p.carencia_segundos + p.limite_segundos) * interval '1 second'))
              as "excedeuLimite",
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
