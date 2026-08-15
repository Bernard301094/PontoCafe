import { Hono } from 'hono'
import { z } from 'zod'
import { requireRole, requireUser, type AppEnv } from '../auth-runtime.js'
import { query } from '../db.js'
import { hashDeviceUnlockPin } from '../security.js'
import { parseJson, uuidSchema } from './shared.js'

export const deviceManagementRoutes = new Hono<AppEnv>()
deviceManagementRoutes.use('*', requireUser, requireRole('ADMIN'))

const pinSchema = z.string().trim().regex(/^\d{4,12}$/, 'O PIN deve ter entre 4 e 12 números.')

deviceManagementRoutes.get('/devices', async (c) => {
  const result = await query<{
    id: string
    nome: string
    ativo: boolean
    criadoEm: string
    atualizadoEm: string
    pinConfigurado: boolean
  }>(
    `select id,nome,ativo,
            criado_em::text as "criadoEm",
            atualizado_em::text as "atualizadoEm",
            (unlock_pin_hash is not null) as "pinConfigurado"
       from dispositivos
      order by ativo desc,nome`,
  )
  return c.json({ dispositivos: result.rows })
})

deviceManagementRoutes.put('/devices/:id/unlock-pin', async (c) => {
  const deviceId = c.req.param('id')
  if (!uuidSchema.safeParse(deviceId).success) return c.json({ erro: 'Dispositivo inválido.' }, 400)

  const body = await parseJson(c, z.object({ pin: pinSchema }))
  if (!body.ok) return body.response

  const updated = await query<{ id: string; nome: string }>(
    `update dispositivos
        set unlock_pin_hash=$2,
            unlock_pin_updated_at=now(),
            unlock_fail_count=0,
            unlock_locked_until=null,
            atualizado_em=now()
      where id=$1 and ativo=true
      returning id,nome`,
    [deviceId, hashDeviceUnlockPin(deviceId, body.data.pin)],
  )
  const device = updated.rows[0]
  if (!device) return c.json({ erro: 'Dispositivo não encontrado ou inativo.' }, 404)

  const actor = c.get('user')
  await query(
    `insert into auditoria (ator_auth_id,ator_tipo,acao,entidade,entidade_id,detalhes)
     values ($1,'ADMIN','ALTERAR_PIN_DISPOSITIVO','DISPOSITIVO',$2,$3::jsonb)`,
    [actor.id, deviceId, JSON.stringify({ nome: device.nome })],
  )

  return c.json({ ok: true, dispositivoId: device.id, nome: device.nome, pinConfigurado: true })
})
