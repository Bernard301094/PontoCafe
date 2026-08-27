import { Hono } from 'hono'
import { z } from 'zod'
import { requireRole, requireUser, type AppEnv } from '../auth-runtime.js'
import { avatarUrl } from '../avatar-storage.js'
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
  foraHorario: boolean
  tempoSegundos?: number | null
  duracaoSegundos?: number | null
  excedeuLimite?: boolean | null
  colaboradorId: string
  nome: string
  matricula: string | null
  setor: string | null
  avatarVersion: number
}

function withAvatar(origin: string, row: PauseRow) {
  const { avatarVersion, ...pause } = row
  return {
    ...pause,
    avatarUrl: avatarUrl(origin, row.colaboradorId, avatarVersion),
  }
}

liveRoutes.get('/pausas/ativas', async (c) => {
  const result = await query<PauseRow>(
    `select p.id,
            p.periodo,
            to_char(p.inicio_em at time zone $1,'HH24:MI') as "inicioLocal",
            p.limite_segundos as "limiteSegundos",
            p.fora_horario as "foraHorario",
            greatest(0,floor(extract(epoch from (now()-p.inicio_em)))::int) as "tempoSegundos",
            col.id as "colaboradorId",
            col.nome,
            col.matricula,
            col.setor,
            col.avatar_version as "avatarVersion"
     from pausas_cafe p
     join colaboradores col on col.id=p.colaborador_id
     where p.fim_em is null
     order by p.inicio_em`,
    [config.appTimezone],
  )
  const origin = new URL(c.req.url).origin
  return c.json({ pausas: result.rows.map((row) => withAvatar(origin, row)) })
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
            col.setor,
            col.avatar_version as "avatarVersion"
     from pausas_cafe p
     join colaboradores col on col.id=p.colaborador_id
     where (p.inicio_em at time zone $1)::date = coalesce($2::date,(now() at time zone $1)::date)
     order by p.inicio_em desc
     limit 300`,
    [config.appTimezone, data],
  )

  const origin = new URL(c.req.url).origin
  return c.json({ pausas: result.rows.map((row) => withAvatar(origin, row)) })
})