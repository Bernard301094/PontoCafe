type WorkerEnv = {
  HYPERDRIVE?: { connectionString: string }
  BETTER_AUTH_SECRET?: string
  CODE_PEPPER?: string
  BIOMETRIC_MASTER_KEY?: string
  FIRST_ADMIN_SETUP_KEY?: string
  APP_TIMEZONE?: string
  SESSION_TTL_HOURS?: string
  FACE_MATCH_THRESHOLD?: string
  FACE_IDENTIFICATION_MARGIN?: string
  AUTHORIZATION_TTL_SECONDS?: string
  FACE_VERIFICATION_TTL_SECONDS?: string
}

type BootStage = 'environment' | 'config' | 'db' | 'auth' | 'application'

let bootStage: BootStage = 'environment'

function copyTextBinding(env: WorkerEnv, name: keyof WorkerEnv) {
  const value = env[name]
  if (typeof value === 'string' && value.trim()) {
    process.env[String(name)] = value
  }
}

function assertRequiredRuntimeConfig() {
  const required = ['DATABASE_URL', 'CODE_PEPPER', 'BIOMETRIC_MASTER_KEY'] as const
  const missing = required.filter((name) => !process.env[name]?.trim())
  if (missing.length > 0) {
    throw new Error(`Configuração ausente: ${missing.join(', ')}`)
  }
}

function safeBindingDiagnostics(env: WorkerEnv) {
  const known = [
    'HYPERDRIVE',
    'BETTER_AUTH_SECRET',
    'CODE_PEPPER',
    'BIOMETRIC_MASTER_KEY',
    'FIRST_ADMIN_SETUP_KEY',
    'APP_TIMEZONE',
    'SESSION_TTL_HOURS',
    'FACE_MATCH_THRESHOLD',
    'FACE_IDENTIFICATION_MARGIN',
    'AUTHORIZATION_TTL_SECONDS',
    'FACE_VERIFICATION_TTL_SECONDS',
  ] as const

  return {
    nomes: Object.keys(env).sort(),
    presentes: Object.fromEntries(
      known.map((name) => [name, Object.prototype.hasOwnProperty.call(env, name)]),
    ),
    tipos: Object.fromEntries(
      known.map((name) => [name, typeof env[name as keyof WorkerEnv]]),
    ),
  }
}

async function handleApplication(env: WorkerEnv, request: Request): Promise<Response> {
  bootStage = 'environment'
  process.env.PONTOCAFE_RUNTIME = 'cloudflare'

  if (!env.HYPERDRIVE?.connectionString) {
    throw new Error('HYPERDRIVE binding ausente.')
  }
  process.env.DATABASE_URL = env.HYPERDRIVE.connectionString

  const textBindings: Array<keyof WorkerEnv> = [
    'BETTER_AUTH_SECRET',
    'CODE_PEPPER',
    'BIOMETRIC_MASTER_KEY',
    'FIRST_ADMIN_SETUP_KEY',
    'APP_TIMEZONE',
    'SESSION_TTL_HOURS',
    'FACE_MATCH_THRESHOLD',
    'FACE_IDENTIFICATION_MARGIN',
    'AUTHORIZATION_TTL_SECONDS',
    'FACE_VERIFICATION_TTL_SECONDS',
  ]
  for (const name of textBindings) copyTextBinding(env, name)

  process.env.BETTER_AUTH_URL = new URL(request.url).origin

  bootStage = 'config'
  assertRequiredRuntimeConfig()
  await import('./config.js')

  bootStage = 'db'
  const { withRequestDatabase } = await import('./db.js')

  return withRequestDatabase(env.HYPERDRIVE.connectionString, async () => {
    bootStage = 'auth'
    await import('./auth-runtime.js')

    bootStage = 'application'
    const { default: app } = await import('./application.js')
    return app.fetch(request, env)
  })
}

export default {
  async fetch(request: Request, env: WorkerEnv): Promise<Response> {
    try {
      return await handleApplication(env, request)
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error)
      const bindings = safeBindingDiagnostics(env)
      console.error(JSON.stringify({
        evento: 'worker_boot_failure',
        etapa: bootStage,
        erro: message,
        bindings,
      }))

      const response: Record<string, unknown> = {
        erro: 'Não foi possível iniciar a API do Ponto Café.',
        etapa: bootStage,
      }
      if (bootStage === 'environment' || bootStage === 'config' || bootStage === 'auth') {
        response.detalhe = message
        response.bindings = bindings
      }

      return Response.json(response, { status: 500 })
    }
  },
}
