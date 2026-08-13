import { Hono } from 'hono'
import { z } from 'zod'
import { requireRole, requireUser, type AppEnv } from '../auth-runtime.js'
import { config } from '../config.js'
import { query } from '../db.js'
import { generateAuthorizationCode, hashAuthorizationCode, newId } from '../security.js'
import { parseJson, periodoSchema, uuidSchema } from './shared.js'

export const authorizationRoutes = new Hono<AppEnv>()
authorizationRoutes.use('*', requireUser, requireRole('ADMIN', 'SUPERVISOR'))

authorizationRoutes.post('/autorizacoes', async (c) => {
  const body = await parseJson(c, z.object({ colaboradorId: uuidSchema, periodo: periodoSchema, motivo: z.string().trim().min(2).max(300) }))
  if (!body.ok) return body.response
  const user = c.get('user')
  const employee = await query('select id from colaboradores where id=$1 and ativo=true', [body.data.colaboradorId])
  if (!employee.rows[0]) return c.json({ erro: 'Colaborador não encontrado ou inativo.' }, 404)

  await query(`update autorizacoes set cancelada_em=now() where colaborador_id=$1 and periodo=$2 and usado_em is null and cancelada_em is null and expira_em>now()`, [body.data.colaboradorId, body.data.periodo])
  const codigo = generateAuthorizationCode()
  const id = newId()
  const created = await query<{ expira_em:string }>(
    `insert into autorizacoes (id,colaborador_id,supervisor_auth_id,periodo,codigo_hash,motivo,expira_em)
     values ($1,$2,$3,$4,$5,$6,now()+($7*interval '1 second')) returning expira_em::text`,
    [id, body.data.colaboradorId, user.id, body.data.periodo, hashAuthorizationCode(codigo), body.data.motivo, config.authorizationTtlSeconds],
  )
  return c.json({ id, codigo, expiraEm: created.rows[0]?.expira_em, expiraEmSegundos: config.authorizationTtlSeconds, aviso: 'O código é exibido somente nesta resposta.' }, 201)
})
