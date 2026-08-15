import { Hono } from 'hono'
import { z } from 'zod'
import type { AppEnv } from '../auth-runtime.js'
import { query } from '../db.js'
import { errorPayload, logServerError } from '../observability.js'
import { deviceTokenMiddleware, parseJson } from './shared.js'

export const deviceTelemetryRoutes = new Hono<AppEnv>()
deviceTelemetryRoutes.use('*', deviceTokenMiddleware)

const healthSchema = z.object({
  appVersion: z.string().trim().min(1).max(32),
  crashCount: z.number().int().min(0).max(1_000_000),
  lastCrashMillis: z.number().int().min(0),
  lastCrashType: z.string().trim().max(80).optional().nullable(),
  lastCrashLocation: z.string().trim().max(180).optional().nullable(),
  stallCount: z.number().int().min(0).max(1_000_000),
  lastStallMillis: z.number().int().min(0),
  lastStallDurationMillis: z.number().int().min(0).max(120_000),
  deviceModel: z.string().trim().max(120).optional().nullable(),
  androidVersion: z.string().trim().max(40).optional().nullable(),
})

deviceTelemetryRoutes.post('/telemetria/saude', async (c) => {
  const body = await parseJson(c, healthSchema)
  if (!body.ok) return body.response

  const device = c.get('device')
  try {
    await query(
      `insert into auditoria (ator_tipo,acao,entidade,entidade_id,detalhes)
       values ('DISPOSITIVO','APP_HEALTH','DISPOSITIVO',$1,$2::jsonb)`,
      [device.id, JSON.stringify({
        appVersion: body.data.appVersion,
        crashCount: body.data.crashCount,
        lastCrashMillis: body.data.lastCrashMillis,
        lastCrashType: body.data.lastCrashType ?? null,
        lastCrashLocation: body.data.lastCrashLocation ?? null,
        stallCount: body.data.stallCount,
        lastStallMillis: body.data.lastStallMillis,
        lastStallDurationMillis: body.data.lastStallDurationMillis,
        deviceModel: body.data.deviceModel ?? null,
        androidVersion: body.data.androidVersion ?? null,
      })],
    )
    return c.json({ ok: true, requestId: c.get('requestId') })
  } catch (error) {
    logServerError(c, 'device_health_telemetry_failure', error, { deviceId: device.id })
    return c.json(errorPayload(c, 'Não foi possível registrar a saúde do dispositivo.', 'DEVICE_HEALTH_TELEMETRY_FAILED'), 500)
  }
})
