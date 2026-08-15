import { Hono } from 'hono'
import { z } from 'zod'
import type { AppEnv, Device } from '../auth-runtime.js'
import { query } from '../db.js'
import { hashToken, newToken } from '../security.js'
import { parseJson } from './shared.js'

export const deviceSetupRoutes = new Hono<AppEnv>()

deviceSetupRoutes.post('/device-activation', async (c) => {
  const body = await parseJson(c, z.object({
    token: z.string().trim().regex(/^[A-Za-z0-9]{10}$/),
  }))
  if (!body.ok) return body.response

  const activationHash = hashToken(body.data.token)
  const deviceToken = newToken()
  const deviceTokenHash = hashToken(deviceToken)

  const activated = await query<Device>(
    `update dispositivos
        set token_hash=$2, atualizado_em=now()
      where token_hash=$1 and ativo=true
      returning id,nome`,
    [activationHash, deviceTokenHash],
  )

  const device = activated.rows[0]
  if (!device) {
    return c.json({ erro: 'Token de ativação inválido ou já utilizado.' }, 401)
  }

  await query(
    `insert into auditoria (ator_tipo,acao,entidade,entidade_id,detalhes)
     values ('DISPOSITIVO','ATIVAR_DISPOSITIVO','DISPOSITIVO',$1,$2::jsonb)`,
    [device.id, JSON.stringify({ nome: device.nome })],
  )

  return c.json({
    token: deviceToken,
    dispositivo: device,
  })
})
