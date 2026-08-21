import { Hono } from 'hono'
import { z } from 'zod'
import { requireRole, requireUser, type AppEnv } from '../auth-runtime.js'
import { validateBiometricVector } from '../biometric-matching.js'
import { config } from '../config.js'
import { query } from '../db.js'
import { errorPayload, logServerError } from '../observability.js'
import { cosineSimilarity, decryptEmbedding } from '../security.js'
import { embeddingSchema, parseJson, uuidSchema } from './shared.js'

export const biometricCalibrationRoutes = new Hono<AppEnv>()
biometricCalibrationRoutes.use('*', requireUser, requireRole('ADMIN', 'SUPERVISOR'))

function rounded(value: number | null | undefined, digits = 6): number | null {
  if (value == null || !Number.isFinite(value)) return null
  return Number(value.toFixed(digits))
}

biometricCalibrationRoutes.post('/colaboradores/:id/biometria/calibrar', async (c) => {
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
  if (!validateBiometricVector(body.data.embedding).valid) {
    return c.json(errorPayload(c, 'A amostra facial é inválida ou incompatível com o modelo.', 'BIOMETRIC_VECTOR_INVALID'), 400)
  }

  try {
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
        order by t.atualizado_em desc,t.criado_em desc`,
      [collaboratorId, body.data.modelo, body.data.versaoModelo],
    )
    const target = targetResult.rows[0]
    if (!target) {
      return c.json(errorPayload(c, 'O colaborador não possui biometria compatível com este modelo.', 'BIOMETRIC_TEMPLATE_NOT_FOUND'), 404)
    }
    let targetScore = -1
    let validTargetTemplates = 0
    for (const targetTemplate of targetResult.rows) {
      if (targetTemplate.dimensao !== body.data.embedding.length) continue
      try {
        const targetEmbedding = decryptEmbedding(
          targetTemplate.template_cifrado,
          targetTemplate.iv,
          targetTemplate.auth_tag,
        )
        if (!validateBiometricVector(targetEmbedding).valid) continue
        const score = cosineSimilarity(targetEmbedding, body.data.embedding)
        if (!Number.isFinite(score)) continue
        validTargetTemplates += 1
        targetScore = Math.max(targetScore, score)
      } catch {
        // A calibração continua com outras aparências válidas da mesma pessoa.
      }
    }
    if (validTargetTemplates === 0) {
      return c.json(errorPayload(c, 'Os templates do colaborador estão inválidos ou incompatíveis.', 'BIOMETRIC_TEMPLATE_INVALID'), 409)
    }

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
    let impostorComparisons = 0
    let impostorAccepts = 0

    for (const row of others.rows) {
      if (row.dimensao !== body.data.embedding.length) continue
      try {
        const stored = decryptEmbedding(row.template_cifrado, row.iv, row.auth_tag)
        if (!validateBiometricVector(stored).valid) continue
        const score = cosineSimilarity(stored, body.data.embedding)
        if (!Number.isFinite(score)) continue
        impostorComparisons += 1
        if (score >= config.faceThreshold) impostorAccepts += 1
        if (!nearestOther || score > nearestOther.score) {
          nearestOther = { id: row.colaborador_id, nome: row.nome, score }
        }
      } catch {
        // Template corrompido é ignorado e pode ser investigado separadamente.
      }
    }

    const nearestScore = nearestOther?.score ?? -1
    const margin = targetScore - nearestScore
    const approved = targetScore >= config.faceThreshold && margin >= config.faceIdentificationMargin
    const top1Correct = nearestOther == null || targetScore > nearestOther.score
    const falseReject = !approved

    const actor = c.get('user')
    await query(
      `insert into auditoria (ator_auth_id,ator_tipo,acao,entidade,entidade_id,detalhes)
       values ($1,$2,'CALIBRAR_BIOMETRIA','COLABORADOR',$3,$4::jsonb)`,
      [actor.id, actor.papel, collaboratorId, JSON.stringify({
        targetScore: rounded(targetScore),
        nearestOtherScore: nearestOther ? rounded(nearestOther.score) : null,
        margin: rounded(margin),
        aprovado: approved,
        falseReject,
        top1Correct,
        impostorComparisons,
        impostorAccepts,
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
      aprovado: approved,
      top1Correto: top1Correct,
      comparacoesImpostor: impostorComparisons,
      falsosAceitesImpostor: impostorAccepts,
    })
  } catch (error) {
    logServerError(c, 'biometric_calibration_failure', error, { collaboratorId })
    return c.json(errorPayload(c, 'Não foi possível concluir o teste biométrico.', 'BIOMETRIC_CALIBRATION_FAILED'), 500)
  }
})

biometricCalibrationRoutes.get('/biometria/calibracao/resumo', async (c) => {
  const result = await query<{
    total: string
    aprovados: string
    falsos_rejeites: string
    top1_corretos: string
    comparacoes_impostor: string
    falsos_aceites_impostor: string
    score_medio: number | null
    margem_media: number | null
  }>(
    `select count(*)::text as total,
            count(*) filter (where coalesce((detalhes->>'aprovado')::boolean,false))::text as aprovados,
            count(*) filter (where coalesce((detalhes->>'falseReject')::boolean, not coalesce((detalhes->>'aprovado')::boolean,false)))::text as falsos_rejeites,
            count(*) filter (where coalesce((detalhes->>'top1Correct')::boolean,
              coalesce((detalhes->>'targetScore')::double precision,-2) > coalesce((detalhes->>'nearestOtherScore')::double precision,-1)))::text as top1_corretos,
            coalesce(sum(coalesce((detalhes->>'impostorComparisons')::int,0)),0)::text as comparacoes_impostor,
            coalesce(sum(coalesce((detalhes->>'impostorAccepts')::int,0)),0)::text as falsos_aceites_impostor,
            avg((detalhes->>'targetScore')::double precision) as score_medio,
            avg((detalhes->>'margin')::double precision) as margem_media
       from auditoria
      where acao='CALIBRAR_BIOMETRIA'
        and detalhes ? 'targetScore'`,
  )
  const row = result.rows[0]
  const total = Number(row?.total ?? 0)
  const falseRejects = Number(row?.falsos_rejeites ?? 0)
  const top1 = Number(row?.top1_corretos ?? 0)
  const impostorComparisons = Number(row?.comparacoes_impostor ?? 0)
  const impostorAccepts = Number(row?.falsos_aceites_impostor ?? 0)

  return c.json({
    amostras: total,
    aprovadas: Number(row?.aprovados ?? 0),
    falseRejectRate: total > 0 ? rounded(falseRejects / total) : null,
    top1Accuracy: total > 0 ? rounded(top1 / total) : null,
    falseAcceptRate: impostorComparisons > 0 ? rounded(impostorAccepts / impostorComparisons) : null,
    comparacoesImpostor: impostorComparisons,
    falsosAceitesImpostor: impostorAccepts,
    scoreMedio: rounded(row?.score_medio),
    margemMedia: rounded(row?.margem_media),
    limiar: config.faceThreshold,
    margemMinima: config.faceIdentificationMargin,
    observacao: 'As taxas refletem apenas as amostras de calibração realizadas neste ambiente e não substituem validação biométrica formal.',
  })
})
