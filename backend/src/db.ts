import { Pool, type PoolClient, type QueryResultRow } from 'pg'
import { config } from './config.js'

const globalForDb = globalThis as typeof globalThis & { pontoCafePool?: Pool }

export const pool = globalForDb.pontoCafePool ?? new Pool({
  connectionString: config.databaseUrl,
  max: 5,
  idleTimeoutMillis: 20_000,
  connectionTimeoutMillis: 10_000,
})

if (process.env.NODE_ENV !== 'production') globalForDb.pontoCafePool = pool

export async function query<T extends QueryResultRow>(text: string, values: unknown[] = []) {
  return pool.query<T>(text, values)
}

export async function transaction<T>(fn: (client: PoolClient) => Promise<T>): Promise<T> {
  const client = await pool.connect()
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
