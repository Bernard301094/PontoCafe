import { Hono } from 'hono'
import { z } from 'zod'
import { requireRole, requireUser, type AppEnv } from '../auth-runtime.js'
import { transaction } from '../db.js'
import { hashDeviceUnlockPin, hashToken, newDeviceToken, newId } from '../security.js'
import { parseJson } from './shared.js'

export const deviceActivationRoutes = new Hono<AppEnv>()
deviceActivationRoutes.use('*', requireUser, requireRole('ADMIN'))

deviceActivationRoutes.post('/device-activation', async (c) => {
  const body = await parseJson(c, z.object({
    nome: z.string().trim().min(2).max(120),
    pin: z.string().trim().regex(/^\d{4,12}$/).optional(),
  }))
  if (!body.ok) return body.response

  const id = newId()
  const token = newDeviceToken(10)
  const unlockPinHash = body.data.pin ? hashDeviceUnlockPin(id, body.data.pin) : null

  await transaction(async (client) => {
    await client.query(
      `insert into dispositivos (id,nome,token_hash,unlock_pin_hash,unlock_pin_updated_at)
       values ($1,$2,$3,$4,case when $4 is null then null else now() end)`,
      [id, body.data.nome, hashToken(token), unlockPinHash],
    )
    await client.query(
      `insert into auditoria (ator_auth_id,ator_tipo,acao,entidade,entidade_id,detalhes)
       values ($1,'ADMIN','CRIAR_DISPOSITIVO','DISPOSITIVO',$2,$3::jsonb)`,
      [c.get('user').id, id, JSON.stringify({ nome: body.data.nome, pinConfigurado: unlockPinHash !== null })],
    )
  })

  return c.json({
    id,
    nome: body.data.nome,
    token,
    pinConfigurado: unlockPinHash !== null,
    aviso: 'Este token de 10 caracteres é exibido uma única vez.',
  }, 201)
})
