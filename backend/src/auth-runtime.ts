import { betterAuth } from 'better-auth'
import { admin, bearer } from 'better-auth/plugins'
import { createMiddleware } from 'hono/factory'
import type { MiddlewareHandler } from 'hono'
import type { AvatarBucket } from './avatar-storage.js'
import { config } from './config.js'
import { getPool, query } from './db.js'
import { hashPassword, verifyPassword } from './password-crypto.js'

export type Role = 'ADMIN' | 'SUPERVISOR'
export type AuthUser = { id: string; nome: string; email: string; papel: Role }
export type Device = { id: string; nome: string }

export type WorkerVersionMetadata = {
  id: string
  tag: string
  timestamp: string
}

export type RuntimeBindings = {
  FIRST_ADMIN_SETUP_KEY?: string
  CF_VERSION_METADATA?: WorkerVersionMetadata
  AVATARS?: AvatarBucket
}

export type AppEnv = {
  Bindings: RuntimeBindings
  Variables: {
    user: AuthUser
    device: Device
    requestId: string
  }
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
      // Explicitly bind the password provider instead of relying on conditional
      // package exports chosen by Wrangler/workerd. The implementation keeps
      // Better Auth's existing scrypt parameters and persisted hash format.
      password: {
        hash: hashPassword,
        verify: verifyPassword,
      },
    },
    session: { expiresIn: config.sessionTtlHours * 3600, updateAge: 3600 },
    advanced: { database: { generateId: 'uuid' } },
    // O token gerado pelo Ponto Café já é um segredo aleatório de 256 bits.
    // No modo padrão, o plugin Bearer o converte internamente no cookie assinado
    // que Better Auth espera antes de consultar a sessão.
    plugins: [bearer(), admin()],
  })
}

type AuthInstance = ReturnType<typeof createAuth>
let localAuth: AuthInstance | null = null

export function getAuth(): AuthInstance {
  if (process.env.PONTOCAFE_RUNTIME === 'cloudflare') {
    return createAuth()
  }

  localAuth ??= createAuth()
  return localAuth
}

export const auth = new Proxy({} as AuthInstance, {
  get(_target, property) {
    const instance = getAuth()
    const value = Reflect.get(instance, property, instance)
    return typeof value === 'function' ? value.bind(instance) : value
  },
})

function readBearerToken(value: string | undefined): string | null {
  if (!value) return null
  const match = /^Bearer\s+(.+)$/i.exec(value.trim())
  return match?.[1]?.trim() || null
}

export const requireUser = createMiddleware<AppEnv>(async (c, next) => {
  const token = readBearerToken(c.req.header('Authorization'))
  if (!token) return c.json({ erro: 'Não autenticado.', requestId: c.get('requestId') }, 401)

  const result = await query<{
    id: string
    name: string
    email: string
    role: string | null
    banned: boolean | null
  }>(
    `select u.id,u.name,u.email,u.role,u.banned
       from session s
       join "user" u on u.id=s."userId"
      where s.token=$1 and s."expiresAt">now()
      limit 1`,
    [token],
  )
  const user = result.rows[0]
  if (!user) return c.json({ erro: 'Sessão inválida ou expirada.', requestId: c.get('requestId') }, 401)
  if (user.banned) return c.json({ erro: 'Esta conta está desativada.', requestId: c.get('requestId') }, 403)

  c.set('user', {
    id: user.id,
    nome: user.name,
    email: user.email,
    papel: user.role === 'admin' ? 'ADMIN' : 'SUPERVISOR',
  })
  await next()
})

export function requireRole(...roles: Role[]): MiddlewareHandler<AppEnv> {
  return async (c, next) => {
    if (!roles.includes(c.get('user').papel)) {
      return c.json({ erro: 'Acesso negado.', requestId: c.get('requestId') }, 403)
    }
    await next()
  }
}