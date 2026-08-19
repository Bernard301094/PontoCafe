import type { Context } from 'hono'
import { createMiddleware } from 'hono/factory'
import { z } from 'zod'
import type { AppEnv, Device } from '../auth-runtime.js'
import { query } from '../db.js'
import { hashToken } from '../security.js'

export const emailSchema = z.string().email().transform((v) => v.trim().toLowerCase())
export const passwordSchema = z.string().min(10).max(128)
export const uuidSchema = z.string().uuid()
export const embeddingSchema = z.array(z.number().finite().min(-100).max(100)).min(64).max(2048)
export const periodoSchema = z.enum(['MANHA', 'TARDE'])

export const deviceTokenMiddleware = createMiddleware<AppEnv>(async (c, next) => {
  const token = c.req.header('X-Device-Token')?.trim()
  if (!token) {
    return c.json({
      erro: 'Dispositivo não autenticado.',
      requestId: c.get('requestId'),
    }, 401)
  }

  const result = await query<Device>(
    'select id,nome from dispositivos where token_hash=$1 and ativo=true limit 1',
    [hashToken(token)],
  )
  const device = result.rows[0]
  if (!device) {
    return c.json({
      erro: 'Dispositivo inválido.',
      requestId: c.get('requestId'),
    }, 401)
  }

  c.set('device', device)
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
