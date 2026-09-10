import { Hono } from 'hono'
import type { PoolClient } from 'pg'
import { z } from 'zod'
import { requireRole, requireUser, type AppEnv } from '../auth-runtime.js'
import { config } from '../config.js'
import { query, transaction } from '../db.js'
import { formatAccessCode, generateAccessCode } from '../domain/access-code.js'
import { buildQrPayload } from '../domain/qr-payload.js'
import { newId } from '../security.js'
import { parseJson, uuidSchema } from './shared.js'

export const accessCodeRoutes = new Hono<AppEnv>()
accessCodeRoutes.use('*', requireUser, requireRole('ADMIN', 'SUPERVISOR'))

const CODE_GENERATION_ATTEMPTS = 6

type Periodo = 'MANHA' | 'TARDE'

type LiveCodeRow = {
  id: string
  codigo: string
  colaboradorId: string
  nome: string
  setor: string | null
  turno: string | null
  emitidoPor: string | null
  motivo: string | null
  criadoEm: string
  expiraEm: string
  saidaEm: string | null
  expiraEmSegundos: number
  pausaId: string | null
  limiteSegundos: number | null
  carenciaSegundos: number | null
  periodo: Periodo | null
  diaOperacional: string | null
}

/**
 * `AGUARDANDO_SAIDA` — emitido e ainda não usado.
 * `EXPIRADO`         — a janela para sair acabou sem ninguém o apresentar.
 * `EM_PAUSA`         — já abriu a pausa e continua válido para o retorno.
 *
 * Um código EM_PAUSA nunca expira: negar o retorno a quem já saiu deixaria a
 * pausa aberta para sempre e obrigaria a um fecho manual.
 */
function codeState(row: LiveCodeRow): 'AGUARDANDO_SAIDA' | 'EXPIRADO' | 'EM_PAUSA' {
  if (row.saidaEm) return 'EM_PAUSA'
  return row.expiraEmSegundos > 0 ? 'AGUARDANDO_SAIDA' : 'EXPIRADO'
}

function present(row: LiveCodeRow) {
  return {
    id: row.id,
    codigo: row.codigo,
    codigoFormatado: formatAccessCode(row.codigo),
    // O mesmo segredo, em forma legível por câmara. Só sai por rotas de
    // Admin/Supervisor, como o próprio código -- nenhuma rota de dispositivo
    // devolve isto.
    qrPayload: buildQrPayload(row.colaboradorId, row.codigo),
    estado: codeState(row),
    periodo: row.periodo,
    diaOperacional: row.diaOperacional,
    colaboradorId: row.colaboradorId,
    nome: row.nome,
    setor: row.setor,
    turno: row.turno,
    emitidoPor: row.emitidoPor,
    motivo: row.motivo,
    criadoEm: row.criadoEm,
    expiraEm: row.expiraEm,
    expiraEmSegundos: Math.max(0, row.expiraEmSegundos),
    saidaEm: row.saidaEm,
    pausaId: row.pausaId,
    limiteSegundos: row.limiteSegundos,
    carenciaSegundos: row.carenciaSegundos,
  }
}

const LIVE_CODE_QUERY = `
  select ca.id,
         ca.codigo,
         ca.colaborador_id as "colaboradorId",
         col.nome,
         col.setor,
         col.turno,
         ca.emitido_por_nome as "emitidoPor",
         ca.motivo,
         ca.criado_em::text as "criadoEm",
         ca.expira_em::text as "expiraEm",
         ca.saida_em::text as "saidaEm",
         floor(extract(epoch from (ca.expira_em - now())))::int as "expiraEmSegundos",
         ca.pausa_id as "pausaId",
         ca.periodo,
         ca.dia_operacional::text as "diaOperacional",
         p.limite_segundos as "limiteSegundos",
         p.carencia_segundos as "carenciaSegundos"
    from codigos_acesso ca
    join colaboradores col on col.id = ca.colaborador_id
    left join pausas_cafe p on p.id = ca.pausa_id
   where ca.cancelado_em is null
     and ca.retorno_em is null`

/**
 * As janelas de café de hoje, já resolvidas em instantes absolutos.
 *
 * Um código de período vive até ao FIM da janela do seu período — é isso que
 * lhe permite estar no telemóvel da pessoa desde manhã. A conversão de
 * `(dia local + hora de fim)` para timestamptz é feita aqui, no banco, porque
 * é ele que conhece as regras de fuso; fazer a soma em JavaScript significaria
 * reimplementar horário de verão à mão.
 */
type Janela = {
  periodo: Periodo
  dia: string
  expiraEm: string
  jaPassou: boolean
  limiteSegundos: number
}

async function janelasDeHoje(client: Pick<PoolClient, 'query'>): Promise<Janela[]> {
  const result = await client.query<Janela>(
    `select periodo,
            (now() at time zone $1)::date::text as dia,
            (((now() at time zone $1)::date + fim) at time zone $1)::text as "expiraEm",
            ((((now() at time zone $1)::date + fim) at time zone $1) <= now()) as "jaPassou",
            limite_segundos as "limiteSegundos"
       from regras_cafe
      where ativo=true
      order by inicio`,
    [config.appTimezone],
  )
  return result.rows
}

type EmissaoOk = {
  ok: true
  id: string
  codigo: string
  periodo: Periodo
  dia: string
  expiraEm: string
  criadoEm: string
  limiteSegundos: number
}
type EmissaoErro = { ok: false; erro: 'EM_PAUSA' | 'JA_EMITIDO' | 'COLISAO'; codigo?: string }

/**
 * Emite (ou reemite) o código de um período para uma pessoa.
 *
 * Reemitir é deliberado e é o caso comum: quem perdeu o papel, apagou a
 * mensagem ou trocou de telemóvel precisa de um código novo, e o antigo tem de
 * deixar de valer no mesmo instante. O que NÃO se pode reemitir é o código de
 * quem já saiu com ele -- esse é a única forma de fechar a pausa aberta.
 */
async function emitirParaPeriodo(
  client: PoolClient,
  actor: { id: string; nome?: string | null; papel: string },
  colaboradorId: string,
  janela: Janela,
  motivo: string | null,
): Promise<EmissaoOk | EmissaoErro> {
  const emUso = await client.query<{ id: string; codigo: string }>(
    `select id,codigo from codigos_acesso
      where colaborador_id=$1 and cancelado_em is null
        and saida_em is not null and retorno_em is null
      limit 1 for update`,
    [colaboradorId],
  )
  if (emUso.rows[0]) return { ok: false, erro: 'EM_PAUSA', codigo: emUso.rows[0].codigo }

  // Só o código deste período e deste dia é substituído. O da tarde não pode
  // morrer porque se reemitiu o da manhã -- foi exactamente isso que a coluna
  // `periodo` veio permitir.
  await client.query(
    `update codigos_acesso set cancelado_em=now()
      where colaborador_id=$1 and cancelado_em is null
        and saida_em is null and retorno_em is null
        and periodo=$2 and dia_operacional=$3::date`,
    [colaboradorId, janela.periodo, janela.dia],
  )

  const id = newId()
  for (let tentativa = 0; tentativa < CODE_GENERATION_ATTEMPTS; tentativa++) {
    const candidato = generateAccessCode()
    try {
      const inserido = await client.query<{ criado_em: string; expira_em: string }>(
        `insert into codigos_acesso
           (id,colaborador_id,codigo,emitido_por_auth_id,emitido_por_nome,emitido_por_tipo,
            motivo,expira_em,periodo,dia_operacional)
         values ($1,$2,$3,$4,$5,$6,$7,$8::timestamptz,$9,$10::date)
         returning criado_em::text, expira_em::text`,
        [
          id,
          colaboradorId,
          candidato,
          actor.id,
          actor.nome ?? null,
          actor.papel,
          motivo,
          janela.expiraEm,
          janela.periodo,
          janela.dia,
        ],
      )
      const row = inserido.rows[0]!
      return {
        ok: true,
        id,
        codigo: candidato,
        periodo: janela.periodo,
        dia: janela.dia,
        expiraEm: row.expira_em,
        criadoEm: row.criado_em,
        limiteSegundos: janela.limiteSegundos,
      }
    } catch (error: unknown) {
      const falha = error as { code?: string; constraint?: string }
      if (falha.code !== '23505') throw error
      // Duas unicidades diferentes chegam aqui pelo mesmo código de erro, e a
      // resposta certa é oposta em cada uma. Sortear outra vez só corrige a
      // colisão da string; se já existe código deste período, sortear mil
      // vezes daria mil vezes o mesmo choque.
      if (falha.constraint === 'ux_codigo_acesso_periodo_dia') return { ok: false, erro: 'JA_EMITIDO' }
      continue
    }
  }
  return { ok: false, erro: 'COLISAO' }
}

async function auditarEmissao(
  client: PoolClient,
  actor: { id: string; papel: string },
  emissao: EmissaoOk,
  colaborador: { id: string; nome: string },
  motivo: string | null,
) {
  await client.query(
    `insert into auditoria (ator_auth_id,ator_tipo,acao,entidade,entidade_id,detalhes)
     values ($1,$2,'EMITIR_CODIGO_ACESSO','CODIGO_ACESSO',$3,$4::jsonb)`,
    [actor.id, actor.papel, emissao.id, JSON.stringify({
      colaboradorId: colaborador.id,
      colaboradorNome: colaborador.nome,
      motivo,
      periodo: emissao.periodo,
      diaOperacional: emissao.dia,
      expiraEm: emissao.expiraEm,
    })],
  )
}

accessCodeRoutes.get('/codigos', async (c) => {
  const result = await query<LiveCodeRow>(`${LIVE_CODE_QUERY} order by ca.criado_em desc limit 200`)
  return c.json({
    codigos: result.rows.map(present),
    validadeSegundos: config.accessCodeTtlSeconds,
    carenciaSegundos: config.coffeeGraceSeconds,
  })
})

/**
 * Os códigos do dia inteiro, de uma vez.
 *
 * É a rota da manhã: o Supervisor abre a operação e emite, para cada pessoa, o
 * código da MANHÃ e o da TARDE. Cada um serve para sair e para voltar da sua
 * pausa, e vale até ao fim da janela do seu período.
 *
 * Uma janela que já passou é saltada em vez de recusada. Emitir às 15h o
 * código da manhã produziria uma linha nascida vencida -- o banco recusa-a pela
 * restrição `expira_em > criado_em`, e mesmo que aceitasse ninguém a poderia
 * usar. O que o chamador recebe é a lista do que foi emitido e do que ficou de
 * fora, com o motivo.
 */
accessCodeRoutes.post('/codigos/dia', async (c) => {
  const body = await parseJson(c, z.object({
    colaboradorId: uuidSchema.optional(),
    motivo: z.string().trim().min(2).max(300).optional(),
  }))
  if (!body.ok) return body.response

  const actor = c.get('user')
  const motivo = body.data.motivo ?? null

  const resultado = await transaction(async (client) => {
    const janelas = await janelasDeHoje(client)
    if (janelas.length === 0) return { erro: 'SEM_REGRAS' as const }

    const alvos = body.data.colaboradorId
      ? (await client.query<{ id: string; nome: string }>(
          'select id,nome from colaboradores where id=$1 and ativo=true for update',
          [body.data.colaboradorId],
        )).rows
      : (await client.query<{ id: string; nome: string }>(
          'select id,nome from colaboradores where ativo=true order by nome for update',
        )).rows

    if (alvos.length === 0) return { erro: 'SEM_COLABORADOR' as const }

    const emitidos: Array<{
      colaboradorId: string
      colaboradorNome: string
      periodo: Periodo
      codigo: string
      codigoFormatado: string
      qrPayload: string
      expiraEm: string
      limiteSegundos: number
    }> = []
    const ignorados: Array<{ colaboradorNome: string; periodo: Periodo; motivo: string }> = []

    for (const janela of janelas) {
      if (janela.jaPassou) {
        ignorados.push({
          colaboradorNome: '—',
          periodo: janela.periodo,
          motivo: 'A janela deste período já terminou hoje.',
        })
        continue
      }
      for (const alvo of alvos) {
        const emissao = await emitirParaPeriodo(client, actor, alvo.id, janela, motivo)
        if (!emissao.ok) {
          ignorados.push({
            colaboradorNome: alvo.nome,
            periodo: janela.periodo,
            motivo: emissao.erro === 'EM_PAUSA'
              ? 'Está em pausa: o código que levou continua válido para o retorno.'
              : emissao.erro === 'JA_EMITIDO'
                ? 'Já existe código vivo deste período.'
                : 'Não foi possível sortear um código livre.',
          })
          continue
        }
        await auditarEmissao(client, actor, emissao, alvo, motivo)
        emitidos.push({
          colaboradorId: alvo.id,
          colaboradorNome: alvo.nome,
          periodo: emissao.periodo,
          codigo: emissao.codigo,
          codigoFormatado: formatAccessCode(emissao.codigo),
          qrPayload: buildQrPayload(alvo.id, emissao.codigo),
          expiraEm: emissao.expiraEm,
          limiteSegundos: emissao.limiteSegundos,
        })
      }
    }

    return { ok: true as const, emitidos, ignorados }
  })

  if ('erro' in resultado) {
    if (resultado.erro === 'SEM_REGRAS') {
      return c.json({
        erro: 'Nenhuma janela de café está ativa. Configure as regras antes de emitir os códigos do dia.',
        codigo: 'SEM_REGRAS',
      }, 409)
    }
    return c.json({ erro: 'Colaborador não encontrado ou inativo.', codigo: 'COLABORADOR_INVALIDO' }, 404)
  }

  return c.json({
    emitidos: resultado.emitidos,
    ignorados: resultado.ignorados,
    carenciaSegundos: config.coffeeGraceSeconds,
    aviso: 'Cada código serve para sair e para voltar da pausa do seu período, e vale até ao fim da janela dele.',
  }, 201)
})

accessCodeRoutes.post('/codigos', async (c) => {
  const body = await parseJson(c, z.object({
    colaboradorId: uuidSchema,
    motivo: z.string().trim().min(2).max(300).optional(),
  }))
  if (!body.ok) return body.response

  const actor = c.get('user')
  const motivo = body.data.motivo ?? null

  const created = await transaction(async (client) => {
    const collaborator = (await client.query<{ id: string; nome: string; setor: string | null; turno: string | null }>(
      'select id,nome,setor,turno from colaboradores where id=$1 and ativo=true for update',
      [body.data.colaboradorId],
    )).rows[0]
    if (!collaborator) return { erro: 'COLABORADOR_INVALIDO' as const }

    // Avulso continua a existir: é o código de quem chegou tarde, trocou de
    // turno, ou perdeu o do dia. Vai para a janela vigente -- a que contém a
    // hora de agora, e na falta dela a primeira que ainda não terminou.
    const janelas = await janelasDeHoje(client)
    const janela = janelas.find((j) => !j.jaPassou)
    if (!janela) return { erro: 'SEM_JANELA' as const }

    const emissao = await emitirParaPeriodo(client, actor, collaborator.id, janela, motivo)
    if (!emissao.ok) return { erro: emissao.erro, codigo: emissao.codigo }

    await auditarEmissao(client, actor, emissao, collaborator, motivo)
    return { ok: true as const, emissao, collaborator }
  })

  if ('erro' in created) {
    if (created.erro === 'COLABORADOR_INVALIDO') {
      return c.json({ erro: 'Colaborador não encontrado ou inativo.', codigo: 'COLABORADOR_INVALIDO' }, 404)
    }
    if (created.erro === 'SEM_JANELA') {
      return c.json({
        erro: 'Todas as janelas de café de hoje já terminaram. O código valeria vencido.',
        codigo: 'SEM_JANELA',
      }, 409)
    }
    if (created.erro === 'EM_PAUSA') {
      return c.json({
        erro: 'Esta pessoa está em pausa. O código que ela levou continua válido para o retorno.',
        codigo: 'EM_PAUSA',
        codigoAtivo: created.codigo,
      }, 409)
    }
    if (created.erro === 'JA_EMITIDO') {
      return c.json({
        erro: 'Já existe um código vivo deste período para esta pessoa.',
        codigo: 'JA_EMITIDO',
      }, 409)
    }
    return c.json({
      erro: 'Não foi possível sortear um código livre. Tente novamente.',
      codigo: 'COLISAO_CODIGO',
    }, 503)
  }

  const { emissao, collaborator } = created
  return c.json({
    id: emissao.id,
    codigo: emissao.codigo,
    codigoFormatado: formatAccessCode(emissao.codigo),
    qrPayload: buildQrPayload(collaborator.id, emissao.codigo),
    periodo: emissao.periodo,
    diaOperacional: emissao.dia,
    colaboradorId: collaborator.id,
    colaboradorNome: collaborator.nome,
    setor: collaborator.setor,
    turno: collaborator.turno,
    criadoEm: emissao.criadoEm,
    expiraEm: emissao.expiraEm,
    expiraEmSegundos: Math.max(0, Math.floor((new Date(emissao.expiraEm).getTime() - Date.now()) / 1000)),
    carenciaSegundos: config.coffeeGraceSeconds,
    limiteSegundos: emissao.limiteSegundos,
    usos: { saida: 1, retorno: 1 },
    aviso: 'A mesma pessoa usa este código para sair e para voltar. Ele não serve para mais ninguém.',
  }, 201)
})

accessCodeRoutes.post('/codigos/cancelar', async (c) => {
  const body = await parseJson(c, z.object({ colaboradorId: uuidSchema }))
  if (!body.ok) return body.response

  const user = c.get('user')
  const result = await transaction(async (client) => {
    const target = (await client.query<{ id: string; saida_em: string | null }>(
      `select id,saida_em::text from codigos_acesso
        where colaborador_id=$1 and cancelado_em is null and retorno_em is null
        order by criado_em desc limit 1 for update`,
      [body.data.colaboradorId],
    )).rows[0]
    if (!target) return { erro: 'NAO_ENCONTRADO' as const }
    if (target.saida_em) return { erro: 'EM_PAUSA' as const }

    await client.query('update codigos_acesso set cancelado_em=now() where id=$1', [target.id])
    await client.query(
      `insert into auditoria (ator_auth_id,ator_tipo,acao,entidade,entidade_id,detalhes)
       values ($1,$2,'CANCELAR_CODIGO_ACESSO','CODIGO_ACESSO',$3,$4::jsonb)`,
      [user.id, user.papel, target.id, JSON.stringify({ colaboradorId: body.data.colaboradorId })],
    )
    return { ok: true as const, id: target.id }
  })

  if ('erro' in result) {
    if (result.erro === 'EM_PAUSA') {
      return c.json({
        erro: 'Esta pessoa já saiu com o código. Cancelá-lo agora deixaria a pausa sem como ser fechada.',
        codigo: 'EM_PAUSA',
      }, 409)
    }
    return c.json({
      erro: 'Não existe código pendente para cancelar. Ele pode ter expirado ou já ter sido usado.',
      codigo: 'NAO_ENCONTRADO',
    }, 404)
  }

  return c.json({ ok: true, cancelado: true, id: result.id })
})
