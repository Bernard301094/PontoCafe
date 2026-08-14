import { Hono } from 'hono'
import { createMiddleware } from 'hono/factory'
import { z } from 'zod'
import type { AppEnv, Device } from '../auth-runtime.js'
import { config } from '../config.js'
import { query } from '../db.js'
import { cosineSimilarity, decryptEmbedding, hashToken, newId, newToken } from '../security.js'
import { embeddingSchema, parseJson, uuidSchema } from './shared.js'

const requireDevice = createMiddleware<AppEnv>(async (c, next) => {
  const token = c.req.header('X-Device-Token')?.trim()
  if (!token) return c.json({ erro: 'Dispositivo não autenticado.' }, 401)

  const result = await query<Device>(
    'select id,nome from dispositivos where token_hash=$1 and ativo=true limit 1',
    [hashToken(token)],
  )
  const device = result.rows[0]
  if (!device) return c.json({ erro: 'Dispositivo inválido.' }, 401)

  c.set('device', device)
  await next()
})

type CatalogTemplateRow = {
  colaborador_id: string
  matricula: string | null
  nome: string
  setor: string | null
  turno: string | null
  template_cifrado: Buffer
  iv: Buffer
  auth_tag: Buffer
  dimensao: number
  modelo: string
  versao_modelo: string
  atualizado_em: string
}

export const localBiometricRoutes = new Hono<AppEnv>()
localBiometricRoutes.use('*', requireDevice)

localBiometricRoutes.get('/biometria/catalogo', async (c) => {
  const modelo = c.req.query('modelo')?.trim() ?? ''
  const versaoModelo = c.req.query('versaoModelo')?.trim() ?? ''
  const versaoAtual = c.req.query('versaoAtual')?.trim() ?? ''

  if (modelo.length < 2 || modelo.length > 100 || versaoModelo.length < 1 || versaoModelo.length > 50) {
    return c.json({ erro: 'Modelo facial inválido.' }, 400)
  }

  const metadata = await query<{ versao: string }>(
    `select coalesce(max(t.atualizado_em)::text,'sem-dados') || ':' || count(*)::text as versao
     from templates_faciais t
     join colaboradores col on col.id=t.colaborador_id
     where col.ativo=true and t.modelo=$1 and t.versao_modelo=$2`,
    [modelo, versaoModelo],
  )
  const versao = metadata.rows[0]?.versao ?? 'sem-dados:0'

  c.header('Cache-Control', 'no-store')
  if (versaoAtual && versaoAtual === versao) {
    return c.json({
      atualizado: false,
      versao,
      modelo,
      versaoModelo,
      limiar: config.faceThreshold,
      margem: config.faceIdentificationMargin,
      templates: [],
    })
  }

  const result = await query<CatalogTemplateRow>(
    `select t.colaborador_id,col.matricula,col.nome,col.setor,col.turno,
            t.template_cifrado,t.iv,t.auth_tag,t.dimensao,t.modelo,t.versao_modelo,
            t.atualizado_em::text
     from templates_faciais t
     join colaboradores col on col.id=t.colaborador_id
     where col.ativo=true and t.modelo=$1 and t.versao_modelo=$2
     order by col.nome`,
    [modelo, versaoModelo],
  )

  const templates = result.rows.flatMap((row) => {
    try {
      const embedding = decryptEmbedding(row.template_cifrado, row.iv, row.auth_tag)
      if (embedding.length !== row.dimensao) return []
      return [{
        colaborador: {
          id: row.colaborador_id,
          matricula: row.matricula,
          nome: row.nome,
          setor: row.setor,
          turno: row.turno,
        },
        embedding,
        modelo: row.modelo,
        versaoModelo: row.versao_modelo,
        atualizadoEm: row.atualizado_em,
      }]
    } catch (error) {
      console.error(JSON.stringify({
        evento: 'template_biometrico_invalido',
        colaboradorId: row.colaborador_id,
        erro: error instanceof Error ? error.message : 'erro desconhecido',
      }))
      return []
    }
  })

  return c.json({
    atualizado: true,
    versao,
    modelo,
    versaoModelo,
    limiar: config.faceThreshold,
    margem: config.faceIdentificationMargin,
    templates,
  })
})

localBiometricRoutes.post('/biometria/confirmar-local', async (c) => {
  const body = await parseJson(c, z.object({
    colaboradorId: uuidSchema,
    embedding: embeddingSchema,
    modelo: z.string().trim().min(2).max(100),
    versaoModelo: z.string().trim().min(1).max(50),
  }))
  if (!body.ok) return body.response

  const device = c.get('device')
  const result = await query<{
    colaborador_id: string
    matricula: string | null
    nome: string
    setor: string | null
    turno: string | null
    template_cifrado: Buffer
    iv: Buffer
    auth_tag: Buffer
    dimensao: number
  }>(
    `select t.colaborador_id,col.matricula,col.nome,col.setor,col.turno,
            t.template_cifrado,t.iv,t.auth_tag,t.dimensao
     from templates_faciais t
     join colaboradores col on col.id=t.colaborador_id
     where t.colaborador_id=$1 and col.ativo=true and t.modelo=$2 and t.versao_modelo=$3
     limit 1`,
    [body.data.colaboradorId, body.data.modelo, body.data.versaoModelo],
  )

  const stored = result.rows[0]
  if (!stored) return c.json({ erro: 'Biometria não cadastrada ou modelo incompatível.' }, 404)
  if (stored.dimensao !== body.data.embedding.length) {
    return c.json({ erro: 'Modelo biométrico incompatível.' }, 409)
  }

  const cadastrado = decryptEmbedding(stored.template_cifrado, stored.iv, stored.auth_tag)
  const score = cosineSimilarity(cadastrado, body.data.embedding)
  if (score < config.faceThreshold) {
    return c.json({
      reconhecido: false,
      motivo: 'CONFIRMACAO_REPROVADA',
      mensagem: 'Não foi possível confirmar sua identidade. Tente novamente.',
      score: Number(score.toFixed(4)),
    }, 401)
  }

  const pausaAberta = await query<{
    id: string
    periodo: 'MANHA' | 'TARDE'
    inicio_em: string
    inicio_local: string
    limite_segundos: number
    tempo_decorrido_segundos: number
  }>(
    `select p.id,p.periodo,p.inicio_em::text,
            to_char(p.inicio_em at time zone $2,'HH24:MI') as inicio_local,
            p.limite_segundos,
            greatest(0,floor(extract(epoch from (now()-p.inicio_em)))::int) as tempo_decorrido_segundos
     from pausas_cafe p
     where p.colaborador_id=$1 and p.fim_em is null
     order by p.inicio_em desc limit 1`,
    [stored.colaborador_id, config.appTimezone],
  )

  const regraAtual = await query<{ periodo: 'MANHA' | 'TARDE'; limite_segundos: number }>(
    `select periodo,limite_segundos from regras_cafe
     where ativo=true
       and (now() at time zone $1)::time>=inicio
       and (now() at time zone $1)::time<fim
     order by inicio limit 1`,
    [config.appTimezone],
  )

  const verificacaoToken = newToken()
  await query(
    `insert into verificacoes_faciais (id,colaborador_id,dispositivo_id,token_hash,score,expira_em)
     values ($1,$2,$3,$4,$5,now()+($6*interval '1 second'))`,
    [newId(), stored.colaborador_id, device.id, hashToken(verificacaoToken), score, config.verificationTtlSeconds],
  )

  const aberta = pausaAberta.rows[0]
  const regra = regraAtual.rows[0]

  return c.json({
    reconhecido: true,
    score: Number(score.toFixed(4)),
    verificacaoToken,
    expiraEmSegundos: config.verificationTtlSeconds,
    colaborador: {
      id: stored.colaborador_id,
      matricula: stored.matricula,
      nome: stored.nome,
      setor: stored.setor,
      turno: stored.turno,
    },
    acaoSugerida: aberta ? 'FINALIZAR' : 'INICIAR',
    pausaAberta: aberta ? {
      id: aberta.id,
      periodo: aberta.periodo,
      inicioEm: aberta.inicio_em,
      inicioLocal: aberta.inicio_local,
      limiteSegundos: aberta.limite_segundos,
      tempoDecorridoSegundos: aberta.tempo_decorrido_segundos,
    } : null,
    dentroHorario: Boolean(regra),
    periodoAtual: regra?.periodo ?? null,
    limiteSegundos: regra?.limite_segundos ?? null,
  })
})
