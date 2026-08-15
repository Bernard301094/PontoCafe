import { Hono } from 'hono'
import { z } from 'zod'
import { requireRole, requireUser, type AppEnv } from '../auth-runtime.js'
import { query } from '../db.js'
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

  try {
    const id = newId()
    const token = newDeviceToken(10)
    const unlockPinHash = body.data.pin ? hashDeviceUnlockPin(id, body.data.pin) : null
    const details = JSON.stringify({
      nome: body.data.nome,
      pinConfigurado: unlockPinHash !== null,
    })

    // Dispositivo e auditoria são persistidos pela mesma instrução SQL. Além de
    // manter atomicidade, isso evita abrir uma transação explícita sobre
    // Hyperdrive apenas para duas escritas relacionadas.
    const created = await query<{ id: string; nome: string }>(
      `with novo_dispositivo as (
         insert into dispositivos (id,nome,token_hash,unlock_pin_hash,unlock_pin_updated_at)
         values ($1,$2,$3,$4,case when $4 is null then null else now() end)
         returning id,nome
       ), auditoria_criacao as (
         insert into auditoria (ator_auth_id,ator_tipo,acao,entidade,entidade_id,detalhes)
         select $5,'ADMIN','CRIAR_DISPOSITIVO','DISPOSITIVO',id,$6::jsonb
           from novo_dispositivo
         returning id
       )
       select id,nome from novo_dispositivo`,
      [
        id,
        body.data.nome,
        hashToken(token),
        unlockPinHash,
        c.get('user').id,
        details,
      ],
    )

    const device = created.rows[0]
    if (!device) throw new Error('DEVICE_NOT_PERSISTED')

    return c.json({
      id: device.id,
      nome: device.nome,
      token,
      pinConfigurado: unlockPinHash !== null,
      aviso: 'Este token de 10 caracteres é exibido uma única vez.',
    }, 201)
  } catch (error) {
    const databaseError = error as { code?: string; constraint?: string; name?: string }
    console.error('Falha ao criar dispositivo.', {
      code: databaseError.code ?? null,
      constraint: databaseError.constraint ?? null,
      name: databaseError.name ?? null,
    })
    return c.json({
      erro: 'Não foi possível criar o dispositivo. Tente novamente em alguns segundos.',
      codigo: 'DEVICE_CREATE_FAILED',
    }, 500)
  }
})
