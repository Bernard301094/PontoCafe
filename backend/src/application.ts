import { Hono } from 'hono'
import { cors } from 'hono/cors'
import { secureHeaders } from 'hono/secure-headers'
import { auth, type AppEnv } from './auth-runtime.js'
import { config } from './config.js'
import { query } from './db.js'
import { errorPayload, logServerError, requestIdMiddleware } from './observability.js'
import { adminBiometricDeletionRoutes } from './routes/admin-biometric-deletion-routes.js'
import { adminRoutes } from './routes/admin-routes.js'
import { auditRoutes } from './routes/audit-routes.js'
import { authRoutes } from './routes/auth-routes.js'
import { authorizationRoutes } from './routes/authorization-routes.js'
import { biometricCalibrationRoutes } from './routes/biometric-calibration-routes.js'
import { coffeeRuleRoutes } from './routes/coffee-rule-routes.js'
import { collaboratorManagementRoutes } from './routes/collaborator-management-routes.js'
import { deviceActivationRoutes } from './routes/device-activation-routes.js'
import { deviceManagementRoutes } from './routes/device-management-routes.js'
import { deviceSetupRoutes } from './routes/device-setup-routes.js'
import { deviceTelemetryRoutes } from './routes/device-telemetry-routes.js'
import { deviceUnlockRoutes } from './routes/device-unlock-routes.js'
import { liveRoutes } from './routes/live-routes.js'
import { localBiometricRoutes } from './routes/local-biometric-routes.js'
import { offlineRoutes } from './routes/offline-routes.js'
import { pontoRoutes } from './routes/ponto-routes.js'
import { pontoStatusRoutes } from './routes/ponto-status-routes.js'
import { reliabilityRoutes } from './routes/reliability-routes.js'
import { reportRoutes } from './routes/report-routes.js'
import { setupRoutes } from './routes/setup-routes.js'
import { userManagementRoutes } from './routes/user-management-routes.js'
import { workforceRoutes } from './routes/workforce-routes.js'

const app = new Hono<AppEnv>()

app.use('*', requestIdMiddleware())
app.use('*', secureHeaders())
app.use('*', cors({
  origin: '*',
  allowHeaders: ['Content-Type', 'Authorization', 'X-Device-Token', 'X-App-Version', 'X-Request-Id', 'Idempotency-Key'],
  exposeHeaders: ['set-auth-token', 'X-Request-Id', 'Server-Timing'],
  allowMethods: ['GET', 'POST', 'PUT', 'OPTIONS'],
}))

function redactAuthMessage(message: string): string {
  return message
    .replace(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/gi, '[email]')
    .replace(/\b(?:password|senha|secret|token)\s*[:=]\s*[^\s,;}\]]+/gi, (value) => `${value.split(/[:=]/)[0]}=[redacted]`)
    .replace(/\b[A-Za-z0-9+/_=-]{40,}\b/g, '[value]')
    .slice(0, 240)
}

function classifyAuthMessage(message: string): string {
  const combined = message.toLowerCase()
  if (combined.includes('different request') || combined.includes('different io context')) return 'AUTH_CROSS_REQUEST_IO'
  if (combined.includes('contexto de banco') || combined.includes('database context')) return 'AUTH_DATABASE_CONTEXT'
  if (combined.includes('connection') || combined.includes('pool') || combined.includes('socket')) return 'AUTH_DATABASE_CONNECTION'
  if (combined.includes('column') || combined.includes('relation') || combined.includes('does not exist')) return 'AUTH_DATABASE_SCHEMA'
  if (combined.includes('permission') || combined.includes('privilege')) return 'AUTH_DATABASE_PERMISSION'
  if (combined.includes('scrypt') || combined.includes('crypto') || combined.includes('password')) return 'AUTH_PASSWORD_CRYPTO'
  return 'AUTH_RUNTIME_FAILURE'
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
  const message = causeMessage || err.message

  return {
    codigo: classifyAuthMessage(message),
    tipo: err.name,
    codigoInterno: rawCode && /^[A-Z0-9_]{2,16}$/i.test(rawCode) ? rawCode : null,
    mensagem: redactAuthMessage(message),
  }
}

async function safeAuthResponseFailure(response: Response) {
  let rawBody = ''
  try {
    rawBody = await response.clone().text()
  } catch {
    rawBody = ''
  }

  const contentType = response.headers.get('content-type') || ''
  const message = rawBody || `Better Auth retornou HTTP ${response.status} sem corpo.`

  return {
    codigo: classifyAuthMessage(message),
    tipo: 'BetterAuthResponse',
    codigoInterno: null,
    statusOriginal: response.status,
    contentType: contentType.slice(0, 80),
    mensagem: redactAuthMessage(message),
  }
}

app.route('/api/auth', authRoutes)

app.on(['POST', 'GET'], '/api/auth/*', async (c) => {
  try {
    const response = await auth.handler(c.req.raw)

    if (response.status >= 500) {
      const diagnostico = await safeAuthResponseFailure(response)
      console.error(JSON.stringify({ evento: 'better_auth_5xx_response', requestId: c.get('requestId'), ...diagnostico }))
      return c.json({ erro: 'Falha interna de autenticação.', requestId: c.get('requestId'), diagnostico }, 500)
    }

    response.headers.set('X-Request-Id', c.get('requestId'))
    return response
  } catch (error) {
    const diagnostico = safeAuthFailure(error)
    console.error(JSON.stringify({ evento: 'better_auth_runtime_failure', requestId: c.get('requestId'), ...diagnostico }))
    return c.json({ erro: 'Falha interna de autenticação.', requestId: c.get('requestId'), diagnostico }, 500)
  }
})

app.get('/', (c) => c.json({ app: 'Ponto Café API', status: 'ok', versao: '0.7.0', requestId: c.get('requestId') }))
app.get('/app-status', (c) => {
  const workerVersion = c.env.CF_VERSION_METADATA
  return c.json({
    apiVersion: '0.7.0',
    backendRevision: config.backendRevision,
    workerVersionId: workerVersion?.id ?? null,
    workerVersionTag: workerVersion?.tag ?? null,
    workerVersionTimestamp: workerVersion?.timestamp ?? null,
    latestAndroidVersion: config.latestAndroidVersion,
    minimumAndroidVersion: config.minimumAndroidVersion,
    timezone: config.appTimezone,
    offlineMaxEventAgeHours: config.offlineMaxEventAgeHours,
    biometricRetentionDays: config.biometricRetentionDays,
    requestId: c.get('requestId'),
  })
})
app.get('/health', async (c) => {
  const startedAt = Date.now()
  const result = await query<{ agora: string }>('select now()::text as agora')
  return c.json({
    status: 'ok',
    banco: 'ok',
    servidor: result.rows[0]?.agora,
    latenciaBancoMs: Math.max(0, Date.now() - startedAt),
    requestId: c.get('requestId'),
  })
})

app.route('/setup', deviceSetupRoutes)
app.route('/setup', setupRoutes)
app.route('/admin', deviceActivationRoutes)
app.route('/admin', deviceManagementRoutes)
app.route('/admin', userManagementRoutes)
app.route('/admin', coffeeRuleRoutes)
app.route('/admin', reliabilityRoutes)
app.route('/admin', adminRoutes)
app.route('/admin', authorizationRoutes)
app.route('/admin', auditRoutes)
// A exclusão biométrica destrutiva é Admin-only e precisa interceptar esta rota
// antes da implementação compatível mantida em collaboratorManagementRoutes.
app.route('/gestao', adminBiometricDeletionRoutes)
// Calibração específica precisa preceder a versão compatível mantida em workforceRoutes.
app.route('/gestao', biometricCalibrationRoutes)
// Rotas específicas (importar/lote/histórico) precisam preceder /colaboradores/:id.
app.route('/gestao', workforceRoutes)
app.route('/gestao', collaboratorManagementRoutes)
app.route('/ponto', deviceUnlockRoutes)
app.route('/ponto', deviceTelemetryRoutes)
app.route('/ponto', localBiometricRoutes)
app.route('/ponto', pontoRoutes)
app.route('/ponto', pontoStatusRoutes)
app.route('/ponto', offlineRoutes)
app.route('/supervisor', liveRoutes)
app.route('/supervisor', reportRoutes)
app.route('/supervisor', authorizationRoutes)

app.notFound((c) => c.json(errorPayload(c, 'Rota não encontrada.', 'ROUTE_NOT_FOUND'), 404))
app.onError((error, c) => {
  logServerError(c, 'unhandled_api_error', error, { path: c.req.path, method: c.req.method })
  return c.json(errorPayload(c, 'Erro interno do servidor.', 'UNEXPECTED_ERROR'), 500)
})

export default app
