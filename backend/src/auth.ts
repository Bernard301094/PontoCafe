import { betterAuth } from 'better-auth'
import { admin, bearer } from 'better-auth/plugins'
import { createMiddleware } from 'hono/factory'
import type { MiddlewareHandler } from 'hono'
import { config } from './config.js'
import { pool } from './db.js'

export type Role = 'ADMIN' | 'SUPERVISOR'
export type AuthUser = { id: string; nome: string; email: string; papel: Role }
export type Device = { id: string; nome: string }
export type AppEnv = { Variables: { user: AuthUser; device: Device } }

export const auth = betterAuth({
  database: pool,
  secret: config.betterAuthSecret,
  baseURL: config.betterAuthUrl,
  emailAndPassword: {
    enabled: true,
    disableSignUp: true,
    minPasswordLength: 10,
    maxPasswordLength: 128,
  },
  session: {
    expiresIn: config.sessionTtlHours * 60 * 60,
    updateAge: 60 * 60,
  },
  advanced: { database: { generateId: 'uuid' } },
  plugins: [bearer({ requireSignature: true }), admin()],
})

export const requireUser = createMiddleware<AppEnv>(async (c, next) => {
  const session = await auth.api.getSession({ headers: c.req.raw.headers })
  if (!session) return c.json({ erro: 'Não autenticado.' }, 401)
  const rawRole = (session.user as typeof session.user & { role?: string }).role
  c.set('user', {
    id: session.user.id,
    nome: session.user.name,
    email: session.user.email,
    papel: rawRole === 'admin' ? 'ADMIN' : 'SUPERVISOR',
  })
  await next()
})

export function requireRole(...roles: Role[]): MiddlewareHandler<AppEnv> {
  return async (c, next) => {
    const user = c.get('user')
    if (!user || !roles.includes(user.papel)) return c.json({ erro: 'Acesso negado.' }, 403)
    await next()
  }
}
