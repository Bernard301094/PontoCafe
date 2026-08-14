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

let appPromise: Promise<typeof import('./application.js')> | null = null
let bootStage: BootStage = 'environment'

function copyTextBinding(env: WorkerEnv, name: keyof WorkerEnv) {
  const value = env[name]
  if (typeof value === 'string' && value.trim()) {
    process.env[String(name)] = value
  }
}

async function loadApplication(env: WorkerEnv, request: Request) {
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

  if (!process.env.BETTER_AUTH_URL) {
    process.env.BETTER_AUTH_URL = new URL(request.url).origin
  }

  bootStage = 'config'
  await import('./config.js')

  bootStage = 'db'
  await import('./db.js')

  bootStage = 'auth'
  await import('./auth-runtime.js')

  bootStage = 'application'
  appPromise ??= import('./application.js')
  return appPromise
}

export default {
  async fetch(request: Request, env: WorkerEnv): Promise<Response> {
    try {
      const { default: app } = await loadApplication(env, request)
      return await app.fetch(request)
    } catch (error) {
      console.error(JSON.stringify({
        evento: 'worker_boot_failure',
        etapa: bootStage,
        erro: error instanceof Error ? error.message : String(error),
      }))
      return Response.json(
        {
          erro: 'Não foi possível iniciar a API do Ponto Café.',
          etapa: bootStage,
        },
        { status: 500 },
      )
    }
  },
}
