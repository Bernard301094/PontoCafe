import { Hono } from 'hono'
import { cors } from 'hono/cors'
import { secureHeaders } from 'hono/secure-headers'
import { auth, type AppEnv } from './auth-runtime.js'
import { query } from './db.js'
import { adminRoutes } from './routes/admin-routes.js'
import { authorizationRoutes } from './routes/authorization-routes.js'
import { liveRoutes } from './routes/live-routes.js'
import { localBiometricRoutes } from './routes/local-biometric-routes.js'
import { pontoRoutes } from './routes/ponto-routes.js'
import { pontoStatusRoutes } from './routes/ponto-status-routes.js'
import { reportRoutes } from './routes/report-routes.js'
import { setupRoutes } from './routes/setup-routes.js'

const app = new Hono<AppEnv>()

app.use('*', secureHeaders())
app.use('*', cors({
  origin: '*',
  allowHeaders: ['Content-Type', 'Authorization', 'X-Device-Token'],
  exposeHeaders: ['set-auth-token'],
  allowMethods: ['GET', 'POST', 'PUT', 'OPTIONS'],
}))

function redactAuthMessage(message: string): string {
  return message
    .replace(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/gi, '[email]')
    .replace(/\b(?:password|senha|secret|token)\s*[:=]\s*[^\s,;}\]]+/gi, (value) => `${value.split(/[:=]/)[0]}=[redacted]`)
    .replace(/\b[A-Za-z0-9+/_=-]{40,}\b/g, '[value]')
    .slice(0, 240)
}

function safeAuthFailure(error: unknown) {
  const err = error instanceof Error ? error : new Error(String(error))
  const cause = err.cause && typeof err.cause === 'object' ? err.cause as Record<string, unknown> : null
  const causeMessage = cause && typeof cause.message === 'string' ? cause.message : ''
  const rawCode = typeof (err as Error & { code?: unknown }).code === 'string'
    ? (err as Error & { code?: string }).code
    : typeof cause?.code === 'string'
      ? cause.code
      : null
  const combined = `${err.message} ${causeMessage}`.toLowerCase()

  let codigo = 'AUTH_RUNTIME_FAILURE'
  if (combined.includes('different request') || combined.includes('different io context')) codigo = 'AUTH_CROSS_REQUEST_IO'
  else if (combined.includes('contexto de banco') || combined.includes('database context')) codigo = 'AUTH_DATABASE_CONTEXT'
  else if (combined.includes('connection') || combined.includes('pool') || combined.includes('socket')) codigo = 'AUTH_DATABASE_CONNECTION'
  else if (combined.includes('column') || combined.includes('relation') || combined.includes('does not exist')) codigo = 'AUTH_DATABASE_SCHEMA'
  else if (combined.includes('permission') || combined.includes('privilege')) codigo = 'AUTH_DATABASE_PERMISSION'
  else if (combined.includes('scrypt') || combined.includes('crypto') || combined.includes('password')) codigo = 'AUTH_PASSWORD_CRYPTO'

  return {
    codigo,
    tipo: err.name,
    codigoInterno: rawCode && /^[A-Z0-9_]{2,16}$/i.test(rawCode) ? rawCode : null,
    mensagem: redactAuthMessage(causeMessage || err.message),
  }
}

app.on(['POST', 'GET'], '/api/auth/*', async (c) => {
  try {
    return await auth.handler(c.req.raw)
  } catch (error) {
    const diagnostico = safeAuthFailure(error)
    console.error(JSON.stringify({ evento: 'better_auth_runtime_failure', ...diagnostico }))
    return c.json({ erro: 'Falha interna de autenticação.', diagnostico }, 500)
  }
})

app.get('/', (c) => c.json({ app: 'Ponto Café API', status: 'ok', versao: '0.5.0' }))
app.get('/health', async (c) => {
  const result = await query<{ agora: string }>('select now()::text as agora')
  return c.json({ status: 'ok', banco: 'ok', servidor: result.rows[0]?.agora })
})

app.route('/setup', setupRoutes)
app.route('/admin', adminRoutes)
app.route('/admin', authorizationRoutes)
app.route('/ponto', localBiometricRoutes)
app.route('/ponto', pontoRoutes)
app.route('/ponto', pontoStatusRoutes)
app.route('/supervisor', liveRoutes)
app.route('/supervisor', reportRoutes)

app.notFound((c) => c.json({ erro: 'Rota não encontrada.' }, 404))
app.onError((error, c) => {
  console.error(error)
  return c.json({ erro: 'Erro interno do servidor.' }, 500)
})

export default app
