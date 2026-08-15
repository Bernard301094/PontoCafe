import { Hono } from 'hono'
import { z } from 'zod'
import { requireRole, requireUser, type AppEnv } from '../auth-runtime.js'
import { query } from '../db.js'
import { errorPayload, logServerError } from '../observability.js'
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

  const actor = c.get('user')

  for (let attempt = 0; attempt < 3; attempt += 1) {
    const id = newId()
    const token = newDeviceToken(10)
    const unlockPinHash = body.data.pin ? hashDeviceUnlockPin(id, body.data.pin) : null

    try {
      // Uma única instrução PostgreSQL mantém dispositivo + auditoria atômicos e
      // usa o mesmo caminho currentPool().query() já utilizado pela autenticação.
      // Assim esta operação não depende de pool.connect()/BEGIN/COMMIT no Worker.
      const created = await query<{ id: string; nome: string }>(
        `with novo_dispositivo as (
           insert into dispositivos (
             id,
             nome,
             token_hash,
             unlock_pin_hash,
             unlock_pin_updated_at
           )
           values (
             $1,
             $2,
             $3,
             $4,
             case when $4 is null then null else now() end
           )
           returning id,nome
         ),
         auditoria_criacao as (
           insert into auditoria (
             ator_auth_id,
             ator_tipo,
             acao,
             entidade,
             entidade_id,
             detalhes
           )
           select
             $5,
             'ADMIN',
             'CRIAR_DISPOSITIVO',
             'DISPOSITIVO',
             novo_dispositivo.id::text,
             $6::jsonb
           from novo_dispositivo
           returning id
         )
         select novo_dispositivo.id,novo_dispositivo.nome
           from novo_dispositivo
           cross join auditoria_criacao
          limit 1`,
        [
          id,
          body.data.nome,
          hashToken(token),
          unlockPinHash,
          actor.id,
          JSON.stringify({
            nome: body.data.nome,
            pinConfigurado: unlockPinHash !== null,
          }),
        ],
      )

      const device = created.rows[0]
      if (!device) throw new Error('DEVICE_NOT_PERSISTED')

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
        stage: 'DEVICE_ATOMIC_CREATE',
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
