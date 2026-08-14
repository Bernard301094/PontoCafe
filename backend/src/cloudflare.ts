type WorkerEnv = {
  HYPERDRIVE?: { connectionString: string }
}

let appPromise: Promise<typeof import('./application.js')> | null = null

async function loadApplication(env: WorkerEnv, request: Request) {
  if (env.HYPERDRIVE?.connectionString) {
    process.env.DATABASE_URL = env.HYPERDRIVE.connectionString
  }

  if (!process.env.BETTER_AUTH_URL) {
    process.env.BETTER_AUTH_URL = new URL(request.url).origin
  }

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
        erro: error instanceof Error ? error.message : String(error),
      }))
      return Response.json(
        { erro: 'Não foi possível iniciar a API do Ponto Café.' },
        { status: 500 },
      )
    }
  },
}
