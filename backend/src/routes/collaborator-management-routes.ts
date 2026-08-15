import { Hono, type Context } from 'hono'
import { z } from 'zod'
import { requireRole, requireUser, type AppEnv } from '../auth-runtime.js'
import { config } from '../config.js'
import { query, transaction } from '../db.js'
import { cosineSimilarity, decryptEmbedding, encryptEmbedding, newId } from '../security.js'
import { embeddingSchema, parseJson, uuidSchema } from './shared.js'

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

collaboratorManagementRoutes.get('/colaboradores', async (c) => {
  const result = await query<{
    id: string
    nome: string
    setor: string | null
    turno: string | null
    ativo: boolean
    rostoCadastrado: boolean
  }>(
    `select col.id,col.nome,col.setor,col.turno,col.ativo,
            exists(select 1 from templates_faciais t where t.colaborador_id=col.id) as "rostoCadastrado"
       from colaboradores col
      where col.ativo=true
      order by col.nome`,
  )
  return c.json({ colaboradores: result.rows })
})

collaboratorManagementRoutes.post('/colaboradores', async (c) => {
  const body = await parseJson(c, z.object({
    nome: z.string().trim().min(2).max(160),
    setor: z.string().trim().max(120).optional().nullable(),
    turno: z.string().trim().max(80).optional().nullable(),
  }))
  if (!body.ok) return body.response

  const id = newId()
  await query(
    'insert into colaboradores (id,matricula,nome,setor,turno) values ($1,null,$2,$3,$4)',
    [id, body.data.nome, body.data.setor ?? null, body.data.turno ?? null],
  )

  await audit(c, 'CRIAR_COLABORADOR', id, { nome: body.data.nome })
  return c.json({ id, ...body.data, ativo: true, rostoCadastrado: false }, 201)
})

collaboratorManagementRoutes.put('/colaboradores/:id', async (c) => {
  if (c.get('user').papel !== 'ADMIN') return c.json({ erro: 'Somente o Administrador pode editar os dados do colaborador.' }, 403)

  const colaboradorId = c.req.param('id')
  if (!uuidSchema.safeParse(colaboradorId).success) return c.json({ erro: 'Colaborador inválido.' }, 400)

  const body = await parseJson(c, z.object({
    nome: z.string().trim().min(2).max(160),
    setor: z.string().trim().max(120).optional().nullable(),
    turno: z.string().trim().max(80).optional().nullable(),
  }))
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

    const face = await client.query<{ cadastrado: boolean }>(
      'select exists(select 1 from templates_faciais where colaborador_id=$1) as cadastrado',
      [colaboradorId],
    )

    return {
      ...row,
      rostoCadastrado: face.rows[0]?.cadastrado ?? false,
    }
  })

  if (!result) return c.json({ erro: 'Colaborador não encontrado ou inativo.' }, 404)
  return c.json(result)
})

collaboratorManagementRoutes.put('/colaboradores/:id/biometria', async (c) => {
  const colaboradorId = c.req.param('id')
  if (!uuidSchema.safeParse(colaboradorId).success) return c.json({ erro: 'Colaborador inválido.' }, 400)

  const body = await parseJson(c, z.object({
    embedding: embeddingSchema,
    modelo: z.string().trim().min(2).max(100),
    versaoModelo: z.string().trim().min(1).max(50),
  }))
  if (!body.ok) return body.response

  const collaborator = await query<{ id: string; nome: string }>(
    'select id,nome from colaboradores where id=$1 and ativo=true limit 1',
    [colaboradorId],
  )
  if (!collaborator.rows[0]) return c.json({ erro: 'Colaborador não encontrado ou inativo.' }, 404)

  const existing = await query<{
    colaborador_id: string
    nome: string
    template_cifrado: Buffer
    iv: Buffer
    auth_tag: Buffer
    dimensao: number
  }>(
    `select t.colaborador_id,col.nome,t.template_cifrado,t.iv,t.auth_tag,t.dimensao
       from templates_faciais t
       join colaboradores col on col.id=t.colaborador_id
      where t.colaborador_id<>$1
        and col.ativo=true
        and t.modelo=$2
        and t.versao_modelo=$3`,
    [colaboradorId, body.data.modelo, body.data.versaoModelo],
  )

  const duplicateThreshold = Math.min(
    0.99,
    Math.max(config.faceEnrollmentDuplicateThreshold, config.faceThreshold + 0.05),
  )
  let duplicate: { colaboradorId: string; nome: string; score: number } | null = null

  for (const row of existing.rows) {
    if (row.dimensao !== body.data.embedding.length) continue
    try {
      const stored = decryptEmbedding(row.template_cifrado, row.iv, row.auth_tag)
      if (stored.length !== row.dimensao) continue
      const score = cosineSimilarity(stored, body.data.embedding)
      if (score >= duplicateThreshold && (!duplicate || score > duplicate.score)) {
        duplicate = { colaboradorId: row.colaborador_id, nome: row.nome, score }
      }
    } catch (error) {
      console.error(JSON.stringify({
        evento: 'template_ignorado_ao_verificar_duplicidade',
        colaboradorId: row.colaborador_id,
        erro: error instanceof Error ? error.message : 'erro desconhecido',
      }))
    }
  }

  if (duplicate) {
    return c.json({
      erro: `Este rosto parece já estar cadastrado para ${duplicate.nome}. Verifique o colaborador antes de continuar.`,
      codigo: 'BIOMETRIA_DUPLICADA',
      colaboradorExistenteId: duplicate.colaboradorId,
      similaridade: Number(duplicate.score.toFixed(4)),
    }, 409)
  }

  const encrypted = encryptEmbedding(body.data.embedding)
  await query(
    `insert into templates_faciais
      (id,colaborador_id,template_cifrado,iv,auth_tag,dimensao,modelo,versao_modelo)
     values ($1,$2,$3,$4,$5,$6,$7,$8)
     on conflict (colaborador_id) do update set
       template_cifrado=excluded.template_cifrado,
       iv=excluded.iv,
       auth_tag=excluded.auth_tag,
       dimensao=excluded.dimensao,
       modelo=excluded.modelo,
       versao_modelo=excluded.versao_modelo,
       atualizado_em=now()`,
    [newId(), colaboradorId, encrypted.ciphertext, encrypted.iv, encrypted.authTag,
      body.data.embedding.length, body.data.modelo, body.data.versaoModelo],
  )

  await audit(c, 'CADASTRAR_ATUALIZAR_ROSTO', colaboradorId, {
    modelo: body.data.modelo,
    versaoModelo: body.data.versaoModelo,
    dimensao: body.data.embedding.length,
  })

  return c.json({ ok: true, colaboradorId, dimensao: body.data.embedding.length, amostrasConsolidadas: 5 })
})

collaboratorManagementRoutes.post('/colaboradores/:id/biometria/excluir', async (c) => {
  const colaboradorId = c.req.param('id')
  if (!uuidSchema.safeParse(colaboradorId).success) return c.json({ erro: 'Colaborador inválido.' }, 400)

  const openPause = await query<{ id: string }>(
    'select id from pausas_cafe where colaborador_id=$1 and fim_em is null limit 1',
    [colaboradorId],
  )
  if (openPause.rows[0]) {
    return c.json({ erro: 'Finalize a pausa aberta antes de excluir o rosto deste colaborador.' }, 409)
  }

  const deleted = await query<{ colaborador_id: string }>(
    'delete from templates_faciais where colaborador_id=$1 returning colaborador_id',
    [colaboradorId],
  )
  if (!deleted.rows[0]) return c.json({ erro: 'Este colaborador não possui rosto cadastrado.' }, 404)

  await audit(c, 'EXCLUIR_ROSTO', colaboradorId)
  return c.json({ ok: true, rostoExcluido: true })
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

    await client.query('delete from templates_faciais where colaborador_id=$1', [colaboradorId])
    await client.query('update colaboradores set ativo=false,atualizado_em=now() where id=$1', [colaboradorId])

    const actor = c.get('user')
    await client.query(
      `insert into auditoria (ator_auth_id,ator_tipo,acao,entidade,entidade_id,detalhes)
       values ($1,$2,'EXCLUIR_COLABORADOR','COLABORADOR',$3,$4::jsonb)`,
      [actor.id, actor.papel, colaboradorId, JSON.stringify({ nome: row.nome, exclusaoLogica: true, rostoExcluido: true })],
    )

    return { status: 'OK' as const }
  })

  if (result.status === 'NOT_FOUND') return c.json({ erro: 'Colaborador não encontrado.' }, 404)
  if (result.status === 'OPEN_PAUSE') {
    return c.json({ erro: 'Finalize a pausa aberta antes de excluir este colaborador.' }, 409)
  }

  return c.json({ ok: true, excluido: true, exclusaoLogica: true, rostoExcluido: true })
})
