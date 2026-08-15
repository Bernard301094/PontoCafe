import { Hono } from 'hono'
import { z } from 'zod'
import { requireRole, requireUser, type AppEnv } from '../auth-runtime.js'
import { config } from '../config.js'
import { query, transaction } from '../db.js'
import { cosineSimilarity, decryptEmbedding, newId } from '../security.js'
import { errorPayload, logServerError } from '../observability.js'
import { embeddingSchema, parseJson, uuidSchema } from './shared.js'

export const workforceRoutes = new Hono<AppEnv>()
workforceRoutes.use('*', requireUser, requireRole('ADMIN', 'SUPERVISOR'))

const collaboratorInputSchema = z.object({
  nome: z.string().trim().min(2).max(160),
  setor: z.string().trim().max(120).optional().nullable(),
  turno: z.string().trim().max(80).optional().nullable(),
})

function rounded(value: number | null | undefined, digits = 4): number | null {
  if (value == null || !Number.isFinite(value)) return null
  return Number(value.toFixed(digits))
}

workforceRoutes.get('/colaboradores/:id/historico', async (c) => {
  const collaboratorId = c.req.param('id')
  if (!uuidSchema.safeParse(collaboratorId).success) {
    return c.json(errorPayload(c, 'Colaborador inválido.', 'COLLABORATOR_INVALID'), 400)
  }

  const diasRaw = Number(c.req.query('dias') ?? 30)
  const dias = Number.isInteger(diasRaw) ? Math.min(365, Math.max(1, diasRaw)) : 30

  const collaborator = await query<{
    id: string
    nome: string
    setor: string | null
    turno: string | null
    ativo: boolean
    criado_em: string
    atualizado_em: string
    rosto_cadastrado: boolean
    modelo: string | null
    versao_modelo: string | null
    biometria_criada_em: string | null
    biometria_atualizada_em: string | null
  }>(
    `select c.id,c.nome,c.setor,c.turno,c.ativo,c.criado_em::text,c.atualizado_em::text,
            (t.colaborador_id is not null) as rosto_cadastrado,
            t.modelo,t.versao_modelo,t.criado_em::text as biometria_criada_em,
            t.atualizado_em::text as biometria_atualizada_em
       from colaboradores c
       left join templates_faciais t on t.colaborador_id=c.id
      where c.id=$1
      limit 1`,
    [collaboratorId],
  )
  const person = collaborator.rows[0]
  if (!person) return c.json(errorPayload(c, 'Colaborador não encontrado.', 'COLLABORATOR_NOT_FOUND'), 404)

  const summary = await query<{
    total: string
    media_segundos: number | null
    acima_limite: string
    fora_horario: string
  }>(
    `select count(*)::text as total,
            round(avg(extract(epoch from (coalesce(fim_em,now())-inicio_em))))::int as media_segundos,
            count(*) filter (
              where fim_em is not null and extract(epoch from (fim_em-inicio_em))>limite_segundos
            )::text as acima_limite,
            count(*) filter (where fora_horario=true)::text as fora_horario
       from pausas_cafe
      where colaborador_id=$1
        and inicio_em >= now() - ($2::text || ' days')::interval`,
    [collaboratorId, dias],
  )

  const pauses = await query<{
    id: string
    periodo: string
    inicio_em: string
    fim_em: string | null
    inicio_local: string
    fim_local: string | null
    duracao_segundos: number | null
    limite_segundos: number
    fora_horario: boolean
    excedeu_limite: boolean
  }>(
    `select id,periodo,inicio_em::text,fim_em::text,
            to_char(inicio_em at time zone $3,'DD/MM/YYYY HH24:MI') as inicio_local,
            case when fim_em is null then null else to_char(fim_em at time zone $3,'DD/MM/YYYY HH24:MI') end as fim_local,
            case when fim_em is null then null else extract(epoch from (fim_em-inicio_em))::int end as duracao_segundos,
            limite_segundos,fora_horario,
            case when fim_em is null then false else extract(epoch from (fim_em-inicio_em))>limite_segundos end as excedeu_limite
       from pausas_cafe
      where colaborador_id=$1
        and inicio_em >= now() - ($2::text || ' days')::interval
      order by inicio_em desc
      limit 100`,
    [collaboratorId, dias, config.appTimezone],
  )

  const biometricAudit = await query<{
    acao: string
    criado_em: string
    ator_tipo: string
    ator_nome: string | null
  }>(
    `select a.acao,a.criado_em::text,a.ator_tipo,u.name as ator_nome
       from auditoria a
       left join "user" u on u.id=a.ator_auth_id
      where a.entidade='COLABORADOR'
        and a.entidade_id=$1
        and a.acao in ('CADASTRAR_ATUALIZAR_ROSTO','EXCLUIR_ROSTO','CALIBRAR_BIOMETRIA')
      order by a.criado_em desc
      limit 20`,
    [collaboratorId],
  )

  const row = summary.rows[0]
  return c.json({
    colaborador: {
      id: person.id,
      nome: person.nome,
      setor: person.setor,
      turno: person.turno,
      ativo: person.ativo,
      criadoEm: person.criado_em,
      atualizadoEm: person.atualizado_em,
      rostoCadastrado: person.rosto_cadastrado,
    },
    periodoDias: dias,
    resumo: {
      totalPausas: Number(row?.total ?? 0),
      mediaSegundos: row?.media_segundos ?? null,
      acimaLimite: Number(row?.acima_limite ?? 0),
      foraHorario: Number(row?.fora_horario ?? 0),
    },
    pausas: pauses.rows.map((pause) => ({
      id: pause.id,
      periodo: pause.periodo,
      inicioEm: pause.inicio_em,
      fimEm: pause.fim_em,
      inicioLocal: pause.inicio_local,
      fimLocal: pause.fim_local,
      duracaoSegundos: pause.duracao_segundos,
      limiteSegundos: pause.limite_segundos,
      foraHorario: pause.fora_horario,
      excedeuLimite: pause.excedeu_limite,
    })),
    biometria: {
      cadastrada: person.rosto_cadastrado,
      modelo: person.modelo,
      versaoModelo: person.versao_modelo,
      criadaEm: person.biometria_criada_em,
      atualizadaEm: person.biometria_atualizada_em,
      retencaoDias: config.biometricRetentionDays,
      eventos: biometricAudit.rows.map((event) => ({
        acao: event.acao,
        criadoEm: event.criado_em,
        atorTipo: event.ator_tipo,
        atorNome: event.ator_nome,
      })),
    },
  })
})

workforceRoutes.post('/colaboradores/importar', async (c) => {
  if (c.get('user').papel !== 'ADMIN') {
    return c.json(errorPayload(c, 'Somente o Administrador pode importar colaboradores.', 'ADMIN_ONLY'), 403)
  }

  const body = await parseJson(c, z.object({
    colaboradores: z.array(collaboratorInputSchema).min(1).max(500),
  }))
  if (!body.ok) return body.response

  try {
    const result = await transaction(async (client) => {
      const created: Array<{ id: string; nome: string; setor: string | null; turno: string | null }> = []
      const existing: Array<{ nome: string; motivo: string }> = []

      for (const item of body.data.colaboradores) {
        const setor = item.setor?.trim() || null
        const turno = item.turno?.trim() || null
        const duplicate = await client.query<{ id: string }>(
          `select id from colaboradores
            where ativo=true
              and lower(trim(nome))=lower(trim($1))
              and lower(coalesce(trim(setor),''))=lower(coalesce(trim($2),''))
              and lower(coalesce(trim(turno),''))=lower(coalesce(trim($3),''))
            limit 1`,
          [item.nome, setor, turno],
        )
        if (duplicate.rows[0]) {
          existing.push({ nome: item.nome, motivo: 'Já existe um colaborador ativo com os mesmos dados.' })
          continue
        }

        const id = newId()
        await client.query(
          'insert into colaboradores (id,matricula,nome,setor,turno) values ($1,null,$2,$3,$4)',
          [id, item.nome, setor, turno],
        )
        created.push({ id, nome: item.nome, setor, turno })
      }

      const actor = c.get('user')
      await client.query(
        `insert into auditoria (ator_auth_id,ator_tipo,acao,entidade,detalhes)
         values ($1,'ADMIN','IMPORTAR_COLABORADORES','COLABORADOR',$2::jsonb)`,
        [actor.id, JSON.stringify({ recebidos: body.data.colaboradores.length, criados: created.length, existentes: existing.length })],
      )

      return { created, existing }
    })

    return c.json({
      recebidos: body.data.colaboradores.length,
      criados: result.created.length,
      existentes: result.existing.length,
      colaboradoresCriados: result.created,
      ignorados: result.existing,
    })
  } catch (error) {
    logServerError(c, 'bulk_collaborator_import_failure', error)
    return c.json(errorPayload(c, 'Não foi possível concluir a importação.', 'COLLABORATOR_IMPORT_FAILED'), 500)
  }
})

workforceRoutes.put('/colaboradores/lote', async (c) => {
  if (c.get('user').papel !== 'ADMIN') {
    return c.json(errorPayload(c, 'Somente o Administrador pode alterar colaboradores em lote.', 'ADMIN_ONLY'), 403)
  }

  const body = await parseJson(c, z.object({
    ids: z.array(uuidSchema).min(1).max(200),
    setor: z.string().trim().max(120).optional().nullable(),
    turno: z.string().trim().max(80).optional().nullable(),
    ativo: z.boolean().optional(),
  }).refine((value) => value.setor !== undefined || value.turno !== undefined || value.ativo !== undefined, {
    message: 'Informe ao menos uma alteração.',
  }))
  if (!body.ok) return body.response

  const uniqueIds = [...new Set(body.data.ids)]
  try {
    const result = await transaction(async (client) => {
      if (body.data.ativo === false) {
        const open = await client.query<{ id: string; nome: string }>(
          `select distinct c.id,c.nome
             from colaboradores c
             join pausas_cafe p on p.colaborador_id=c.id and p.fim_em is null
            where c.id=any($1::uuid[])`,
          [uniqueIds],
        )
        if (open.rows.length > 0) {
          return { blocked: open.rows, updated: [] as Array<{ id: string; nome: string }> }
        }
      }

      const updated = await client.query<{ id: string; nome: string }>(
        `update colaboradores
            set setor=case when $2::boolean then $3 else setor end,
                turno=case when $4::boolean then $5 else turno end,
                ativo=coalesce($6,ativo),
                atualizado_em=now()
          where id=any($1::uuid[])
          returning id,nome`,
        [
          uniqueIds,
          body.data.setor !== undefined,
          body.data.setor?.trim() || null,
          body.data.turno !== undefined,
          body.data.turno?.trim() || null,
          body.data.ativo ?? null,
        ],
      )

      const actor = c.get('user')
      await client.query(
        `insert into auditoria (ator_auth_id,ator_tipo,acao,entidade,detalhes)
         values ($1,'ADMIN','EDITAR_COLABORADORES_LOTE','COLABORADOR',$2::jsonb)`,
        [actor.id, JSON.stringify({ ids: uniqueIds, setor: body.data.setor, turno: body.data.turno, ativo: body.data.ativo })],
      )

      return { blocked: [] as Array<{ id: string; nome: string }>, updated: updated.rows }
    })

    if (result.blocked.length > 0) {
      return c.json(errorPayload(c, 'Existem colaboradores com pausa aberta. Finalize as pausas antes de desativá-los.', 'OPEN_PAUSE_BLOCKS_BULK_UPDATE', {
        bloqueados: result.blocked,
      }), 409)
    }

    return c.json({ ok: true, atualizados: result.updated.length, colaboradores: result.updated })
  } catch (error) {
    logServerError(c, 'bulk_collaborator_update_failure', error)
    return c.json(errorPayload(c, 'Não foi possível alterar os colaboradores selecionados.', 'COLLABORATOR_BULK_UPDATE_FAILED'), 500)
  }
})

workforceRoutes.post('/colaboradores/:id/biometria/calibrar', async (c) => {
  const collaboratorId = c.req.param('id')
  if (!uuidSchema.safeParse(collaboratorId).success) {
    return c.json(errorPayload(c, 'Colaborador inválido.', 'COLLABORATOR_INVALID'), 400)
  }

  const body = await parseJson(c, z.object({
    embedding: embeddingSchema,
    modelo: z.string().trim().min(2).max(100),
    versaoModelo: z.string().trim().min(1).max(50),
  }))
  if (!body.ok) return body.response

  const targetResult = await query<{
    nome: string
    template_cifrado: Buffer
    iv: Buffer
    auth_tag: Buffer
    dimensao: number
  }>(
    `select c.nome,t.template_cifrado,t.iv,t.auth_tag,t.dimensao
       from templates_faciais t
       join colaboradores c on c.id=t.colaborador_id
      where t.colaborador_id=$1 and c.ativo=true
        and t.modelo=$2 and t.versao_modelo=$3
      limit 1`,
    [collaboratorId, body.data.modelo, body.data.versaoModelo],
  )
  const target = targetResult.rows[0]
  if (!target) {
    return c.json(errorPayload(c, 'O colaborador não possui biometria compatível com este modelo.', 'BIOMETRIC_TEMPLATE_NOT_FOUND'), 404)
  }
  if (target.dimensao !== body.data.embedding.length) {
    return c.json(errorPayload(c, 'A dimensão da amostra não corresponde ao cadastro.', 'BIOMETRIC_DIMENSION_MISMATCH'), 409)
  }

  try {
    const storedTarget = decryptEmbedding(target.template_cifrado, target.iv, target.auth_tag)
    const targetScore = cosineSimilarity(storedTarget, body.data.embedding)

    const others = await query<{
      colaborador_id: string
      nome: string
      template_cifrado: Buffer
      iv: Buffer
      auth_tag: Buffer
      dimensao: number
    }>(
      `select t.colaborador_id,c.nome,t.template_cifrado,t.iv,t.auth_tag,t.dimensao
         from templates_faciais t
         join colaboradores c on c.id=t.colaborador_id
        where t.colaborador_id<>$1 and c.ativo=true
          and t.modelo=$2 and t.versao_modelo=$3`,
      [collaboratorId, body.data.modelo, body.data.versaoModelo],
    )

    let nearestOther: { id: string; nome: string; score: number } | null = null
    for (const row of others.rows) {
      if (row.dimensao !== body.data.embedding.length) continue
      try {
        const stored = decryptEmbedding(row.template_cifrado, row.iv, row.auth_tag)
        const score = cosineSimilarity(stored, body.data.embedding)
        if (!nearestOther || score > nearestOther.score) {
          nearestOther = { id: row.colaborador_id, nome: row.nome, score }
        }
      } catch {
        // Um template corrompido não deve impedir o diagnóstico dos demais.
      }
    }

    const nearestScore = nearestOther?.score ?? -1
    const margin = targetScore - nearestScore
    const aprovado = targetScore >= config.faceThreshold && margin >= config.faceIdentificationMargin

    const actor = c.get('user')
    await query(
      `insert into auditoria (ator_auth_id,ator_tipo,acao,entidade,entidade_id,detalhes)
       values ($1,$2,'CALIBRAR_BIOMETRIA','COLABORADOR',$3,$4::jsonb)`,
      [actor.id, actor.papel, collaboratorId, JSON.stringify({
        targetScore: rounded(targetScore),
        nearestOtherScore: nearestOther ? rounded(nearestOther.score) : null,
        margin: rounded(margin),
        aprovado,
        modelo: body.data.modelo,
        versaoModelo: body.data.versaoModelo,
      })],
    )

    return c.json({
      colaboradorId: collaboratorId,
      nome: target.nome,
      score: rounded(targetScore),
      outroMaisProximo: nearestOther ? {
        colaboradorId: nearestOther.id,
        nome: nearestOther.nome,
        score: rounded(nearestOther.score),
      } : null,
      margem: rounded(margin),
      limiar: config.faceThreshold,
      margemMinima: config.faceIdentificationMargin,
      aprovado,
    })
  } catch (error) {
    logServerError(c, 'biometric_calibration_failure', error, { collaboratorId })
    return c.json(errorPayload(c, 'Não foi possível concluir o teste biométrico.', 'BIOMETRIC_CALIBRATION_FAILED'), 500)
  }
})

workforceRoutes.get('/biometria/resumo', async (c) => {
  const totals = await query<{
    ativos: string
    cadastrados: string
    sem_rosto: string
    mais_antigo: string | null
  }>(
    `select count(*) filter (where c.ativo)::text as ativos,
            count(*) filter (where c.ativo and t.colaborador_id is not null)::text as cadastrados,
            count(*) filter (where c.ativo and t.colaborador_id is null)::text as sem_rosto,
            min(t.atualizado_em)::text as mais_antigo
       from colaboradores c
       left join templates_faciais t on t.colaborador_id=c.id`,
  )
  const models = await query<{ modelo: string; versao_modelo: string; total: string }>(
    `select modelo,versao_modelo,count(*)::text as total
       from templates_faciais
      group by modelo,versao_modelo
      order by count(*) desc`,
  )
  const row = totals.rows[0]
  return c.json({
    colaboradoresAtivos: Number(row?.ativos ?? 0),
    biometriaCadastrada: Number(row?.cadastrados ?? 0),
    biometriaPendente: Number(row?.sem_rosto ?? 0),
    templateMaisAntigoEm: row?.mais_antigo ?? null,
    modelos: models.rows.map((model) => ({
      modelo: model.modelo,
      versaoModelo: model.versao_modelo,
      total: Number(model.total),
    })),
    limiar: config.faceThreshold,
    margemMinima: config.faceIdentificationMargin,
    limiarDuplicidade: config.faceEnrollmentDuplicateThreshold,
    retencaoDias: config.biometricRetentionDays,
  })
})

workforceRoutes.post('/biometria/retencao/executar', async (c) => {
  if (c.get('user').papel !== 'ADMIN') {
    return c.json(errorPayload(c, 'Somente o Administrador pode executar a limpeza biométrica.', 'ADMIN_ONLY'), 403)
  }

  try {
    const deleted = await transaction(async (client) => {
      const result = await client.query<{ colaborador_id: string }>(
        `delete from templates_faciais t
          using colaboradores c
          where c.id=t.colaborador_id
            and c.ativo=false
            and c.atualizado_em < now() - ($1::text || ' days')::interval
          returning t.colaborador_id`,
        [config.biometricRetentionDays],
      )
      const actor = c.get('user')
      await client.query(
        `insert into auditoria (ator_auth_id,ator_tipo,acao,entidade,detalhes)
         values ($1,'ADMIN','LIMPEZA_RETENCAO_BIOMETRICA','BIOMETRIA',$2::jsonb)`,
        [actor.id, JSON.stringify({ retencaoDias: config.biometricRetentionDays, removidos: result.rowCount ?? 0 })],
      )
      return result.rows
    })

    return c.json({ ok: true, removidos: deleted.length, retencaoDias: config.biometricRetentionDays })
  } catch (error) {
    logServerError(c, 'biometric_retention_cleanup_failure', error)
    return c.json(errorPayload(c, 'Não foi possível executar a limpeza biométrica.', 'BIOMETRIC_RETENTION_CLEANUP_FAILED'), 500)
  }
})
