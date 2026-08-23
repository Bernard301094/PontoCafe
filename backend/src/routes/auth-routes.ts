import { Hono } from 'hono'
import { z } from 'zod'
import { getAuth, type AppEnv } from '../auth-runtime.js'
import { config } from '../config.js'
import { query } from '../db.js'
import { isRecognizedPasswordHash } from '../password-crypto.js'
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

function safeAuthLog(
  requestId: string,
  stage: string,
  details: Record<string, boolean | string | null>,
) {
  console.info(JSON.stringify({
    evento: 'auth_login_stage',
    requestId,
    etapa: stage,
    ...details,
  }))
}

async function consumeComparablePasswordWork(password: string) {
  // Keep comparable work for unknown accounts without leaking whether an e-mail
  // exists. The configured provider is the same native scrypt implementation
  // used for account creation and normal password verification.
  const authContext = await getAuth().$context
  await authContext.password.hash(password)
}

authRoutes.post('/sign-in/email', async (c) => {
  const requestId = c.get('requestId')
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
  const hashPresent = Boolean(account?.password)
  const hashFormatSupported = isRecognizedPasswordHash(account?.password)
  const roleRecognized = account?.role === 'admin' || account?.role === 'user'

  safeAuthLog(requestId, 'account_lookup', {
    accountFound: Boolean(account),
    accountActive: account ? !Boolean(account.banned) : null,
    roleRecognized: account ? roleRecognized : null,
    passwordHashPresent: account ? hashPresent : null,
    hashFormatSupported: account ? hashFormatSupported : null,
  })

  if (!account?.password) {
    try {
      await consumeComparablePasswordWork(body.data.password)
    } catch (error) {
      console.error(JSON.stringify({
        evento: 'auth_login_crypto_failure',
        requestId,
        etapa: 'comparable_password_work',
        tipo: error instanceof Error ? error.name : typeof error,
      }))
      return c.json({ erro: 'Autenticação temporariamente indisponível.', codigo: 'AUTH_PASSWORD_RUNTIME' }, 503)
    }
    return c.json({ erro: 'E-mail ou senha inválidos.' }, 401)
  }

  if (!hashFormatSupported) {
    safeAuthLog(requestId, 'password_verification', {
      passwordVerified: false,
      hashFormatSupported: false,
    })
    return c.json({ erro: 'E-mail ou senha inválidos.' }, 401)
  }

  let validPassword = false
  try {
    const authContext = await getAuth().$context
    validPassword = await authContext.password.verify({
      hash: account.password,
      password: body.data.password,
    })
  } catch (error) {
    console.error(JSON.stringify({
      evento: 'auth_login_crypto_failure',
      requestId,
      etapa: 'password_verification',
      hashFormatSupported: true,
      tipo: error instanceof Error ? error.name : typeof error,
    }))
    return c.json({ erro: 'Autenticação temporariamente indisponível.', codigo: 'AUTH_PASSWORD_RUNTIME' }, 503)
  }

  safeAuthLog(requestId, 'password_verification', {
    passwordVerified: validPassword,
    hashFormatSupported: true,
  })

  if (!validPassword) {
    return c.json({ erro: 'E-mail ou senha inválidos.' }, 401)
  }

  if (account.banned) {
    safeAuthLog(requestId, 'account_state', {
      accountActive: false,
      roleRecognized,
    })
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

  safeAuthLog(requestId, 'session_creation', {
    sessionCreated: true,
    accountActive: true,
    roleRecognized,
  })

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
