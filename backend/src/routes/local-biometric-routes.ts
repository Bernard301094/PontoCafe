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
  id: string
  tipo: string
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

type ConfirmationTemplateRow = {
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

export const localBiometricRoutes = new Hono<AppEnv>()
localBiometricRoutes.use('*', requireDevice)

localBiometricRoutes.get('/biometria/catalogo', async (c) => {
  const modelo = c.req.query('modelo')?.trim() ?? ''
  const versaoModelo = c.req.query('versaoModelo')?.trim() ?? ''
  const versaoAtual = c.req.query('versaoAtual')?.trim() ?? ''

  if (modelo.length < 2 || modelo.length > 100 || versaoModelo.length < 1 || versaoModelo.length > 50) {
    return c.json({ erro: 'Modelo facial inválido.' }, 400)
  }

  // `tipo` foi acrescentado pela migração de múltiplas aparências, mas não é
  // necessário para comparar embeddings. A leitura pelo JSON da linha devolve
  // null quando a coluna ainda não existe, evitando derrubar todo o catálogo de
  // instalações legadas com PostgreSQL 42703 e preservando o rótulo quando a
  // migração já foi aplicada.
  const metadata = await query<{ versao: string }>(
    `select md5(coalesce(string_agg(
       col.id::text || ':' || coalesce(col.matricula,'') || ':' || col.nome || ':' ||
       coalesce(col.setor,'') || ':' || coalesce(col.turno,'') || ':' ||
       t.id::text || ':' || coalesce(to_jsonb(t)->>'tipo','LEGADO') || ':' || t.atualizado_em::text,
       '|' order by col.id,t.id
     ),'')) as versao
     from templates_faciais t
     join colaboradores col on col.id=t.colaborador_id
     where col.ativo=true and t.modelo=$1 and t.versao_modelo=$2`,
    [modelo, versaoModelo],
  )
  const versao = metadata.rows[0]?.versao ?? 'd41d8cd98f00b204e9800998ecf8427e'

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
    `select t.id,coalesce(to_jsonb(t)->>'tipo','LEGADO') as tipo,t.colaborador_id,
            col.matricula,col.nome,col.setor,col.turno,
            t.template_cifrado,t.iv,t.auth_tag,t.dimensao,t.modelo,t.versao_modelo,
            t.atualizado_em::text
     from templates_faciais t
     join colaboradores col on col.id=t.colaborador_id
     where col.ativo=true and t.modelo=$1 and t.versao_modelo=$2
     order by col.nome,t.atualizado_em desc,t.id`,
    [modelo, versaoModelo],
  )

  const templates = result.rows.flatMap((row) => {
    try {
      const embedding = decryptEmbedding(row.template_cifrado, row.iv, row.auth_tag)
      if (embedding.length !== row.dimensao) return []
      return [{
        templateId: row.id,
        tipo: row.tipo,
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
        templateId: row.id,
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
  const result = await query<ConfirmationTemplateRow>(
    `select t.colaborador_id,col.matricula,col.nome,col.setor,col.turno,
            t.template_cifrado,t.iv,t.auth_tag,t.dimensao
     from templates_faciais t
     join colaboradores col on col.id=t.colaborador_id
     where t.colaborador_id=$1 and col.ativo=true and t.modelo=$2 and t.versao_modelo=$3
     order by t.atualizado_em desc,t.criado_em desc`,
    [body.data.colaboradorId, body.data.modelo, body.data.versaoModelo],
  )

  const stored = result.rows[0]
  if (!stored) return c.json({ erro: 'Biometria não cadastrada ou modelo incompatível.' }, 404)

  let score = -1
  let compatibleTemplates = 0
  for (const template of result.rows) {
    if (template.dimensao !== body.data.embedding.length) continue
    try {
      const cadastrado = decryptEmbedding(template.template_cifrado, template.iv, template.auth_tag)
      if (cadastrado.length !== template.dimensao) continue
      compatibleTemplates += 1
      score = Math.max(score, cosineSimilarity(cadastrado, body.data.embedding))
    } catch (error) {
      console.error(JSON.stringify({
        evento: 'template_ignorado_na_confirmacao_local',
        colaboradorId: template.colaborador_id,
        erro: error instanceof Error ? error.message : 'erro desconhecido',
      }))
    }
  }

  if (compatibleTemplates === 0) {
    return c.json({ erro: 'Modelo biométrico incompatível.' }, 409)
  }
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

  const aberta = pausaAberta.rows[0]
  const regra = regraAtual.rows[0]

  // O estado diário é decidido antes de horário/autorização. Isso impede que
  // alguém que já consumiu MANHA + TARDE receba apenas "fora do horário".
  const pausasConcluidasHoje = !aberta
    ? (await query<PausaUtilizadaRow>(
        `select p.id,p.periodo,
                to_char(p.inicio_em at time zone $2,'HH24:MI') as inicio_local,
                to_char(p.fim_em at time zone $2,'HH24:MI') as fim_local,
                floor(extract(epoch from (p.fim_em-p.inicio_em)))::int as duracao_segundos,
                p.limite_segundos
           from pausas_cafe p
          where p.colaborador_id=$1
            and p.fim_em is not null
            and (p.inicio_em at time zone $2)::date=(now() at time zone $2)::date
          order by p.inicio_em desc`,
        [stored.colaborador_id, config.appTimezone],
      )).rows
    : []

  const pausaManha = pausasConcluidasHoje.find((p) => p.periodo === 'MANHA')
  const pausaTarde = pausasConcluidasHoje.find((p) => p.periodo === 'TARDE')

  const verificacaoToken = newToken()
  await query(
    `insert into verificacoes_faciais (id,colaborador_id,dispositivo_id,token_hash,score,expira_em)
     values ($1,$2,$3,$4,$5,now()+($6*interval '1 second'))`,
    [newId(), stored.colaborador_id, device.id, hashToken(verificacaoToken), score, config.verificationTtlSeconds],
  )

  if (!aberta && pausaManha && pausaTarde) {
    await query(
      `insert into auditoria (ator_tipo,acao,entidade,entidade_id,detalhes)
       values ('DISPOSITIVO','TENTATIVA_PONTO_REPETIDA','PAUSA',$1,$2::jsonb)`,
      [pausaTarde.id, JSON.stringify({
        colaboradorId: stored.colaborador_id,
        colaboradorNome: stored.nome,
        dispositivoId: device.id,
        dispositivoNome: device.nome,
        tentativaEm: new Date().toISOString(),
        origem: 'ONLINE',
        motivo: 'PAUSAS_DO_DIA_JA_UTILIZADAS',
        pausasUtilizadas: ['MANHA', 'TARDE'],
        manha: { inicioLocal: pausaManha.inicio_local, fimLocal: pausaManha.fim_local },
        tarde: { inicioLocal: pausaTarde.inicio_local, fimLocal: pausaTarde.fim_local },
        score: Number(score.toFixed(4)),
      })],
    )

    return c.json({
      reconhecido: true,
      motivo: 'PAUSAS_DO_DIA_JA_UTILIZADAS',
      mensagem: `Pausas de hoje já utilizadas (2/2). Manhã: ${pausaManha.inicio_local}–${pausaManha.fim_local} · Tarde: ${pausaTarde.inicio_local}–${pausaTarde.fim_local}. Não há mais pausa disponível para hoje.`,
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
      acaoSugerida: 'BLOQUEADO',
      pausaAberta: null,
      dentroHorario: false,
      periodoAtual: null,
      limiteSegundos: null,
    })
  }

  // O fluxo atual usa liberação prévia: o Supervisor seleciona a pessoa e o
  // motivo no próprio perfil. Nenhum código é exibido ou digitado no Ponto.
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
        [stored.colaborador_id],
      )).rows[0]
    : undefined

  // Sem janela ativa nem liberação, usamos a regra mais próxima apenas para
  // identificar corretamente uma tentativa repetida (ex.: 17:04 -> TARDE).
  const regraReferencia = !aberta && !regra && !liberacao
    ? (await query<{ periodo: 'MANHA' | 'TARDE'; limite_segundos: number }>(
        `select periodo,limite_segundos
           from regras_cafe
          where ativo=true
          order by case
            when (now() at time zone $1)::time < inicio
              then extract(epoch from (inicio - (now() at time zone $1)::time))
            when (now() at time zone $1)::time >= fim
              then extract(epoch from ((now() at time zone $1)::time - fim))
            else 0
          end asc,
          inicio asc
          limit 1`,
        [config.appTimezone],
      )).rows[0]
    : undefined

  const periodoPretendido = regra?.periodo ?? liberacao?.periodo ?? regraReferencia?.periodo
  const pausaUtilizada = periodoPretendido
    ? pausasConcluidasHoje.find((p) => p.periodo === periodoPretendido)
    : undefined

  if (pausaUtilizada) {
    await query(
      `insert into auditoria (ator_tipo,acao,entidade,entidade_id,detalhes)
       values ('DISPOSITIVO','TENTATIVA_PONTO_REPETIDA','PAUSA',$1,$2::jsonb)`,
      [pausaUtilizada.id, JSON.stringify({
        colaboradorId: stored.colaborador_id,
        colaboradorNome: stored.nome,
        dispositivoId: device.id,
        dispositivoNome: device.nome,
        periodo: pausaUtilizada.periodo,
        tentativaEm: new Date().toISOString(),
        origem: 'ONLINE',
        motivo: 'PAUSA_PERIODO_JA_UTILIZADA',
        inicioLocal: pausaUtilizada.inicio_local,
        fimLocal: pausaUtilizada.fim_local,
        duracaoSegundos: pausaUtilizada.duracao_segundos,
        limiteSegundos: pausaUtilizada.limite_segundos,
        score: Number(score.toFixed(4)),
      })],
    )

    return c.json({
      reconhecido: true,
      motivo: 'PAUSA_PERIODO_JA_UTILIZADA',
      mensagem: `Pausa da ${periodoLabel(pausaUtilizada.periodo)} já utilizada hoje. Saída: ${pausaUtilizada.inicio_local} · Retorno: ${pausaUtilizada.fim_local} · Duração: ${duracaoLabel(pausaUtilizada.duracao_segundos)}.`,
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
      acaoSugerida: 'BLOQUEADO',
      pausaAberta: null,
      dentroHorario: false,
      periodoAtual: pausaUtilizada.periodo,
      limiteSegundos: pausaUtilizada.limite_segundos,
    })
  }

  const foraHorarioSemPausaAberta = !aberta && !regra
  const autorizadoForaHorario = foraHorarioSemPausaAberta && Boolean(liberacao)

  // Sem liberação prévia, o caso é terminal e nenhum registro é iniciado.
  if (foraHorarioSemPausaAberta && !autorizadoForaHorario) {
    return c.json({
      reconhecido: true,
      motivo: 'FORA_HORARIO',
      mensagem: 'Fora do horário permitido. Solicite uma liberação prévia ao Supervisor.',
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
      acaoSugerida: 'BLOQUEADO',
      pausaAberta: null,
      dentroHorario: false,
      periodoAtual: regraReferencia?.periodo ?? null,
      limiteSegundos: regraReferencia?.limite_segundos ?? null,
    })
  }

  return c.json({
    reconhecido: true,
    motivo: autorizadoForaHorario ? 'AUTORIZACAO_PREVIA' : null,
    mensagem: autorizadoForaHorario ? 'Pausa liberada previamente pelo Supervisor.' : null,
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
    dentroHorario: Boolean(regra) || autorizadoForaHorario,
    periodoAtual: aberta?.periodo ?? regra?.periodo ?? liberacao?.periodo ?? null,
    limiteSegundos: aberta?.limite_segundos ?? regra?.limite_segundos ?? liberacao?.limite_segundos ?? null,
  })
})
