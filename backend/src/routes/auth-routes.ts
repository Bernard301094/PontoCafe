import { Hono } from 'hono'
import { z } from 'zod'
import { getAuth, type AppEnv } from '../auth-runtime.js'
import { config } from '../config.js'
import { query, transaction } from '../db.js'
import { isRecognizedPasswordHash } from '../password-crypto.js'
import { newId, newToken } from '../security.js'
import { isStrongPassword } from '../supervisor-onboarding.js'
import { parseJson } from './shared.js'

export const authRoutes = new Hono<AppEnv>()

type CredentialRow = {
  id: string
  name: string
  email: string
  role: string | null
  banned: boolean | null
  password: string | null
  turno: string | null
  mustChangePassword: boolean
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
    `select u.id,u.name,u.email,u.role,u.banned,u.turno,
            u."mustChangePassword" as "mustChangePassword",a.password
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
    return c.json({ erro: 'Esta conta está desativada.', codigo: 'AUTH_ACCOUNT_DISABLED' }, 403)
  }

  if (!roleRecognized) {
    safeAuthLog(requestId, 'account_state', {
      accountActive: true,
      roleRecognized: false,
    })
    return c.json({ erro: 'Perfil de acesso inválido.', codigo: 'AUTH_ROLE_INVALID' }, 403)
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
    roleRecognized: true,
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
      role: account.role,
      turno: account.turno,
      mustChangePassword: account.role === 'user' && account.mustChangePassword,
    },
  })
})

authRoutes.post('/change-temporary-password', async (c) => {
  const requestId = c.get('requestId')
  const token = bearerToken(c.req.header('Authorization'))
  if (!token) {
    return c.json({ erro: 'Sessão necessária para trocar a senha.', codigo: 'AUTH_SESSION_REQUIRED' }, 401)
  }

  const body = await parseJson(c, z.object({
    newPassword: z.string().min(10).max(128),
  }))
  if (!body.ok) return body.response
  if (!isStrongPassword(body.data.newPassword)) {
    return c.json({
      erro: 'A nova senha deve ter pelo menos 10 caracteres e combinar letras e números.',
      codigo: 'PASSWORD_POLICY',
    }, 400)
  }

  const current = await query<{
    id: string
    role: string | null
    banned: boolean | null
    mustChangePassword: boolean
    password: string | null
  }>(
    `select u.id,u.role,u.banned,u."mustChangePassword" as "mustChangePassword",a.password
       from session s
       join "user" u on u.id=s."userId"
       left join account a on a."userId"=u.id and a."providerId"='credential'
      where s.token=$1 and s."expiresAt">now()
      limit 1`,
    [token],
  )
  const user = current.rows[0]
  if (!user) return c.json({ erro: 'Sessão inválida ou expirada.', codigo: 'AUTH_SESSION_INVALID' }, 401)
  if (user.banned) return c.json({ erro: 'Esta conta está desativada.', codigo: 'AUTH_ACCOUNT_DISABLED' }, 403)
  if (user.role !== 'user') {
    return c.json({ erro: 'Esta troca de senha é exclusiva do primeiro acesso do Supervisor.', codigo: 'PASSWORD_CHANGE_NOT_APPLICABLE' }, 403)
  }
  if (!user.mustChangePassword) {
    return c.json({ erro: 'A troca obrigatória de senha já foi concluída.', codigo: 'PASSWORD_CHANGE_ALREADY_COMPLETED' }, 409)
  }
  if (!user.password || !isRecognizedPasswordHash(user.password)) {
    return c.json({ erro: 'A credencial da conta está inválida.', codigo: 'AUTH_CREDENTIAL_INVALID' }, 409)
  }

  const authContext = await getAuth().$context
  const sameAsTemporary = await authContext.password.verify({
    hash: user.password,
    password: body.data.newPassword,
  })
  if (sameAsTemporary) {
    return c.json({
      erro: 'Escolha uma senha diferente da senha temporária.',
      codigo: 'PASSWORD_MUST_DIFFER',
    }, 400)
  }

  const newPasswordHash = await authContext.password.hash(body.data.newPassword)

  await transaction(async (client) => {
    await client.query(
      `update account
          set password=$1,"updatedAt"=now()
        where "userId"=$2 and "providerId"='credential'`,
      [newPasswordHash, user.id],
    )
    await client.query(
      `update "user"
          set "mustChangePassword"=false,"updatedAt"=now()
        where id=$1`,
      [user.id],
    )
    // Mantém apenas a sessão autenticada que realizou a troca. Assim a tela pode
    // seguir diretamente para o Supervisor sem exigir a senha nova novamente.
    await client.query('delete from session where "userId"=$1 and token<>$2', [user.id, token])
    await client.query(
      `insert into auditoria (ator_auth_id,ator_tipo,acao,entidade,entidade_id,detalhes)
       values ($1,'SUPERVISOR','TROCAR_SENHA_INICIAL','USUARIO',$1,$2::jsonb)`,
      [user.id, JSON.stringify({ trocaObrigatoriaConcluida: true, outrasSessoesRevogadas: true })],
    )
  })

  safeAuthLog(requestId, 'initial_password_change', {
    sessionCreated: true,
    accountActive: true,
    roleRecognized: true,
  })
  c.header('Cache-Control', 'no-store')
  return c.json({ ok: true, mustChangePassword: false })
})

authRoutes.post('/sign-out', async (c) => {
  const token = bearerToken(c.req.header('Authorization'))
  if (token) {
    await query('delete from session where token=$1', [token])
  }
  c.header('Cache-Control', 'no-store')
  return c.json({ ok: true })
})
