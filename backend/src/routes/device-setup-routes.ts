import { Hono } from 'hono'
import { z } from 'zod'
import type { AppEnv, Device } from '../auth-runtime.js'
import { transaction } from '../db.js'
import { hashToken, newToken } from '../security.js'
import { parseJson } from './shared.js'

export const deviceSetupRoutes = new Hono<AppEnv>()

function safeDeviceMetadataHeader(value: string | undefined, maxLength: number): string | null {
  const normalized = value
    ?.replace(/[\u0000-\u001f\u007f]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, maxLength)
  return normalized || null
}

deviceSetupRoutes.post('/device-activation', async (c) => {
  const body = await parseJson(c, z.object({
    token: z.string().trim().regex(/^[A-Za-z0-9]{10}$/),
  }))
  if (!body.ok) return body.response

  const appVersion = safeDeviceMetadataHeader(c.req.header('X-App-Version'), 80)
  const deviceModel = safeDeviceMetadataHeader(c.req.header('X-Device-Model'), 120)
  const androidVersion = safeDeviceMetadataHeader(c.req.header('X-Android-Version'), 80)

  const activationHash = hashToken(body.data.token)
  const deviceToken = newToken()
  const deviceTokenHash = hashToken(deviceToken)

  const device = await transaction(async (client) => {
    const activated = await client.query<Device>(
      `update dispositivos
          set token_hash=$2, atualizado_em=now()
        where token_hash=$1 and ativo=true
        returning id,nome`,
      [activationHash, deviceTokenHash],
    )

    const row = activated.rows[0]
    if (!row) return null

    await client.query(
      `insert into auditoria (ator_tipo,acao,entidade,entidade_id,detalhes)
       values ('DISPOSITIVO','ATIVAR_DISPOSITIVO','DISPOSITIVO',$1,$2::jsonb)`,
      [
        row.id,
        JSON.stringify({
          nome: row.nome,
          appVersion,
          deviceModel,
          androidVersion,
        }),
      ],
    )
    return row
  })

  if (!device) {
    return c.json({ erro: 'Token de ativação inválido ou já utilizado.' }, 401)
  }

  return c.json({
    token: deviceToken,
    dispositivo: device,
  })
})
