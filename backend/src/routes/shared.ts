import type { Context } from 'hono'
import { z } from 'zod'
import type { AppEnv } from '../auth.js'

export const emailSchema = z.string().email().transform((v) => v.trim().toLowerCase())
export const passwordSchema = z.string().min(10).max(200)
export const uuidSchema = z.string().uuid()
export const embeddingSchema = z.array(z.number().finite().min(-100).max(100)).min(64).max(2048)
export const periodoSchema = z.enum(['MANHA', 'TARDE'])

export async function parseJson<T>(c: Context<AppEnv>, schema: z.ZodType<T>) {
  try {
    const parsed = schema.safeParse(await c.req.json())
    if (!parsed.success) return { ok: false as const, response: c.json({ erro: 'Dados inválidos.', detalhes: parsed.error.flatten() }, 400) }
    return { ok: true as const, data: parsed.data }
  } catch {
    return { ok: false as const, response: c.json({ erro: 'JSON inválido.' }, 400) }
  }
}
