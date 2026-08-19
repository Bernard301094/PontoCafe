import { Hono } from 'hono'
import { z } from 'zod'
import { requireRole, requireUser, type AppEnv } from '../auth-runtime.js'
import { config } from '../config.js'
import { query, transaction } from '../db.js'
import { generateAuthorizationCode, hashAuthorizationCode, newId } from '../security.js'
import { parseJson, periodoSchema, uuidSchema } from './shared.js'

export const authorizationRoutes = new Hono<AppEnv>()
authorizationRoutes.use('*', requireUser, requireRole('ADMIN', 'SUPERVISOR'))

async function inferirPeriodoAtual(): Promise<'MANHA' | 'TARDE' | null> {
  const result = await query<{ periodo: 'MANHA' | 'TARDE' }>(
    `select periodo
     from regras_cafe
     where ativo=true
     order by
       case
         when (now() at time zone $1)::time >= inicio
          and (now() at time zone $1)::time < fim then 0
         when (now() at time zone $1)::time < inicio
           then extract(epoch from (inicio - (now() at time zone $1)::time))
         else extract(epoch from ((now() at time zone $1)::time - fim))
       end asc,
       inicio asc
     limit 1`,
    [config.appTimezone],
  )
  return result.rows[0]?.periodo ?? null
}

authorizationRoutes.post('/autorizacoes', async (c) => {
  const body = await parseJson(c, z.object({
    colaboradorId: uuidSchema,
    // Mantido opcional apenas para compatibilidade com APKs anteriores.
    // O servidor é a fonte de verdade e sempre calcula o período pela hora atual.
    periodo: periodoSchema.optional(),
    motivo: z.string().trim().min(2).max(300),
  }))
  if (!body.ok) return body.response

  const periodo = await inferirPeriodoAtual()
  if (!periodo) {
    return c.json({ erro: 'Não existe regra de café ativa para determinar o período automaticamente.' }, 409)
  }

  const user = c.get('user')
  // Mantemos um segredo interno apenas por compatibilidade com o schema atual.
  // O colaborador não precisa mais receber nem digitar código no Ponto.
  const segredoInterno = generateAuthorizationCode()
  const id = newId()

  const created = await transaction(async (client) => {
    const employee = await client.query<{ id: string; nome: string }>(
      'select id,nome from colaboradores where id=$1 and ativo=true for update',
      [body.data.colaboradorId],
    )
    const collaborator = employee.rows[0]
    if (!collaborator) return null

    // Uma pessoa só pode ter uma liberação prévia ativa por vez.
    await client.query(
      `update autorizacoes set cancelada_em=now()
       where colaborador_id=$1 and usado_em is null
         and cancelada_em is null and expira_em>now()`,
      [body.data.colaboradorId],
    )

    const inserted = await client.query<{ expira_em: string }>(
      `insert into autorizacoes (id,colaborador_id,supervisor_auth_id,periodo,codigo_hash,motivo,expira_em)
       values ($1,$2,$3,$4,$5,$6,now()+($7*interval '1 second'))
       returning expira_em::text`,
      [
        id,
        body.data.colaboradorId,
        user.id,
        periodo,
        hashAuthorizationCode(segredoInterno),
        body.data.motivo,
        config.authorizationTtlSeconds,
      ],
    )

    await client.query(
      `insert into auditoria (ator_auth_id,ator_tipo,acao,entidade,entidade_id,detalhes)
       values ($1,$2,'LIBERAR_PAUSA_FORA_HORARIO','AUTORIZACAO',$3,$4::jsonb)`,
      [
        user.id,
        user.papel,
        id,
        JSON.stringify({
          colaboradorId: collaborator.id,
          colaboradorNome: collaborator.nome,
          periodo,
          periodoDefinidoAutomaticamente: true,
          motivo: body.data.motivo,
          expiraEmSegundos: config.authorizationTtlSeconds,
        }),
      ],
    )

    return { expiraEm: inserted.rows[0]?.expira_em, colaboradorNome: collaborator.nome }
  })

  if (!created) return c.json({ erro: 'Colaborador não encontrado ou inativo.' }, 404)

  return c.json({
    id,
    // Campo legado mantido para compatibilidade; não é exibido nem solicitado.
    codigo: segredoInterno,
    liberada: true,
    colaboradorNome: created.colaboradorNome,
    periodo,
    periodoDefinidoAutomaticamente: true,
    expiraEm: created.expiraEm,
    expiraEmSegundos: config.authorizationTtlSeconds,
    aviso: 'Pausa liberada previamente. O período foi definido automaticamente pelo horário do servidor.',
  }, 201)
})

authorizationRoutes.post('/autorizacoes/cancelar', async (c) => {
  const body = await parseJson(c, z.object({
    colaboradorId: uuidSchema,
    // Compatibilidade com APKs anteriores. O cancelamento usa a liberação ativa mais recente.
    periodo: periodoSchema.optional(),
  }))
  if (!body.ok) return body.response

  const user = c.get('user')
  const canceled = await transaction(async (client) => {
    const result = await client.query<{ id: string; periodo: 'MANHA' | 'TARDE' }>(
      `with target as (
         select id,periodo
         from autorizacoes
         where colaborador_id=$1
           and usado_em is null
           and cancelada_em is null
           and expira_em>now()
         order by criado_em desc
         limit 1
         for update
       )
       update autorizacoes a
       set cancelada_em=now()
       from target
       where a.id=target.id
       returning a.id,target.periodo`,
      [body.data.colaboradorId],
    )
    const authorization = result.rows[0]
    if (!authorization) return null

    await client.query(
      `insert into auditoria (ator_auth_id,ator_tipo,acao,entidade,entidade_id,detalhes)
       values ($1,$2,'CANCELAR_LIBERACAO_FORA_HORARIO','AUTORIZACAO',$3,$4::jsonb)`,
      [
        user.id,
        user.papel,
        authorization.id,
        JSON.stringify({
          colaboradorId: body.data.colaboradorId,
          periodo: authorization.periodo,
        }),
      ],
    )

    return authorization.id
  })

  if (!canceled) {
    return c.json({
      erro: 'Não existe liberação ativa para cancelar. Ela pode ter expirado ou já ter sido utilizada.',
    }, 404)
  }

  return c.json({ ok: true, cancelada: true, id: canceled })
})
