import { Hono, type Context } from 'hono'
import { z } from 'zod'
import { requireRole, requireUser, type AppEnv } from '../auth-runtime.js'
import { query, transaction } from '../db.js'
import { newId } from '../security.js'
import { parseJson, uuidSchema } from './shared.js'

export const collaboratorManagementRoutes = new Hono<AppEnv>()
collaboratorManagementRoutes.use('*', requireUser, requireRole('ADMIN', 'SUPERVISOR'))

async function audit(
  c: Context<AppEnv>,
  action: string,
  collaboratorId: string,
  details: Record<string, unknown> = {},
) {
  const actor = c.get('user')
  await query(
    `insert into auditoria (ator_auth_id,ator_tipo,acao,entidade,entidade_id,detalhes)
     values ($1,$2,$3,'COLABORADOR',$4,$5::jsonb)`,
    [actor.id, actor.papel, action, collaboratorId, JSON.stringify(details)],
  )
}

const collaboratorInput = z.object({
  nome: z.string().trim().min(2).max(160),
  setor: z.string().trim().max(120).optional().nullable(),
  turno: z.string().trim().max(80).optional().nullable(),
})

/**
 * `codigoAtivo` diz apenas SE a pessoa tem um passe vivo e em que estado — nunca
 * qual é. Quem precisa do código em si usa GET /codigos, que é a rota desenhada
 * para isso e devolve só o que o Supervisor tem de ditar.
 */
collaboratorManagementRoutes.get('/colaboradores', async (c) => {
  const result = await query<{
    id: string
    nome: string
    setor: string | null
    turno: string | null
    ativo: boolean
    emPausa: boolean
    codigoAtivo: boolean
  }>(
    `select col.id,col.nome,col.setor,col.turno,col.ativo,
            exists(
              select 1 from pausas_cafe p
               where p.colaborador_id=col.id and p.fim_em is null
            ) as "emPausa",
            exists(
              select 1 from codigos_acesso ca
               where ca.colaborador_id=col.id
                 and ca.cancelado_em is null
                 and ca.retorno_em is null
                 and (ca.saida_em is not null or ca.expira_em>now())
            ) as "codigoAtivo"
       from colaboradores col
      where col.ativo=true
      order by col.nome`,
  )
  return c.json({ colaboradores: result.rows })
})

collaboratorManagementRoutes.post('/colaboradores', async (c) => {
  const body = await parseJson(c, collaboratorInput)
  if (!body.ok) return body.response

  const id = newId()
  await query(
    'insert into colaboradores (id,matricula,nome,setor,turno) values ($1,null,$2,$3,$4)',
    [id, body.data.nome, body.data.setor ?? null, body.data.turno ?? null],
  )

  await audit(c, 'CRIAR_COLABORADOR', id, { nome: body.data.nome })
  return c.json({ id, ...body.data, ativo: true, emPausa: false, codigoAtivo: false }, 201)
})

collaboratorManagementRoutes.put('/colaboradores/:id', async (c) => {
  if (c.get('user').papel !== 'ADMIN') {
    return c.json({ erro: 'Somente o Administrador pode editar os dados do colaborador.' }, 403)
  }

  const colaboradorId = c.req.param('id')
  if (!uuidSchema.safeParse(colaboradorId).success) return c.json({ erro: 'Colaborador inválido.' }, 400)

  const body = await parseJson(c, collaboratorInput)
  if (!body.ok) return body.response

  const result = await transaction(async (client) => {
    const previous = await client.query<{
      id: string
      nome: string
      setor: string | null
      turno: string | null
      ativo: boolean
    }>(
      'select id,nome,setor,turno,ativo from colaboradores where id=$1 for update',
      [colaboradorId],
    )
    const before = previous.rows[0]
    if (!before || !before.ativo) return null

    const updated = await client.query<{
      id: string
      nome: string
      setor: string | null
      turno: string | null
      ativo: boolean
    }>(
      `update colaboradores
          set nome=$2,setor=$3,turno=$4,atualizado_em=now()
        where id=$1
        returning id,nome,setor,turno,ativo`,
      [colaboradorId, body.data.nome, body.data.setor ?? null, body.data.turno ?? null],
    )
    const row = updated.rows[0]
    if (!row) return null

    const actor = c.get('user')
    await client.query(
      `insert into auditoria (ator_auth_id,ator_tipo,acao,entidade,entidade_id,detalhes)
       values ($1,$2,'EDITAR_COLABORADOR','COLABORADOR',$3,$4::jsonb)`,
      [actor.id, actor.papel, colaboradorId, JSON.stringify({
        anterior: { nome: before.nome, setor: before.setor, turno: before.turno },
        novo: { nome: row.nome, setor: row.setor, turno: row.turno },
      })],
    )

    return row
  })

  if (!result) return c.json({ erro: 'Colaborador não encontrado ou inativo.' }, 404)
  return c.json(result)
})

collaboratorManagementRoutes.post('/colaboradores/:id/excluir', async (c) => {
  const colaboradorId = c.req.param('id')
  if (!uuidSchema.safeParse(colaboradorId).success) return c.json({ erro: 'Colaborador inválido.' }, 400)

  const result = await transaction(async (client) => {
    const collaborator = await client.query<{ id: string; nome: string; ativo: boolean }>(
      'select id,nome,ativo from colaboradores where id=$1 for update',
      [colaboradorId],
    )
    const row = collaborator.rows[0]
    if (!row || !row.ativo) return { status: 'NOT_FOUND' as const }

    const openPause = await client.query<{ id: string }>(
      'select id from pausas_cafe where colaborador_id=$1 and fim_em is null limit 1',
      [colaboradorId],
    )
    if (openPause.rows[0]) return { status: 'OPEN_PAUSE' as const }

    // Um passe pendente de alguém que já não trabalha aqui não pode continuar a
    // valer. Códigos já consumidos ficam como estão: são histórico.
    const revoked = await client.query(
      `update codigos_acesso set cancelado_em=now()
        where colaborador_id=$1 and cancelado_em is null
          and saida_em is null and retorno_em is null`,
      [colaboradorId],
    )
    await client.query('update colaboradores set ativo=false,atualizado_em=now() where id=$1', [colaboradorId])

    const actor = c.get('user')
    await client.query(
      `insert into auditoria (ator_auth_id,ator_tipo,acao,entidade,entidade_id,detalhes)
       values ($1,$2,'EXCLUIR_COLABORADOR','COLABORADOR',$3,$4::jsonb)`,
      [actor.id, actor.papel, colaboradorId, JSON.stringify({
        nome: row.nome,
        exclusaoLogica: true,
        codigosCancelados: revoked.rowCount ?? 0,
      })],
    )

    return { status: 'OK' as const, codigosCancelados: revoked.rowCount ?? 0 }
  })

  if (result.status === 'NOT_FOUND') return c.json({ erro: 'Colaborador não encontrado.' }, 404)
  if (result.status === 'OPEN_PAUSE') {
    return c.json({ erro: 'Finalize a pausa aberta antes de excluir este colaborador.' }, 409)
  }

  return c.json({
    ok: true,
    excluido: true,
    exclusaoLogica: true,
    codigosCancelados: result.codigosCancelados,
  })
})
