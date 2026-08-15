import { Hono } from 'hono'
import { createMiddleware } from 'hono/factory'
import { z } from 'zod'
import type { AppEnv, Device } from '../auth-runtime.js'
import { query } from '../db.js'
import { hashDeviceUnlockPin, hashToken, secureHexEquals } from '../security.js'
import { parseJson } from './shared.js'

const LEGACY_DEFAULT_PIN_SHA256 = '51b6d230c2e8d8a991c525dcd98cc5c2567eb5720336ea62a6e1097ad04fbc3f'
const MAX_FAILURES = 5
const LOCK_SECONDS = 60

const requireDevice = createMiddleware<AppEnv>(async (c, next) => {
  const token = c.req.header('X-Device-Token')?.trim()
  if (!token) return c.json({ erro: 'Dispositivo não autenticado.' }, 401)
  const result = await query<Device>(
    'select id,nome from dispositivos where token_hash=$1 and ativo=true limit 1',
    [hashToken(token)],
  )
  const device = result.rows[0]
  if (!device) return c.json({ erro: 'Dispositivo inválido.' }, 401)
  c.set('device', device)
  await next()
})

export const deviceUnlockRoutes = new Hono<AppEnv>()
deviceUnlockRoutes.use('*', requireDevice)

deviceUnlockRoutes.post('/device/unlock', async (c) => {
  const body = await parseJson(c, z.object({
    pin: z.string().trim().regex(/^\d{4,12}$/),
    area: z.enum(['SUPERVISOR', 'ADMIN']),
  }))
  if (!body.ok) return body.response

  const device = c.get('device')
  const result = await query<{
    unlock_pin_hash: string | null
    unlock_fail_count: number
    unlock_locked_until: string | null
    locked_seconds: number
  }>(
    `select unlock_pin_hash,unlock_fail_count,unlock_locked_until::text,
            case when unlock_locked_until>now()
                 then ceil(extract(epoch from (unlock_locked_until-now())))::int
                 else 0 end as locked_seconds
       from dispositivos
      where id=$1 and ativo=true
      limit 1`,
    [device.id],
  )
  const security = result.rows[0]
  if (!security) return c.json({ erro: 'Dispositivo inválido.' }, 401)

  if (security.locked_seconds > 0) {
    return c.json({
      erro: `Muitas tentativas incorretas. Aguarde ${security.locked_seconds} segundos.`,
      bloqueado: true,
      tentarNovamenteEmSegundos: security.locked_seconds,
    }, 429)
  }

  const valid = security.unlock_pin_hash
    ? secureHexEquals(hashDeviceUnlockPin(device.id, body.data.pin), security.unlock_pin_hash)
    : secureHexEquals(hashToken(body.data.pin), LEGACY_DEFAULT_PIN_SHA256)

  if (!valid) {
    const nextFailures = security.unlock_fail_count + 1
    const shouldLock = nextFailures >= MAX_FAILURES
    await query(
      `update dispositivos
          set unlock_fail_count=$2,
              unlock_locked_until=case when $3 then now()+($4 || ' seconds')::interval else null end,
              atualizado_em=now()
        where id=$1`,
      [device.id, shouldLock ? 0 : nextFailures, shouldLock, LOCK_SECONDS],
    )

    if (shouldLock) {
      return c.json({
        erro: `PIN incorreto. O desbloqueio foi bloqueado por ${LOCK_SECONDS} segundos.`,
        bloqueado: true,
        tentarNovamenteEmSegundos: LOCK_SECONDS,
      }, 429)
    }

    return c.json({
      erro: 'PIN incorreto.',
      tentativasRestantes: MAX_FAILURES - nextFailures,
    }, 401)
  }

  await query(
    `update dispositivos
        set unlock_fail_count=0,unlock_locked_until=null,atualizado_em=now()
      where id=$1`,
    [device.id],
  )
  await query(
    `insert into auditoria (ator_tipo,acao,entidade,entidade_id,detalhes)
     values ('DISPOSITIVO','DESBLOQUEAR_MODO_PONTO','DISPOSITIVO',$1,$2::jsonb)`,
    [device.id, JSON.stringify({ nome: device.nome, area: body.data.area })],
  )

  return c.json({ ok: true, area: body.data.area })
})
