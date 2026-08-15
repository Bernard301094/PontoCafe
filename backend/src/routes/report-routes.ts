import { Hono } from 'hono'
import { z } from 'zod'
import { requireRole, requireUser, type AppEnv } from '../auth-runtime.js'
import { config } from '../config.js'
import { query } from '../db.js'

export const reportRoutes = new Hono<AppEnv>()
reportRoutes.use('*', requireUser, requireRole('ADMIN', 'SUPERVISOR'))

const dateSchema = z.string()
  .regex(/^\d{4}-\d{2}-\d{2}$/)
  .refine((value) => {
    const [year, month, day] = value.split('-').map(Number)
    const date = new Date(Date.UTC(year, month - 1, day))
    return date.getUTCFullYear() === year &&
      date.getUTCMonth() === month - 1 &&
      date.getUTCDate() === day
  }, 'Data inválida.')

const periodSchema = z.object({
  inicio: dateSchema,
  fim: dateSchema,
})
  .refine((value) => value.inicio <= value.fim, 'O início deve ser anterior ou igual ao fim.')
  .refine((value) => {
    const start = Date.parse(`${value.inicio}T00:00:00Z`)
    const end = Date.parse(`${value.fim}T00:00:00Z`)
    return (end - start) / 86_400_000 <= 366
  }, 'O período máximo para um relatório é de 367 dias incluindo as duas datas.')

function parsePeriod(c: { req: { query(name: string): string | undefined } }) {
  return periodSchema.safeParse({ inicio: c.req.query('inicio'), fim: c.req.query('fim') })
}

reportRoutes.get('/relatorios/resumo', async (c) => {
  const parsed = parsePeriod(c)
  if (!parsed.success) {
    return c.json({ erro: parsed.error.issues[0]?.message || 'Informe início e fim em YYYY-MM-DD.' }, 400)
  }
  const values = [config.appTimezone, parsed.data.inicio, parsed.data.fim]

  const summary = await query<{
    totalPausas: number
    colaboradores: number
    mediaSegundos: number | null
    acimaLimite: number
    foraHorario: number
  }>(
    `select count(*)::int as "totalPausas",
            count(distinct colaborador_id)::int as colaboradores,
            round(avg(extract(epoch from (coalesce(fim_em,now())-inicio_em))))::int as "mediaSegundos",
            count(*) filter (where fim_em is not null and extract(epoch from (fim_em-inicio_em))>limite_segundos)::int as "acimaLimite",
            count(*) filter (where fora_horario)::int as "foraHorario"
       from pausas_cafe
      where (inicio_em at time zone $1)::date between $2::date and $3::date`,
    values,
  )

  const byDay = await query<{
    data: string
    pausas: number
    acimaLimite: number
    foraHorario: number
  }>(
    `select (inicio_em at time zone $1)::date::text as data,
            count(*)::int as pausas,
            count(*) filter (where fim_em is not null and extract(epoch from (fim_em-inicio_em))>limite_segundos)::int as "acimaLimite",
            count(*) filter (where fora_horario)::int as "foraHorario"
       from pausas_cafe
      where (inicio_em at time zone $1)::date between $2::date and $3::date
      group by 1 order by 1`,
    values,
  )

  const topDelays = await query<{
    colaboradorId: string
    nome: string
    ocorrencias: number
    maiorDuracaoSegundos: number
    excessoTotalSegundos: number
  }>(
    `select c.id::text as "colaboradorId",
            c.nome,
            count(*)::int as ocorrencias,
            max(extract(epoch from (p.fim_em-p.inicio_em)))::int as "maiorDuracaoSegundos",
            sum(greatest(0,extract(epoch from (p.fim_em-p.inicio_em))-p.limite_segundos))::int as "excessoTotalSegundos"
       from pausas_cafe p
       join colaboradores c on c.id=p.colaborador_id
      where p.fim_em is not null
        and extract(epoch from (p.fim_em-p.inicio_em))>p.limite_segundos
        and (p.inicio_em at time zone $1)::date between $2::date and $3::date
      group by c.id,c.nome
      order by "excessoTotalSegundos" desc,"maiorDuracaoSegundos" desc
      limit 10`,
    values,
  )

  return c.json({
    periodo: parsed.data,
    resumo: summary.rows[0],
    porDia: byDay.rows,
    maioresAtrasos: topDelays.rows,
  })
})

reportRoutes.get('/relatorios/csv', async (c) => {
  const parsed = parsePeriod(c)
  if (!parsed.success) {
    return c.json({ erro: parsed.error.issues[0]?.message || 'Informe início e fim em YYYY-MM-DD.' }, 400)
  }

  const rows = await query<{
    data: string
    nome: string
    setor: string | null
    periodo: string
    inicio: string
    fim: string | null
    duracaoSegundos: number | null
    limiteSegundos: number
    foraHorario: boolean
  }>(
    `select (p.inicio_em at time zone $1)::date::text as data,
            c.nome,c.setor,p.periodo,
            to_char(p.inicio_em at time zone $1,'HH24:MI:SS') as inicio,
            case when p.fim_em is null then null else to_char(p.fim_em at time zone $1,'HH24:MI:SS') end as fim,
            case when p.fim_em is null then null else floor(extract(epoch from (p.fim_em-p.inicio_em)))::int end as "duracaoSegundos",
            p.limite_segundos as "limiteSegundos",
            p.fora_horario as "foraHorario"
       from pausas_cafe p
       join colaboradores c on c.id=p.colaborador_id
      where (p.inicio_em at time zone $1)::date between $2::date and $3::date
      order by p.inicio_em,c.nome`,
    [config.appTimezone, parsed.data.inicio, parsed.data.fim],
  )

  const escapeCsv = (value: unknown) => {
    let text = value == null ? '' : String(value)
    // Evita CSV/Formula Injection quando o arquivo é aberto em Excel/Sheets.
    if (/^[=+\-@]/.test(text)) text = `'${text}`
    return `"${text.replace(/"/g, '""')}"`
  }
  const header = ['data','nome','setor','periodo','inicio','fim','duracao_segundos','limite_segundos','fora_horario']
  const lines = rows.rows.map((row) => [
    row.data,row.nome,row.setor,row.periodo,row.inicio,row.fim,row.duracaoSegundos,row.limiteSegundos,row.foraHorario,
  ].map(escapeCsv).join(','))
  const csv = `\uFEFF${header.join(',')}\n${lines.join('\n')}`

  c.header('Content-Type', 'text/csv; charset=utf-8')
  c.header('Content-Disposition', `attachment; filename="pontocafe-${parsed.data.inicio}-${parsed.data.fim}.csv"`)
  c.header('Cache-Control', 'no-store')
  return c.body(csv)
})
