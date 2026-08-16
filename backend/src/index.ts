import { serve } from '@hono/node-server'
import { Hono } from 'hono'
import { z } from 'zod'
import { Pool } from 'pg'
import { createHash, randomBytes, randomUUID } from 'node:crypto'

type ErrorDetails = unknown
type RegistrationResponse = {
  deviceId: string
  activationToken: string
  expiresAt: string
  requestId: string
}

const app = new Hono()
const databaseUrl = process.env.DATABASE_URL
const pool = databaseUrl
  ? new Pool({
      connectionString: databaseUrl,
      ssl: process.env.NODE_ENV === 'production' ? { rejectUnauthorized: false } : undefined,
    })
  : null

const deviceSchema = z.object({
  deviceId: z.string().trim().min(8).max(128).regex(/^[A-Za-z0-9._:-]+$/),
  name: z.string().trim().min(2).max(80),
  pin: z.string().regex(/^\d{4,12}$/),
})

const getRequestId = (c: any) => c.get('requestId') as string
const hash = (value: string) => createHash('sha256').update(value).digest('hex')

function fail(c: any, status: 400 | 404 | 422 | 500 | 503, code: string, message: string, details?: ErrorDetails) {
  return c.json(
    {
      error: {
        code,
        message,
        requestId: getRequestId(c),
        ...(details === undefined ? {} : { details }),
      },
    },
    status,
  )
}

app.use('*', async (c, next) => {
  const incomingRequestId = c.req.header('x-request-id')?.trim()
  const requestId = incomingRequestId && incomingRequestId.length <= 128 ? incomingRequestId : randomUUID()
  c.set('requestId', requestId)
  c.header('x-request-id', requestId)
  await next()
})

app.get('/', (c) => c.json({ service: 'pontocafe-api', status: 'online', requestId: getRequestId(c) }))

app.get('/health', async (c) => {
  if (!pool) {
    return fail(c, 503, 'DATABASE_NOT_CONFIGURED', 'DATABASE_URL is not configured.')
  }

  try {
    await pool.query('select 1')
    return c.json({ status: 'online', database: 'online', requestId: getRequestId(c) })
  } catch (error) {
    console.error(JSON.stringify({ event: 'health_check_failed', requestId: getRequestId(c), message: error instanceof Error ? error.message : 'unknown_error' }))
    return fail(c, 503, 'DATABASE_UNAVAILABLE', 'Database is temporarily unavailable.')
  }
})

app.post('/devices/register', async (c) => {
  if (!pool) {
    return fail(c, 503, 'DATABASE_NOT_CONFIGURED', 'Device registration is not configured.')
  }

  const idempotencyKey = c.req.header('idempotency-key')?.trim()
  if (!idempotencyKey || idempotencyKey.length < 16 || idempotencyKey.length > 128) {
    return fail(c, 400, 'INVALID_IDEMPOTENCY_KEY', 'Use an Idempotency-Key between 16 and 128 characters.')
  }

  const json = await c.req.json().catch(() => null)
  const parsed = deviceSchema.safeParse(json)
  if (!parsed.success) {
    return fail(c, 422, 'INVALID_DEVICE_REGISTRATION', 'Check the device name, ID, and PIN.', parsed.error.flatten())
  }

  const { deviceId, name, pin } = parsed.data
  const client = await pool.connect()

  try {
    await client.query('begin')

    const replay = await client.query<{ status_code: number; response_body: RegistrationResponse }>(
      'select status_code, response_body from device_registration_requests where idempotency_key = $1 for update',
      [idempotencyKey],
    )

    if (replay.rowCount) {
      await client.query('commit')
      return c.json(replay.rows[0].response_body, replay.rows[0].status_code as 200 | 201)
    }

    const pinHash = hash(`${deviceId}:${pin}`)
    const activationToken = randomBytes(32).toString('base64url')
    const expiresAt = new Date(Date.now() + 15 * 60 * 1000)

    const device = await client.query<{ id: string }>(
      `insert into dispositivos (installation_id, nome, pin_hash, ativo, atualizado_em)
       values ($1, $2, $3, false, now())
       on conflict (installation_id) do update
       set nome = excluded.nome, pin_hash = excluded.pin_hash, ativo = false, atualizado_em = now()
       returning id`,
      [deviceId, name, pinHash],
    )

    const devicePk = device.rows[0].id
    await client.query('delete from device_activation_tokens where dispositivo_id = $1 and usado_em is null', [devicePk])
    await client.query(
      'insert into device_activation_tokens (dispositivo_id, token_hash, expira_em) values ($1, $2, $3)',
      [devicePk, hash(activationToken), expiresAt],
    )

    const body: RegistrationResponse = {
      deviceId,
      activationToken,
      expiresAt: expiresAt.toISOString(),
      requestId: getRequestId(c),
    }

    await client.query(
      'insert into device_registration_requests (idempotency_key, status_code, response_body) values ($1, $2, $3::jsonb)',
      [idempotencyKey, 201, JSON.stringify(body)],
    )

    await client.query('commit')
    return c.json(body, 201)
  } catch (error) {
    await client.query('rollback')
    console.error(JSON.stringify({ event: 'device_registration_failed', requestId: getRequestId(c), message: error instanceof Error ? error.message : 'unknown_error' }))
    return fail(c, 500, 'DEVICE_REGISTER_FAILED', 'Could not create the device. Retry in a few seconds.')
  } finally {
    client.release()
  }
})

app.notFound((c) => fail(c, 404, 'ROUTE_NOT_FOUND', 'Route not found.'))

export default app

if (process.env.VERCEL !== '1') {
  serve({ fetch: app.fetch, port: Number(process.env.PORT || 3000) })
}
