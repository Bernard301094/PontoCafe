import { betterAuth } from 'better-auth'
import { admin, bearer } from 'better-auth/plugins'
import { createMiddleware } from 'hono/factory'
import type { MiddlewareHandler } from 'hono'
import { config } from './config.js'
import { getPool } from './db.js'

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

function createAuth() {
  const secret = process.env.BETTER_AUTH_SECRET
  const baseURL = process.env.BETTER_AUTH_URL || 'http://localhost:3000'
  if (!secret) throw new Error('Configuração de autenticação ausente.')

  return betterAuth({
    database: getPool(),
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
}

type AuthInstance = ReturnType<typeof createAuth>
let localAuth: AuthInstance | null = null

export function getAuth(): AuthInstance {
  if (process.env.PONTOCAFE_RUNTIME === 'cloudflare') {
    // O pool em Cloudflare é escopado à invocação atual. Criar a instância de
    // Better Auth aqui impede que ela retenha conexões de uma requisição anterior.
    return createAuth()
  }

  localAuth ??= createAuth()
  return localAuth
}

// Mantém compatibilidade com as rotas existentes sem manter uma instância
// global de Better Auth no Worker. Cada acesso resolve a instância da request.
export const auth = new Proxy({} as AuthInstance, {
  get(_target, property) {
    const instance = getAuth()
    const value = Reflect.get(instance, property, instance)
    return typeof value === 'function' ? value.bind(instance) : value
  },
})

export const requireUser = createMiddleware<AppEnv>(async (c, next) => {
  const session = await getAuth().api.getSession({ headers: c.req.raw.headers })
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
