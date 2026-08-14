import { AsyncLocalStorage } from 'node:async_hooks'
import { Pool, type PoolClient, type QueryResultRow } from 'pg'
import { config } from './config.js'

type DatabaseScope = { pool: Pool }

const databaseScope = new AsyncLocalStorage<DatabaseScope>()
const cloudflareRuntime = process.env.PONTOCAFE_RUNTIME === 'cloudflare'
let localPool: Pool | null = null

function createPool(connectionString: string, max: number): Pool {
  return new Pool({
    connectionString,
    max,
    idleTimeoutMillis: cloudflareRuntime ? 5_000 : 20_000,
    connectionTimeoutMillis: 10_000,
    allowExitOnIdle: cloudflareRuntime,
  })
}

function currentPool(): Pool {
  const scoped = databaseScope.getStore()?.pool
  if (scoped) return scoped

  if (cloudflareRuntime) {
    throw new Error('Contexto de banco de dados ausente para esta requisição.')
  }

  localPool ??= createPool(config.databaseUrl, 5)
  return localPool
}

/**
 * Cloudflare Workers não permite reutilizar sockets/clients de banco entre
 * invocações. Hyperdrive já mantém o pool de conexões no lado do serviço,
 * então criamos um Pool do driver apenas para a requisição atual.
 */
export async function withRequestDatabase<T>(connectionString: string, fn: () => Promise<T>): Promise<T> {
  const requestPool = createPool(connectionString, 2)
  return databaseScope.run({ pool: requestPool }, async () => {
    try {
      return await fn()
    } finally {
      try {
        await requestPool.end()
      } catch (error) {
        console.error('Falha ao encerrar pool da requisição.', error)
      }
    }
  })
}

export function getPool(): Pool {
  return currentPool()
}

export async function query<T extends QueryResultRow>(text: string, values: unknown[] = []) {
  return currentPool().query<T>(text, values)
}

export async function transaction<T>(fn: (client: PoolClient) => Promise<T>): Promise<T> {
  const client = await currentPool().connect()
  try {
    await client.query('BEGIN')
    const result = await fn(client)
    await client.query('COMMIT')
    return result
  } catch (error) {
    await client.query('ROLLBACK')
    throw error
  } finally {
    client.release()
  }
}
