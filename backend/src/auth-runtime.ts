import { betterAuth } from 'better-auth'
import { admin, bearer } from 'better-auth/plugins'
import { createMiddleware } from 'hono/factory'
import type { MiddlewareHandler } from 'hono'
import { config } from './config.js'
import { pool } from './db.js'

export type Role = 'ADMIN' | 'SUPERVISOR'
export type AuthUser = { id: string; nome: string; email: string; papel: Role }
export type Device = { id: string; nome: string }

export type RuntimeBindings = {
  FIRST_ADMIN_SETUP_KEY?: string
}

export type AppEnv = {
  Bindings: RuntimeBindings
  Variables: { user: AuthUser; device: Device }
}

const secret = process.env.BETTER_AUTH_SECRET
const baseURL = process.env.BETTER_AUTH_URL || 'http://localhost:3000'
if (!secret) throw new Error('Configuração de autenticação ausente.')

export const auth = betterAuth({
  database: pool,
  secret,
  baseURL,
  emailAndPassword: {
    enabled: true,
    disableSignUp: true,
    minPasswordLength: 10,
    maxPasswordLength: 128,
  },
  session: { expiresIn: config.sessionTtlHours * 3600, updateAge: 3600 },
  advanced: { database: { generateId: 'uuid' } },
  plugins: [bearer({ requireSignature: true }), admin()],
})

export const requireUser = createMiddleware<AppEnv>(async (c, next) => {
  const session = await auth.api.getSession({ headers: c.req.raw.headers })
  if (!session) return c.json({ erro: 'Não autenticado.' }, 401)

  const role = (session.user as typeof session.user & { role?: string }).role
  c.set('user', {
    id: session.user.id,
    nome: session.user.name,
    email: session.user.email,
    papel: role === 'admin' ? 'ADMIN' : 'SUPERVISOR',
  })
  await next()
})

export function requireRole(...roles: Role[]): MiddlewareHandler<AppEnv> {
  return async (c, next) => {
    if (!roles.includes(c.get('user').papel)) return c.json({ erro: 'Acesso negado.' }, 403)
    await next()
  }
}
