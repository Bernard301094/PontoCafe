import { Hono } from 'hono'
import { z } from 'zod'
import { getAuth, type AppEnv } from '../auth-runtime.js'
import { config } from '../config.js'
import { query } from '../db.js'
import { newId, newToken } from '../security.js'
import { parseJson } from './shared.js'

export const authRoutes = new Hono<AppEnv>()

type CredentialRow = {
  id: string
  name: string
  email: string
  role: string | null
  banned: boolean | null
  password: string | null
}

function bearerToken(value: string | undefined): string | null {
  if (!value) return null
  const match = /^Bearer\s+(.+)$/i.exec(value.trim())
  return match?.[1]?.trim() || null
}

async function consumeComparablePasswordWork(password: string) {
  // Better Auth hace lo mismo para evitar diferencias de tiempo evidentes
  // entre un e-mail existente y uno inexistente.
  const authContext = await getAuth().$context
  await authContext.password.hash(password)
}

authRoutes.post('/sign-in/email', async (c) => {
  const body = await parseJson(c, z.object({
    email: z.string().trim().toLowerCase().email().max(254),
    password: z.string().min(1).max(128),
    rememberMe: z.boolean().optional().default(true),
  }))
  if (!body.ok) return body.response

  const result = await query<CredentialRow>(
    `select u.id,u.name,u.email,u.role,u.banned,a.password
       from "user" u
       left join account a
         on a."userId"=u.id and a."providerId"='credential'
      where lower(u.email)=lower($1)
      limit 1`,
    [body.data.email],
  )
  const account = result.rows[0]

  if (!account?.password) {
    await consumeComparablePasswordWork(body.data.password)
    return c.json({ erro: 'E-mail ou senha inválidos.' }, 401)
  }

  const authContext = await getAuth().$context
  const validPassword = await authContext.password.verify({
    hash: account.password,
    password: body.data.password,
  })
  if (!validPassword) {
    return c.json({ erro: 'E-mail ou senha inválidos.' }, 401)
  }

  if (account.banned) {
    return c.json({ erro: 'Esta conta está desativada.' }, 403)
  }

  const now = new Date()
  const lifetimeSeconds = body.data.rememberMe === false
    ? 24 * 60 * 60
    : config.sessionTtlHours * 60 * 60
  const expiresAt = new Date(now.getTime() + lifetimeSeconds * 1000)
  const sessionId = newId()
  const token = newToken()

  await query(
    `insert into session
      (id,"expiresAt",token,"createdAt","updatedAt","ipAddress","userAgent","userId")
     values ($1,$2,$3,$4,$4,$5,$6,$7)`,
    [
      sessionId,
      expiresAt,
      token,
      now,
      c.req.header('cf-connecting-ip') || '',
      c.req.header('user-agent') || '',
      account.id,
    ],
  )

  c.header('set-auth-token', token)
  c.header('Cache-Control', 'no-store')
  return c.json({
    redirect: false,
    token,
    user: {
      id: account.id,
      name: account.name,
      email: account.email,
      role: account.role ?? 'user',
    },
  })
})

authRoutes.post('/sign-out', async (c) => {
  const token = bearerToken(c.req.header('Authorization'))
  if (token) {
    await query('delete from session where token=$1', [token])
  }
  c.header('Cache-Control', 'no-store')
  return c.json({ ok: true })
})
