import { Hono, type Context } from 'hono'
import { z } from 'zod'
import { requireRole, requireUser, type AppEnv } from '../auth-runtime.js'
import { config } from '../config.js'
import { query, transaction } from '../db.js'
import { cosineSimilarity, decryptEmbedding, encryptEmbedding, newId } from '../security.js'
import { embeddingSchema, parseJson, uuidSchema } from './shared.js'

export const collaboratorManagementRoutes = new Hono<AppEnv>()
collaboratorManagementRoutes.use('*', requireUser, requireRole('ADMIN', 'SUPERVISOR'))

const BIOMETRIC_ENROLLMENT_LOCK = 847231

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
    amostras: z.array(embeddingSchema).min(1).max(5).optional(),
    modelo: z.string().trim().min(2).max(100),
    versaoModelo: z.string().trim().min(1).max(50),
  }))
  if (!body.ok) return body.response

  const expectedDimension = body.data.embedding.length
  const samples = body.data.amostras ?? []
  if (samples.some((sample) => sample.length !== expectedDimension)) {
    return c.json({
      erro: 'As amostras faciais possuem dimensões incompatíveis.',
      codigo: 'BIOMETRIC_SAMPLE_DIMENSION_MISMATCH',
    }, 400)
  }

  const duplicateThreshold = Math.max(config.faceThreshold, config.faceEnrollmentDuplicateThreshold)
  const strongSampleThreshold = Math.min(0.99, duplicateThreshold + 0.08)
  const actor = c.get('user')

  const result = await transaction(async (client) => {
    // Serializa cadastros biométricos para impedir que duas requisições
    // simultâneas passem pela verificação antes de qualquer uma persistir.
    await client.query('select pg_advisory_xact_lock($1)', [BIOMETRIC_ENROLLMENT_LOCK])

    const collaborator = await client.query<{ id: string; nome: string }>(
      'select id,nome from colaboradores where id=$1 and ativo=true limit 1',
      [colaboradorId],
    )
    const current = collaborator.rows[0]
    if (!current) return { status: 'NOT_FOUND' as const }

    const existing = await client.query<{
      colaborador_id: string
      nome: string
      ativo: boolean
      template_cifrado: Buffer
      iv: Buffer
      auth_tag: Buffer
      dimensao: number
    }>(
      `select t.colaborador_id,col.nome,col.ativo,t.template_cifrado,t.iv,t.auth_tag,t.dimensao
         from templates_faciais t
         join colaboradores col on col.id=t.colaborador_id
        where t.colaborador_id<>$1
          and t.modelo=$2
          and t.versao_modelo=$3`,
      [colaboradorId, body.data.modelo, body.data.versaoModelo],
    )

    let duplicate: {
      colaboradorId: string
      nome: string
      ativo: boolean
      score: number
      scoreConsolidado: number
      melhorAmostra: number
      amostrasCoincidentes: number
    } | null = null

    for (const row of existing.rows) {
      if (row.dimensao !== expectedDimension) continue
      try {
        const stored = decryptEmbedding(row.template_cifrado, row.iv, row.auth_tag)
        if (stored.length !== row.dimensao) continue

        const consolidatedScore = cosineSimilarity(stored, body.data.embedding)
        const sampleScores = samples.map((sample) => cosineSimilarity(stored, sample))
        const bestSample = sampleScores.length > 0 ? Math.max(...sampleScores) : -1
        const matchingSamples = sampleScores.filter((score) => score >= duplicateThreshold).length
        const isDuplicate =
          consolidatedScore >= duplicateThreshold ||
          matchingSamples >= 2 ||
          bestSample >= strongSampleThreshold
        const strongestScore = Math.max(consolidatedScore, bestSample)

        if (isDuplicate && (!duplicate || strongestScore > duplicate.score)) {
          duplicate = {
            colaboradorId: row.colaborador_id,
            nome: row.nome,
            ativo: row.ativo,
            score: strongestScore,
            scoreConsolidado: consolidatedScore,
            melhorAmostra: bestSample,
            amostrasCoincidentes: matchingSamples,
          }
        }
      } catch (error) {
        console.error(JSON.stringify({
          evento: 'template_ignorado_ao_verificar_duplicidade',
          colaboradorId: row.colaborador_id,
          erro: error instanceof Error ? error.message : 'erro desconhecido',
        }))
      }
    }

    if (duplicate) return { status: 'DUPLICATE' as const, duplicate }

    const encrypted = encryptEmbedding(body.data.embedding)
    await client.query(
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
      [
        newId(),
        colaboradorId,
        encrypted.ciphertext,
        encrypted.iv,
        encrypted.authTag,
        expectedDimension,
        body.data.modelo,
        body.data.versaoModelo,
      ],
    )

    await client.query(
      `insert into auditoria (ator_auth_id,ator_tipo,acao,entidade,entidade_id,detalhes)
       values ($1,$2,'CADASTRAR_ATUALIZAR_ROSTO','COLABORADOR',$3,$4::jsonb)`,
      [
        actor.id,
        actor.papel,
        colaboradorId,
        JSON.stringify({
          modelo: body.data.modelo,
          versaoModelo: body.data.versaoModelo,
          dimensao: expectedDimension,
          verificacaoDuplicidade: true,
          limiteDuplicidade: duplicateThreshold,
          limiteAmostraForte: strongSampleThreshold,
          amostrasVerificadas: samples.length,
        }),
      ],
    )

    return { status: 'OK' as const, nome: current.nome }
  })

  if (result.status === 'NOT_FOUND') {
    return c.json({ erro: 'Colaborador não encontrado ou inativo.' }, 404)
  }

  if (result.status === 'DUPLICATE') {
    const duplicate = result.duplicate
    return c.json({
      erro: duplicate.ativo
        ? `Este rosto já parece estar cadastrado para ${duplicate.nome}. O cadastro foi bloqueado.`
        : `Este rosto pertence a ${duplicate.nome}, que está desativado mas ainda possui biometria retida. Exclua ou aguarde a retenção antes de reutilizar o rosto.`,
      codigo: 'BIOMETRIA_DUPLICADA',
      colaboradorExistenteId: duplicate.colaboradorId,
      colaboradorExistenteAtivo: duplicate.ativo,
      similaridade: Number(duplicate.score.toFixed(4)),
      similaridadeConsolidada: Number(duplicate.scoreConsolidado.toFixed(4)),
      melhorSimilaridadeAmostra: Number(duplicate.melhorAmostra.toFixed(4)),
      amostrasCoincidentes: duplicate.amostrasCoincidentes,
      limiteDuplicidade: duplicateThreshold,
    }, 409)
  }

  return c.json({
    ok: true,
    colaboradorId,
    dimensao: expectedDimension,
    amostrasConsolidadas: 5,
    amostrasVerificadas: samples.length,
    verificacaoDuplicidade: true,
    limiteDuplicidade: duplicateThreshold,
  })
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
