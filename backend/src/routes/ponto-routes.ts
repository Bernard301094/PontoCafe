import { Hono } from 'hono'
import { createMiddleware } from 'hono/factory'
import { z } from 'zod'
import type { AppEnv, Device } from '../auth-runtime.js'
import { evaluateBiometricIdentification, validateBiometricVector } from '../biometric-matching.js'
import { config } from '../config.js'
import { query, transaction } from '../db.js'
import { cosineSimilarity, decryptEmbedding, hashToken, newId, newToken } from '../security.js'
import { embeddingSchema, parseJson, uuidSchema } from './shared.js'

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
  constructor(
    message: string,
    readonly status: 401 | 403 | 404 | 409,
    readonly details?: { pauseId?: string; periodo?: 'MANHA' | 'TARDE' },
  ) {
    super(message)
  }
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

type PausaUtilizadaRow = {
  id: string
  periodo: 'MANHA' | 'TARDE'
  inicio_local: string
  fim_local: string
  duracao_segundos: number
  limite_segundos: number
}

function periodoLabel(periodo: 'MANHA' | 'TARDE'): string {
  return periodo === 'MANHA' ? 'manhã' : 'tarde'
}

function duracaoLabel(segundos: number): string {
  const minutos = Math.floor(segundos / 60)
  const restante = segundos % 60
  if (minutos <= 0) return `${restante} s`
  return restante > 0 ? `${minutos} min ${restante} s` : `${minutos} min`
}

async function auditRepeatedAttempt(params: {
  colaboradorId: string
  colaboradorNome?: string | null
  device: Device
  pauseId?: string | null
  periodo?: 'MANHA' | 'TARDE' | null
  origem: 'ONLINE_LEGACY_IDENTIFICAR' | 'ONLINE_INICIAR'
  score?: number | null
}) {
  await query(
    `insert into auditoria (ator_tipo,acao,entidade,entidade_id,detalhes)
     values ('DISPOSITIVO','TENTATIVA_PONTO_REPETIDA','PAUSA',$1,$2::jsonb)`,
    [params.pauseId ?? null, JSON.stringify({
      colaboradorId: params.colaboradorId,
      colaboradorNome: params.colaboradorNome ?? null,
      dispositivoId: params.device.id,
      dispositivoNome: params.device.nome,
      periodo: params.periodo ?? null,
      tentativaEm: new Date().toISOString(),
      origem: params.origem,
      motivo: 'PAUSA_PERIODO_JA_UTILIZADA',
      score: params.score == null ? null : Number(params.score.toFixed(4)),
    })],
  )
}

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
  if (!validateBiometricVector(body.data.embedding).valid) {
    return c.json({ erro: 'Embedding facial inválido ou incompatível com o modelo.' }, 400)
  }
  const device = c.get('device')

  const templates = await query<TemplateFacial>(
    `select t.colaborador_id,col.matricula,col.nome,col.setor,col.turno,
            t.template_cifrado,t.iv,t.auth_tag,t.dimensao
     from templates_faciais t
     join colaboradores col on col.id=t.colaborador_id
     where col.ativo=true`,
  )

  // Primeiro escolhe o melhor template de cada pessoa. Templates da mesma
  // identidade podem representar touca, óculos, sem acessórios etc. e não podem
  // ocupar simultaneamente o primeiro e o segundo lugar da margem de segurança.
  const bestByCollaborator = new Map<string, Candidato>()
  for (const template of templates.rows) {
    if (template.dimensao !== body.data.embedding.length) continue
    try {
      const cadastrado = decryptEmbedding(template.template_cifrado, template.iv, template.auth_tag)
      if (!validateBiometricVector(cadastrado).valid) continue
      const candidate = { ...template, score: cosineSimilarity(cadastrado, body.data.embedding) }
      const current = bestByCollaborator.get(template.colaborador_id)
      if (!current || candidate.score > current.score) {
        bestByCollaborator.set(template.colaborador_id, candidate)
      }
    } catch (error) {
      console.error(`Falha ao ler template facial do colaborador ${template.colaborador_id}.`, error)
    }
  }

  const candidatos = [...bestByCollaborator.values()].sort((a, b) => b.score - a.score)
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

  const aberta = pausaAberta.rows[0]
  const regra = regraAtual.rows[0]
  const liberacao = !aberta && !regra
    ? (await query<{ periodo: 'MANHA' | 'TARDE'; limite_segundos: number; expira_em: string }>(
        `select a.periodo,r.limite_segundos,a.expira_em::text
         from autorizacoes a
         join regras_cafe r on r.periodo=a.periodo and r.ativo=true
         where a.colaborador_id=$1
           and a.usado_em is null
           and a.cancelada_em is null
           and a.expira_em>now()
         order by a.criado_em desc
         limit 1`,
        [melhor.colaborador_id],
      )).rows[0]
    : undefined

  const periodoPretendido = regra?.periodo ?? liberacao?.periodo
  const pausaUtilizada = !aberta && periodoPretendido
    ? (await query<PausaUtilizadaRow>(
        `select p.id,p.periodo,
                to_char(p.inicio_em at time zone $3,'HH24:MI') as inicio_local,
                to_char(p.fim_em at time zone $3,'HH24:MI') as fim_local,
                floor(extract(epoch from (p.fim_em-p.inicio_em)))::int as duracao_segundos,
                p.limite_segundos
           from pausas_cafe p
          where p.colaborador_id=$1
            and p.periodo=$2
            and p.fim_em is not null
            and (p.inicio_em at time zone $3)::date=(now() at time zone $3)::date
          order by p.inicio_em desc limit 1`,
        [melhor.colaborador_id, periodoPretendido, config.appTimezone],
      )).rows[0]
    : undefined

  const verificacaoToken = newToken()
  await query(
    `insert into verificacoes_faciais (id,colaborador_id,dispositivo_id,token_hash,score,expira_em)
     values ($1,$2,$3,$4,$5,now()+($6*interval '1 second'))`,
    [newId(), melhor.colaborador_id, device.id, hashToken(verificacaoToken), melhor.score, config.verificationTtlSeconds],
  )

  if (pausaUtilizada) {
    await auditRepeatedAttempt({
      colaboradorId: melhor.colaborador_id,
      colaboradorNome: melhor.nome,
      device,
      pauseId: pausaUtilizada.id,
      periodo: pausaUtilizada.periodo,
      origem: 'ONLINE_LEGACY_IDENTIFICAR',
      score: melhor.score,
    })
    return c.json({
      reconhecido: true,
      motivo: 'PAUSA_PERIODO_JA_UTILIZADA',
      mensagem: `Pausa da ${periodoLabel(pausaUtilizada.periodo)} já utilizada hoje. Saída: ${pausaUtilizada.inicio_local} · Retorno: ${pausaUtilizada.fim_local} · Duração: ${duracaoLabel(pausaUtilizada.duracao_segundos)}. Esta nova tentativa de bater o ponto foi registrada.`,
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
      acaoSugerida: 'BLOQUEADO',
      pausaAberta: null,
      dentroHorario: false,
      periodoAtual: pausaUtilizada.periodo,
      limiteSegundos: pausaUtilizada.limite_segundos,
    })
  }

  const foraHorarioSemPausaAberta = !aberta && !regra
  const autorizadoForaHorario = foraHorarioSemPausaAberta && Boolean(liberacao)

  return c.json({
    reconhecido: true,
    motivo: foraHorarioSemPausaAberta
      ? (autorizadoForaHorario ? 'AUTORIZACAO_PREVIA' : 'FORA_HORARIO_NAO_LIBERADO')
      : null,
    mensagem: foraHorarioSemPausaAberta
      ? (autorizadoForaHorario
          ? 'Pausa liberada previamente pelo Supervisor.'
          : 'Você está fora do horário permitido e não possui liberação prévia do Supervisor.')
      : null,
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
    acaoSugerida: aberta
      ? 'FINALIZAR'
      : (foraHorarioSemPausaAberta && !autorizadoForaHorario ? 'BLOQUEADO' : 'INICIAR'),
    pausaAberta: aberta ? {
      id: aberta.id,
      periodo: aberta.periodo,
      inicioEm: aberta.inicio_em,
      inicioLocal: aberta.inicio_local,
      limiteSegundos: aberta.limite_segundos,
      tempoDecorridoSegundos: aberta.tempo_decorrido_segundos,
    } : null,
    dentroHorario: Boolean(regra) || autorizadoForaHorario,
    periodoAtual: regra?.periodo ?? liberacao?.periodo ?? null,
    limiteSegundos: regra?.limite_segundos ?? liberacao?.limite_segundos ?? null,
  })
})

pontoRoutes.post('/biometria/verificar', async (c) => {
  const body = await parseJson(c, z.object({ colaboradorId: uuidSchema, embedding: embeddingSchema }))
  if (!body.ok) return body.response
  if (!validateBiometricVector(body.data.embedding).valid) {
    return c.json({ erro: 'Embedding facial inválido ou incompatível com o modelo.' }, 400)
  }
  const device = c.get('device')
  const result = await query<{ colaborador_id: string; template_cifrado: Buffer; iv: Buffer; auth_tag: Buffer; dimensao: number }>(
    `select t.colaborador_id,t.template_cifrado,t.iv,t.auth_tag,t.dimensao
     from templates_faciais t join colaboradores col on col.id=t.colaborador_id
     where col.ativo=true`,
  )
  if (!result.rows.some((row) => row.colaborador_id === body.data.colaboradorId)) {
    return c.json({ erro: 'Biometria não cadastrada.' }, 404)
  }

  const decrypted: Array<{ collaboratorId: string; embedding: number[]; payload: { colaborador_id: string } }> = []
  for (const stored of result.rows) {
    if (stored.dimensao !== body.data.embedding.length) continue
    try {
      const embedding = decryptEmbedding(stored.template_cifrado, stored.iv, stored.auth_tag)
      if (embedding.length !== stored.dimensao) continue
      decrypted.push({ collaboratorId: stored.colaborador_id, embedding, payload: stored })
    } catch (error) {
      console.error('Falha ao ler uma variante facial durante verificação.', error)
    }
  }
  if (decrypted.length === 0) return c.json({ erro: 'Modelo biométrico incompatível.' }, 409)
  const identification = evaluateBiometricIdentification(
    body.data.embedding,
    decrypted,
    config.faceThreshold,
    config.faceIdentificationMargin,
  )
  const score = identification.best?.score ?? -1
  if (!identification.accepted || identification.best?.collaboratorId !== body.data.colaboradorId) {
    return c.json({ reconhecido: false, score: Number(score.toFixed(4)) }, 401)
  }

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
        const authorization = await client.query<{
          id: string
          periodo: 'MANHA'|'TARDE'
          limite_segundos: number
        }>(
          `select a.id,a.periodo,r.limite_segundos
           from autorizacoes a
           join regras_cafe r on r.periodo=a.periodo and r.ativo=true
           where a.colaborador_id=$1
             and a.usado_em is null
             and a.cancelada_em is null
             and a.expira_em>now()
           order by a.criado_em desc
           limit 1 for update`,
          [body.data.colaboradorId],
        )
        const liberacao = authorization.rows[0]
        if (!liberacao) {
          throw new AppError(
            'Pausa não liberada. Você está fora do horário permitido. Solicite a liberação prévia ao Supervisor.',
            403,
          )
        }
        periodo = liberacao.periodo
        limiteSegundos = liberacao.limite_segundos
        autorizacaoId = liberacao.id
      }

      const alreadyUsed = await client.query<{ id: string }>(
        `select id from pausas_cafe
          where colaborador_id=$1 and periodo=$2
            and (inicio_em at time zone $3)::date=(now() at time zone $3)::date
          order by inicio_em desc limit 1 for update`,
        [body.data.colaboradorId, periodo, config.appTimezone],
      )
      if (alreadyUsed.rows[0]) {
        throw new AppError(
          'Este colaborador já registrou esta pausa hoje. A nova tentativa foi registrada para auditoria.',
          409,
          { pauseId: alreadyUsed.rows[0].id, periodo },
        )
      }

      const id = newId()
      try {
        const inserted = await client.query<{ inicio_em: string }>(
          `insert into pausas_cafe (id,colaborador_id,periodo,limite_segundos,fora_horario,autorizacao_id,dispositivo_inicio_id,verificacao_inicio_id)
           values ($1,$2,$3,$4,$5,$6,$7,$8) returning inicio_em::text`,
          [id, body.data.colaboradorId, periodo, limiteSegundos, foraHorario, autorizacaoId, device.id, verification.rows[0].id],
        )
        if (autorizacaoId) {
          const consumed = await client.query(
            `update autorizacoes
                set usado_em=now()
              where id=$1 and colaborador_id=$2
                and usado_em is null and cancelada_em is null
            returning id`,
            [autorizacaoId, body.data.colaboradorId],
          )
          if (consumed.rowCount !== 1) {
            throw new AppError('A liberação prévia expirou ou já foi utilizada.', 409, { periodo })
          }
        }
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
        if (error?.code === '23505') {
          throw new AppError('Este colaborador já registrou esta pausa hoje ou possui uma pausa aberta.', 409, { periodo })
        }
        throw error
      }
    })
    return c.json(pausa, 201)
  } catch (error) {
    if (error instanceof AppError) {
      if (error.status === 409) {
        await auditRepeatedAttempt({
          colaboradorId: body.data.colaboradorId,
          device,
          pauseId: error.details?.pauseId,
          periodo: error.details?.periodo,
          origem: 'ONLINE_INICIAR',
        })
      }
      return c.json({ erro: error.message }, error.status)
    }
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
