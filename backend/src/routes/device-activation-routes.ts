import { Hono } from 'hono'
import { z } from 'zod'
import { requireRole, requireUser, type AppEnv } from '../auth-runtime.js'
import { transaction, type TransactionStage } from '../db.js'
import { errorPayload, logServerError } from '../observability.js'
import { hashDeviceUnlockPin, hashToken, newDeviceToken, newId } from '../security.js'
import { parseJson } from './shared.js'

export const deviceActivationRoutes = new Hono<AppEnv>()
deviceActivationRoutes.use('*', requireUser, requireRole('ADMIN'))

type DeviceCreationStage =
  | 'DEVICE_DB_CONNECT'
  | 'DEVICE_DB_BEGIN'
  | 'DEVICE_INSERT'
  | 'DEVICE_AUDIT_INSERT'
  | 'DEVICE_DB_COMMIT'

function mapTransactionStage(stage: TransactionStage): DeviceCreationStage | null {
  switch (stage) {
    case 'DB_CONNECT': return 'DEVICE_DB_CONNECT'
    case 'DB_BEGIN': return 'DEVICE_DB_BEGIN'
    case 'DB_COMMIT': return 'DEVICE_DB_COMMIT'
    case 'DB_BODY':
    case 'DB_ROLLBACK':
      return null
  }
}

deviceActivationRoutes.post('/device-activation', async (c) => {
  const body = await parseJson(c, z.object({
    nome: z.string().trim().min(2).max(120),
    pin: z.string().trim().regex(/^\d{4,12}$/).optional(),
  }))
  if (!body.ok) return body.response

  const actor = c.get('user')

  for (let attempt = 0; attempt < 3; attempt += 1) {
    const id = newId()
    const token = newDeviceToken(10)
    const unlockPinHash = body.data.pin ? hashDeviceUnlockPin(id, body.data.pin) : null
    let stage: DeviceCreationStage = 'DEVICE_DB_CONNECT'

    try {
      const device = await transaction(async (client) => {
        stage = 'DEVICE_INSERT'
        const created = await client.query<{ id: string; nome: string }>(
          `insert into dispositivos (id,nome,token_hash,unlock_pin_hash,unlock_pin_updated_at)
           values ($1,$2,$3,$4,case when $4 is null then null else now() end)
           returning id,nome`,
          [id, body.data.nome, hashToken(token), unlockPinHash],
        )
        const row = created.rows[0]
        if (!row) throw new Error('DEVICE_NOT_PERSISTED')

        stage = 'DEVICE_AUDIT_INSERT'
        await client.query(
          `insert into auditoria (ator_auth_id,ator_tipo,acao,entidade,entidade_id,detalhes)
           values ($1,'ADMIN','CRIAR_DISPOSITIVO','DISPOSITIVO',$2,$3::jsonb)`,
          [
            actor.id,
            row.id,
            JSON.stringify({
              nome: row.nome,
              pinConfigurado: unlockPinHash !== null,
            }),
          ],
        )

        return row
      }, {
        onStage: (transactionStage) => {
          const mapped = mapTransactionStage(transactionStage)
          if (mapped) stage = mapped
        },
        onRollbackFailure: (rollbackError) => {
          logServerError(c, 'device_rollback_failure', rollbackError, {
            attempt: attempt + 1,
            originalStage: stage,
          })
        },
      })

      return c.json({
        id: device.id,
        nome: device.nome,
        token,
        pinConfigurado: unlockPinHash !== null,
        aviso: 'Este código de ativação de 10 caracteres é exibido uma única vez.',
        requestId: c.get('requestId'),
      }, 201)
    } catch (error) {
      const databaseError = error as { code?: string; constraint?: string }
      const tokenCollision = databaseError.code === '23505' && databaseError.constraint === 'dispositivos_token_hash_key'
      if (tokenCollision && attempt < 2) continue

      logServerError(c, 'device_create_failure', error, {
        stage,
        attempt: attempt + 1,
        tokenCollision,
      })
      return c.json(
        errorPayload(
          c,
          'Não foi possível criar o dispositivo. Tente novamente em alguns segundos.',
          tokenCollision ? 'DEVICE_TOKEN_COLLISION' : 'DEVICE_CREATE_FAILED',
        ),
        500,
      )
    }
  }

  return c.json(errorPayload(c, 'Não foi possível gerar um código de ativação único.', 'DEVICE_TOKEN_COLLISION'), 500)
})
