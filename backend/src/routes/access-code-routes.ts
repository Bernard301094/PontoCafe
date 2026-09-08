import { Hono } from 'hono'
import { z } from 'zod'
import { requireRole, requireUser, type AppEnv } from '../auth-runtime.js'
import { config } from '../config.js'
import { query, transaction } from '../db.js'
import { formatAccessCode, generateAccessCode } from '../domain/access-code.js'
import { newId } from '../security.js'
import { parseJson, uuidSchema } from './shared.js'

export const accessCodeRoutes = new Hono<AppEnv>()
accessCodeRoutes.use('*', requireUser, requireRole('ADMIN', 'SUPERVISOR'))

const CODE_GENERATION_ATTEMPTS = 6

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
    estado: codeState(row),
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
         p.limite_segundos as "limiteSegundos",
         p.carencia_segundos as "carenciaSegundos"
    from codigos_acesso ca
    join colaboradores col on col.id = ca.colaborador_id
    left join pausas_cafe p on p.id = ca.pausa_id
   where ca.cancelado_em is null
     and ca.retorno_em is null`

accessCodeRoutes.get('/codigos', async (c) => {
  const result = await query<LiveCodeRow>(`${LIVE_CODE_QUERY} order by ca.criado_em desc limit 200`)
  return c.json({
    codigos: result.rows.map(present),
    validadeSegundos: config.accessCodeTtlSeconds,
    carenciaSegundos: config.coffeeGraceSeconds,
  })
})

accessCodeRoutes.post('/codigos', async (c) => {
  const body = await parseJson(c, z.object({
    colaboradorId: uuidSchema,
    motivo: z.string().trim().min(2).max(300).optional(),
  }))
  if (!body.ok) return body.response

  const user = c.get('user')

  const created = await transaction(async (client) => {
    const collaborator = (await client.query<{ id: string; nome: string; setor: string | null; turno: string | null }>(
      'select id,nome,setor,turno from colaboradores where id=$1 and ativo=true for update',
      [body.data.colaboradorId],
    )).rows[0]
    if (!collaborator) return { erro: 'COLABORADOR_INVALIDO' as const }

    // Quem já saiu continua a precisar do código que levou. Emitir outro aqui
    // criaria dois códigos vivos para a mesma pessoa e deixaria em aberto qual
    // deles fecha a pausa.
    const inUse = await client.query<{ id: string; codigo: string }>(
      `select id,codigo from codigos_acesso
        where colaborador_id=$1 and cancelado_em is null
          and saida_em is not null and retorno_em is null
        limit 1 for update`,
      [body.data.colaboradorId],
    )
    if (inUse.rows[0]) {
      return { erro: 'EM_PAUSA' as const, codigo: inUse.rows[0].codigo }
    }

    // Uma pessoa só pode ter um código pendente por vez: o novo substitui o
    // anterior, que deixa de valer no mesmo instante.
    await client.query(
      `update codigos_acesso set cancelado_em=now()
        where colaborador_id=$1 and cancelado_em is null
          and saida_em is null and retorno_em is null`,
      [body.data.colaboradorId],
    )

    const id = newId()
    let codigo: string | null = null
    for (let attempt = 0; attempt < CODE_GENERATION_ATTEMPTS; attempt++) {
      const candidate = generateAccessCode()
      try {
        await client.query(
          `insert into codigos_acesso
             (id,colaborador_id,codigo,emitido_por_auth_id,emitido_por_nome,emitido_por_tipo,motivo,expira_em)
           values ($1,$2,$3,$4,$5,$6,$7,now()+($8*interval '1 second'))`,
          [
            id,
            body.data.colaboradorId,
            candidate,
            user.id,
            user.nome ?? null,
            user.papel,
            body.data.motivo ?? null,
            config.accessCodeTtlSeconds,
          ],
        )
        codigo = candidate
        break
      } catch (error: unknown) {
        // 23505 aqui só pode ser a colisão do índice ux_codigo_acesso_vivo:
        // este colaborador já tem um código vivo com a mesma string. Sortear de
        // novo é a correção certa; qualquer outro erro tem de subir.
        if (typeof error === 'object' && error !== null && (error as { code?: unknown }).code === '23505') continue
        throw error
      }
    }
    if (!codigo) return { erro: 'COLISAO' as const }

    await client.query(
      `insert into auditoria (ator_auth_id,ator_tipo,acao,entidade,entidade_id,detalhes)
       values ($1,$2,'EMITIR_CODIGO_ACESSO','CODIGO_ACESSO',$3,$4::jsonb)`,
      [user.id, user.papel, id, JSON.stringify({
        colaboradorId: collaborator.id,
        colaboradorNome: collaborator.nome,
        motivo: body.data.motivo ?? null,
        validadeSegundos: config.accessCodeTtlSeconds,
      })],
    )

    const stored = (await client.query<{ expira_em: string; criado_em: string }>(
      'select expira_em::text,criado_em::text from codigos_acesso where id=$1',
      [id],
    )).rows[0]!

    return {
      ok: true as const,
      id,
      codigo,
      collaborator,
      expiraEm: stored.expira_em,
      criadoEm: stored.criado_em,
    }
  })

  if ('erro' in created) {
    if (created.erro === 'COLABORADOR_INVALIDO') {
      return c.json({ erro: 'Colaborador não encontrado ou inativo.', codigo: 'COLABORADOR_INVALIDO' }, 404)
    }
    if (created.erro === 'EM_PAUSA') {
      return c.json({
        erro: 'Esta pessoa está em pausa. O código que ela levou continua válido para o retorno.',
        codigo: 'EM_PAUSA',
        codigoAtivo: created.codigo,
      }, 409)
    }
    return c.json({
      erro: 'Não foi possível sortear um código livre. Tente novamente.',
      codigo: 'COLISAO_CODIGO',
    }, 503)
  }

  const rule = (await query<{ limite_segundos: number }>(
    `select limite_segundos from regras_cafe
      where ativo=true
        and (now() at time zone $1)::time>=inicio
        and (now() at time zone $1)::time<fim
      order by inicio limit 1`,
    [config.appTimezone],
  )).rows[0]

  return c.json({
    id: created.id,
    codigo: created.codigo,
    codigoFormatado: formatAccessCode(created.codigo),
    colaboradorId: created.collaborator.id,
    colaboradorNome: created.collaborator.nome,
    setor: created.collaborator.setor,
    turno: created.collaborator.turno,
    criadoEm: created.criadoEm,
    expiraEm: created.expiraEm,
    expiraEmSegundos: config.accessCodeTtlSeconds,
    carenciaSegundos: config.coffeeGraceSeconds,
    limiteSegundos: rule?.limite_segundos ?? null,
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
