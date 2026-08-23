import type { Context } from 'hono'
import { createMiddleware } from 'hono/factory'
import { z } from 'zod'
import type { AppEnv, Device } from '../auth-runtime.js'
import { query } from '../db.js'
import { hashToken } from '../security.js'

export const DEVICE_AUTH_INVALID_CODE = 'DEVICE_AUTH_INVALID'

export const emailSchema = z.string().email().transform((v) => v.trim().toLowerCase())
export const passwordSchema = z.string().min(10).max(128)
export const uuidSchema = z.string().uuid()
export const embeddingSchema = z.array(z.number().finite().min(-100).max(100)).min(64).max(2048)
export const periodoSchema = z.enum(['MANHA', 'TARDE'])

function cleanHeader(value: string | undefined, maxLength: number): string | null {
  const cleaned = value?.trim().slice(0, maxLength)
  return cleaned ? cleaned : null
}

function deviceAuthInvalidPayload(c: Context<AppEnv>, erro: string) {
  return {
    erro,
    codigo: DEVICE_AUTH_INVALID_CODE,
    requestId: c.get('requestId'),
  }
}

function isLegacyDeviceAuthMessage(value: unknown): value is string {
  return value === 'Dispositivo não autenticado.' || value === 'Dispositivo inválido.'
}

/**
 * Compatibility adapter for legacy /ponto route modules that still own a local
 * requireDevice implementation. It upgrades only the two credential-rejection
 * messages to the stable DEVICE_AUTH_INVALID contract and deliberately leaves
 * every other 401/403 business response untouched.
 *
 * This avoids an extra device lookup on every request while the route modules
 * are progressively migrated to deviceTokenMiddleware.
 */
export const deviceAuthContractMiddleware = createMiddleware<AppEnv>(async (c, next) => {
  await next()

  const response = c.res
  if (response.status !== 401) return

  const contentType = response.headers.get('content-type')?.toLowerCase() ?? ''
  if (contentType && !contentType.includes('application/json')) return

  try {
    const payload = await response.clone().json() as Record<string, unknown>
    if (payload.codigo === DEVICE_AUTH_INVALID_CODE) return
    if (!isLegacyDeviceAuthMessage(payload.erro)) return

    const headers = new Headers(response.headers)
    headers.set('content-type', 'application/json; charset=UTF-8')
    c.res = new Response(
      JSON.stringify({
        ...payload,
        codigo: DEVICE_AUTH_INVALID_CODE,
        requestId: payload.requestId ?? c.get('requestId'),
      }),
      {
        status: 401,
        statusText: response.statusText,
        headers,
      },
    )
  } catch {
    // A resposta original continua intacta se não for JSON válido.
  }
})

async function recordDeviceHeartbeat(
  device: Device,
  appVersion: string | null,
  deviceModel: string | null,
  androidVersion: string | null,
) {
  if (!appVersion && !deviceModel && !androidVersion) return

  // A presença de uma requisição autenticada comprova que o token longo já está
  // instalado e funcionando neste aparelho. Mantemos apenas um heartbeat por
  // janela de 15 minutos, ou um novo imediatamente quando algum metadado muda.
  // Falhar ao registrar observabilidade nunca pode interromper o Ponto.
  try {
    await query(
      `insert into auditoria (ator_tipo,acao,entidade,entidade_id,detalhes)
       select 'DISPOSITIVO','DEVICE_HEARTBEAT','DISPOSITIVO',$1,
              jsonb_build_object(
                'appVersion',$2::text,
                'deviceModel',$3::text,
                'androidVersion',$4::text
              )
        where not exists (
          select 1
            from auditoria recent
           where recent.acao='DEVICE_HEARTBEAT'
             and recent.entidade='DISPOSITIVO'
             and recent.entidade_id::text=$1::text
             and recent.criado_em>now()-interval '15 minutes'
             and coalesce(recent.detalhes->>'appVersion','')=coalesce($2,'')
             and coalesce(recent.detalhes->>'deviceModel','')=coalesce($3,'')
             and coalesce(recent.detalhes->>'androidVersion','')=coalesce($4,'')
        )`,
      [device.id, appVersion, deviceModel, androidVersion],
    )
  } catch {
    // Observabilidade é best-effort. Autenticação e registro de ponto continuam.
  }
}

export const deviceTokenMiddleware = createMiddleware<AppEnv>(async (c, next) => {
  const token = c.req.header('X-Device-Token')?.trim()
  if (!token) {
    return c.json(deviceAuthInvalidPayload(c, 'Dispositivo não autenticado.'), 401)
  }

  const result = await query<Device>(
    'select id,nome from dispositivos where token_hash=$1 and ativo=true limit 1',
    [hashToken(token)],
  )
  const device = result.rows[0]
  if (!device) {
    return c.json(deviceAuthInvalidPayload(c, 'Dispositivo inválido.'), 401)
  }

  c.set('device', device)
  await recordDeviceHeartbeat(
    device,
    cleanHeader(c.req.header('X-App-Version'), 32),
    cleanHeader(c.req.header('X-Device-Model'), 120),
    cleanHeader(c.req.header('X-Android-Version'), 40),
  )
  await next()
})

export async function parseJson<T>(c: Context<AppEnv>, schema: z.ZodType<T>) {
  try {
    const parsed = schema.safeParse(await c.req.json())
    if (!parsed.success) return { ok: false as const, response: c.json({ erro: 'Dados inválidos.', detalhes: parsed.error.flatten() }, 400) }
    return { ok: true as const, data: parsed.data }
  } catch {
    return { ok: false as const, response: c.json({ erro: 'JSON inválido.' }, 400) }
  }
}
