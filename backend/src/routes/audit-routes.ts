import { Hono } from 'hono'
import { z } from 'zod'
import { requireRole, requireUser, type AppEnv } from '../auth-runtime.js'
import { config } from '../config.js'
import { query } from '../db.js'

export const auditRoutes = new Hono<AppEnv>()
auditRoutes.use('*', requireUser, requireRole('ADMIN'))

auditRoutes.get('/auditoria', async (c) => {
  const parsed = z.object({
    limite: z.coerce.number().int().min(1).max(250).default(100),
    acao: z.string().trim().max(80).optional(),
  }).safeParse({
    limite: c.req.query('limite') ?? 100,
    acao: c.req.query('acao') || undefined,
  })
  if (!parsed.success) return c.json({ erro: 'Filtros de auditoria inválidos.' }, 400)

  const result = await query<{
    id: string
    atorTipo: string
    atorNome: string
    acao: string
    entidade: string | null
    entidadeId: string | null
    detalhes: Record<string, unknown> | null
    criadoEm: string
    criadoLocal: string
  }>(
    `select a.id::text as id,
            a.ator_tipo as "atorTipo",
            coalesce(u.name,a.ator_tipo,'Sistema') as "atorNome",
            a.acao,
            a.entidade,
            a.entidade_id as "entidadeId",
            a.detalhes,
            a.criado_em::text as "criadoEm",
            to_char(a.criado_em at time zone $1,'DD/MM/YYYY HH24:MI') as "criadoLocal"
       from auditoria a
       left join "user" u on u.id=a.ator_auth_id
      where ($2::text is null or a.acao=$2)
      order by a.criado_em desc
      limit $3`,
    [config.appTimezone, parsed.data.acao ?? null, parsed.data.limite],
  )

  return c.json({ eventos: result.rows })
})

auditRoutes.get('/operacao/resumo', async (c) => {
  const result = await query<{
    colaboradoresAtivos: number
    rostosPendentes: number
    dispositivosAtivos: number
    dispositivosSemPin: number
    dispositivosInativos: number
    supervisoresAtivos: number
    administradoresAtivos: number
    pausasAbertas: number
  }>(
    `select
       (select count(*)::int from colaboradores where ativo=true) as "colaboradoresAtivos",
       (select count(*)::int from colaboradores c where c.ativo=true and not exists (
          select 1 from templates_faciais t where t.colaborador_id=c.id
       )) as "rostosPendentes",
       (select count(*)::int from dispositivos where ativo=true) as "dispositivosAtivos",
       (select count(*)::int from dispositivos where ativo=true and unlock_pin_hash is null) as "dispositivosSemPin",
       (select count(*)::int from dispositivos where ativo=false) as "dispositivosInativos",
       (select count(*)::int from "user" where role='user' and coalesce(banned,false)=false) as "supervisoresAtivos",
       (select count(*)::int from "user" where role='admin' and coalesce(banned,false)=false) as "administradoresAtivos",
       (select count(*)::int from pausas_cafe where fim_em is null) as "pausasAbertas"`,
  )

  return c.json({ resumo: result.rows[0] })
})
