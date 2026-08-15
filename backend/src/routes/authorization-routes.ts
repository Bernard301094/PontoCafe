import { Hono } from 'hono'
import { z } from 'zod'
import { requireRole, requireUser, type AppEnv } from '../auth-runtime.js'
import { config } from '../config.js'
import { transaction } from '../db.js'
import { generateAuthorizationCode, hashAuthorizationCode, newId } from '../security.js'
import { parseJson, periodoSchema, uuidSchema } from './shared.js'

export const authorizationRoutes = new Hono<AppEnv>()
authorizationRoutes.use('*', requireUser, requireRole('ADMIN', 'SUPERVISOR'))

authorizationRoutes.post('/autorizacoes', async (c) => {
  const body = await parseJson(c, z.object({
    colaboradorId: uuidSchema,
    periodo: periodoSchema,
    motivo: z.string().trim().min(2).max(300),
  }))
  if (!body.ok) return body.response

  const user = c.get('user')
  const codigo = generateAuthorizationCode()
  const id = newId()

  const created = await transaction(async (client) => {
    const employee = await client.query<{ id: string; nome: string }>(
      'select id,nome from colaboradores where id=$1 and ativo=true for update',
      [body.data.colaboradorId],
    )
    const collaborator = employee.rows[0]
    if (!collaborator) return null

    // Só pode existir um código temporário válido por colaborador/período.
    await client.query(
      `update autorizacoes set cancelada_em=now()
       where colaborador_id=$1 and periodo=$2 and usado_em is null
         and cancelada_em is null and expira_em>now()`,
      [body.data.colaboradorId, body.data.periodo],
    )

    const inserted = await client.query<{ expira_em: string }>(
      `insert into autorizacoes (id,colaborador_id,supervisor_auth_id,periodo,codigo_hash,motivo,expira_em)
       values ($1,$2,$3,$4,$5,$6,now()+($7*interval '1 second'))
       returning expira_em::text`,
      [
        id,
        body.data.colaboradorId,
        user.id,
        body.data.periodo,
        hashAuthorizationCode(codigo),
        body.data.motivo,
        config.authorizationTtlSeconds,
      ],
    )

    await client.query(
      `insert into auditoria (ator_auth_id,ator_tipo,acao,entidade,entidade_id,detalhes)
       values ($1,$2,'GERAR_AUTORIZACAO_FORA_HORARIO','AUTORIZACAO',$3,$4::jsonb)`,
      [
        user.id,
        user.papel,
        id,
        JSON.stringify({
          colaboradorId: collaborator.id,
          colaboradorNome: collaborator.nome,
          periodo: body.data.periodo,
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
    codigo,
    colaboradorNome: created.colaboradorNome,
    expiraEm: created.expiraEm,
    expiraEmSegundos: config.authorizationTtlSeconds,
    aviso: 'O código é exibido somente nesta resposta.',
  }, 201)
})
