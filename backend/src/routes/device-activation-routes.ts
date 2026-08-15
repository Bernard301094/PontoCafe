import { Hono } from 'hono'
import { z } from 'zod'
import { requireRole, requireUser, type AppEnv } from '../auth-runtime.js'
import { query } from '../db.js'
import { hashToken, newDeviceToken, newId } from '../security.js'
import { parseJson } from './shared.js'

export const deviceActivationRoutes = new Hono<AppEnv>()
deviceActivationRoutes.use('*', requireUser, requireRole('ADMIN'))

deviceActivationRoutes.post('/device-activation', async (c) => {
  const body = await parseJson(c, z.object({ nome: z.string().trim().min(2).max(120) }))
  if (!body.ok) return body.response

  const id = newId()
  const token = newDeviceToken(10)

  await query(
    'insert into dispositivos (id,nome,token_hash) values ($1,$2,$3)',
    [id, body.data.nome, hashToken(token)],
  )

  return c.json({
    id,
    nome: body.data.nome,
    token,
    aviso: 'Este token de 10 caracteres é exibido uma única vez.',
  }, 201)
})
