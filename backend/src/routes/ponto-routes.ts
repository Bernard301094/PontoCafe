import { Hono } from 'hono'
import { createMiddleware } from 'hono/factory'
import { z } from 'zod'
import type { AppEnv, Device } from '../auth-runtime.js'
import { config } from '../config.js'
import { query, transaction } from '../db.js'
import { cosineSimilarity, decryptEmbedding, hashAuthorizationCode, hashToken, newId, newToken } from '../security.js'
import { embeddingSchema, parseJson, periodoSchema, uuidSchema } from './shared.js'

const requireDevice = createMiddleware<AppEnv>(async (c, next) => {
  const token = c.req.header('X-Device-Token')?.trim()
  if (!token) return c.json({ erro: 'Dispositivo não autenticado.' }, 401)
  const result = await query<Device>('select id,nome from dispositivos where token_hash=$1 and ativo=true limit 1', [hashToken(token)])
  const device = result.rows[0]
  if (!device) return c.json({ erro: 'Dispositivo inválido.' }, 401)
  c.set('device', device)
  await next()
})

class AppError extends Error {
  constructor(message: string, readonly status: 401 | 403 | 404 | 409) { super(message) }
}

type TemplateFacial = {
  colaborador_id: string
  matricula: string | null
  nome: string
  setor: string | null
  turno: string | null
  template_cifrado: Buffer
  iv: Buffer
  auth_tag: Buffer
  dimensao: number
}

type Candidato = TemplateFacial & { score: number }

export const pontoRoutes = new Hono<AppEnv>()
pontoRoutes.use('*', requireDevice)

pontoRoutes.get('/colaboradores', async (c) => {
  const busca = c.req.query('q')?.trim() ?? ''
  const result = await query(
    `select id,matricula,nome,setor,turno from colaboradores
     where ativo=true and ($1='' or nome ilike '%'||$1||'%' or coalesce(matricula,'') ilike '%'||$1||'%')
     order by nome limit 100`,
    [busca],
  )
  return c.json({ colaboradores: result.rows })
})

pontoRoutes.post('/biometria/identificar', async (c) => {
  const body = await parseJson(c, z.object({ embedding: embeddingSchema }))
  if (!body.ok) return body.response
  const device = c.get('device')

  const templates = await query<TemplateFacial>(
    `select t.colaborador_id,col.matricula,col.nome,col.setor,col.turno,
            t.template_cifrado,t.iv,t.auth_tag,t.dimensao
     from templates_faciais t
     join colaboradores col on col.id=t.colaborador_id
     where col.ativo=true`,
  )

  const candidatos: Candidato[] = []
  for (const template of templates.rows) {
    if (template.dimensao !== body.data.embedding.length) continue
    try {
      const cadastrado = decryptEmbedding(template.template_cifrado, template.iv, template.auth_tag)
      candidatos.push({ ...template, score: cosineSimilarity(cadastrado, body.data.embedding) })
    } catch (error) {
      console.error(`Falha ao ler template facial do colaborador ${template.colaborador_id}.`, error)
    }
  }

  candidatos.sort((a, b) => b.score - a.score)
  const melhor = candidatos[0]
  const segundo = candidatos[1]

  if (!melhor || melhor.score < config.faceThreshold) {
    return c.json({
      reconhecido: false,
      motivo: 'SEM_CORRESPONDENCIA',
      mensagem: 'Não foi possível reconhecer você. Posicione o rosto novamente.',
    })
  }

  if (segundo && melhor.score - segundo.score < config.faceIdentificationMargin) {
    return c.json({
      reconhecido: false,
      motivo: 'CORRESPONDENCIA_AMBIGUA',
      mensagem: 'Não foi possível confirmar sua identidade com segurança. Tente novamente.',
    })
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
    [melhor.colaborador_id, config.appTimezone],
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
    [newId(), melhor.colaborador_id, device.id, hashToken(verificacaoToken), melhor.score, config.verificationTtlSeconds],
  )

  const aberta = pausaAberta.rows[0]
  const regra = regraAtual.rows[0]
  return c.json({
    reconhecido: true,
    score: Number(melhor.score.toFixed(4)),
    verificacaoToken,
    expiraEmSegundos: config.verificationTtlSeconds,
    colaborador: {
      id: melhor.colaborador_id,
      matricula: melhor.matricula,
      nome: melhor.nome,
      setor: melhor.setor,
      turno: melhor.turno,
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

// Mantido por compatibilidade com versões anteriores do aplicativo.
pontoRoutes.post('/biometria/verificar', async (c) => {
  const body = await parseJson(c, z.object({ colaboradorId: uuidSchema, embedding: embeddingSchema }))
  if (!body.ok) return body.response
  const device = c.get('device')
  const result = await query<{ template_cifrado: Buffer; iv: Buffer; auth_tag: Buffer; dimensao: number }>(
    `select t.template_cifrado,t.iv,t.auth_tag,t.dimensao
     from templates_faciais t join colaboradores col on col.id=t.colaborador_id
     where t.colaborador_id=$1 and col.ativo=true limit 1`,
    [body.data.colaboradorId],
  )
  const stored = result.rows[0]
  if (!stored) return c.json({ erro: 'Biometria não cadastrada.' }, 404)
  if (stored.dimensao !== body.data.embedding.length) return c.json({ erro: 'Modelo biométrico incompatível.' }, 409)

  const score = cosineSimilarity(decryptEmbedding(stored.template_cifrado, stored.iv, stored.auth_tag), body.data.embedding)
  if (score < config.faceThreshold) return c.json({ reconhecido: false, score: Number(score.toFixed(4)) }, 401)

  const verificacaoToken = newToken()
  await query(
    `insert into verificacoes_faciais (id,colaborador_id,dispositivo_id,token_hash,score,expira_em)
     values ($1,$2,$3,$4,$5,now()+($6*interval '1 second'))`,
    [newId(), body.data.colaboradorId, device.id, hashToken(verificacaoToken), score, config.verificationTtlSeconds],
  )
  return c.json({ reconhecido: true, score: Number(score.toFixed(4)), verificacaoToken, expiraEmSegundos: config.verificationTtlSeconds })
})

pontoRoutes.post('/pausas/iniciar', async (c) => {
  const body = await parseJson(c, z.object({
    colaboradorId: uuidSchema,
    verificacaoToken: z.string().min(20),
    periodo: periodoSchema.optional(),
    codigoAutorizacao: z.string().regex(/^\d{6}$/).optional(),
  }))
  if (!body.ok) return body.response
  const device = c.get('device')

  try {
    const pausa = await transaction(async (client) => {
      const verification = await client.query<{ id: string }>(
        `select id from verificacoes_faciais where token_hash=$1 and colaborador_id=$2 and dispositivo_id=$3
         and usado_em is null and expira_em>now() for update`,
        [hashToken(body.data.verificacaoToken), body.data.colaboradorId, device.id],
      )
      if (!verification.rows[0]) throw new AppError('Verificação facial inválida ou expirada.', 401)

      const activeRule = await client.query<{ periodo: 'MANHA'|'TARDE'; limite_segundos: number }>(
        `select periodo,limite_segundos from regras_cafe where ativo=true
         and (now() at time zone $1)::time>=inicio and (now() at time zone $1)::time<fim
         order by inicio limit 1`, [config.appTimezone],
      )

      let periodo: 'MANHA'|'TARDE'
      let limiteSegundos: number
      let foraHorario = false
      let autorizacaoId: string | null = null

      if (activeRule.rows[0]) {
        periodo = activeRule.rows[0].periodo
        limiteSegundos = activeRule.rows[0].limite_segundos
      } else {
        foraHorario = true
        if (!body.data.periodo || !body.data.codigoAutorizacao) throw new AppError('Fora do horário permitido. Informe o período e o código do supervisor.', 403)
        periodo = body.data.periodo
        const configured = await client.query<{ limite_segundos: number }>('select limite_segundos from regras_cafe where periodo=$1 and ativo=true', [periodo])
        if (!configured.rows[0]) throw new AppError('Período indisponível.', 409)
        limiteSegundos = configured.rows[0].limite_segundos
        const authorization = await client.query<{ id: string }>(
          `select id from autorizacoes where colaborador_id=$1 and periodo=$2 and codigo_hash=$3
           and usado_em is null and cancelada_em is null and expira_em>now()
           order by criado_em desc limit 1 for update`,
          [body.data.colaboradorId, periodo, hashAuthorizationCode(body.data.codigoAutorizacao)],
        )
        if (!authorization.rows[0]) throw new AppError('Código de autorização inválido ou expirado.', 403)
        autorizacaoId = authorization.rows[0].id
        await client.query('update autorizacoes set usado_em=now() where id=$1', [autorizacaoId])
      }

      const id = newId()
      try {
        const inserted = await client.query<{ inicio_em: string }>(
          `insert into pausas_cafe (id,colaborador_id,periodo,limite_segundos,fora_horario,autorizacao_id,dispositivo_inicio_id,verificacao_inicio_id)
           values ($1,$2,$3,$4,$5,$6,$7,$8) returning inicio_em::text`,
          [id, body.data.colaboradorId, periodo, limiteSegundos, foraHorario, autorizacaoId, device.id, verification.rows[0].id],
        )
        const horario = await client.query<{ inicio_local: string; retorno_local: string }>(
          `select to_char($1::timestamptz at time zone $3,'HH24:MI') as inicio_local,
                  to_char(($1::timestamptz + ($2 * interval '1 second')) at time zone $3,'HH24:MI') as retorno_local`,
          [inserted.rows[0]?.inicio_em, limiteSegundos, config.appTimezone],
        )
        await client.query('update verificacoes_faciais set usado_em=now() where id=$1', [verification.rows[0].id])
        return {
          id,
          periodo,
          limiteSegundos,
          foraHorario,
          inicioEm: inserted.rows[0]?.inicio_em,
          inicioLocal: horario.rows[0]?.inicio_local,
          retornoAteLocal: horario.rows[0]?.retorno_local,
        }
      } catch (error: any) {
        if (error?.code === '23505') throw new AppError('Este colaborador já registrou esta pausa hoje ou possui uma pausa aberta.', 409)
        throw error
      }
    })
    return c.json(pausa, 201)
  } catch (error) {
    if (error instanceof AppError) return c.json({ erro: error.message }, error.status)
    throw error
  }
})

pontoRoutes.post('/pausas/finalizar', async (c) => {
  const body = await parseJson(c, z.object({ colaboradorId: uuidSchema, verificacaoToken: z.string().min(20) }))
  if (!body.ok) return body.response
  const device = c.get('device')

  try {
    const pausa = await transaction(async (client) => {
      const verification = await client.query<{ id: string }>(
        `select id from verificacoes_faciais where token_hash=$1 and colaborador_id=$2 and dispositivo_id=$3
         and usado_em is null and expira_em>now() for update`,
        [hashToken(body.data.verificacaoToken), body.data.colaboradorId, device.id],
      )
      if (!verification.rows[0]) throw new AppError('Verificação facial inválida ou expirada.', 401)
      const open = await client.query<{ id:string; inicio_em:string; limite_segundos:number }>(
        `select id,inicio_em::text,limite_segundos from pausas_cafe
         where colaborador_id=$1 and fim_em is null order by inicio_em desc limit 1 for update`,
        [body.data.colaboradorId],
      )
      if (!open.rows[0]) throw new AppError('Nenhuma pausa aberta para este colaborador.', 404)
      const finished = await client.query<{ fim_em:string; duracao_segundos:number }>(
        `update pausas_cafe set fim_em=now(),dispositivo_fim_id=$2,verificacao_fim_id=$3 where id=$1
         returning fim_em::text,floor(extract(epoch from (fim_em-inicio_em)))::int as duracao_segundos`,
        [open.rows[0].id, device.id, verification.rows[0].id],
      )
      const horario = await client.query<{ inicio_local: string; fim_local: string }>(
        `select to_char($1::timestamptz at time zone $3,'HH24:MI') as inicio_local,
                to_char($2::timestamptz at time zone $3,'HH24:MI') as fim_local`,
        [open.rows[0].inicio_em, finished.rows[0]?.fim_em, config.appTimezone],
      )
      await client.query('update verificacoes_faciais set usado_em=now() where id=$1', [verification.rows[0].id])
      const row = finished.rows[0]!
      return {
        id: open.rows[0].id,
        inicioLocal: horario.rows[0]?.inicio_local,
        fimEm: row.fim_em,
        fimLocal: horario.rows[0]?.fim_local,
        duracaoSegundos: row.duracao_segundos,
        limiteSegundos: open.rows[0].limite_segundos,
        excedeuLimite: row.duracao_segundos > open.rows[0].limite_segundos,
      }
    })
    return c.json(pausa)
  } catch (error) {
    if (error instanceof AppError) return c.json({ erro: error.message }, error.status)
    throw error
  }
})
