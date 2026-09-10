import { randomUUID } from 'node:crypto'
import type { Context, MiddlewareHandler } from 'hono'
import type { AppEnv } from './auth-runtime.js'

const requestIdPattern = /^[A-Za-z0-9._-]{8,80}$/
const safeDatabaseIdentifierPattern = /^[A-Za-z0-9_.-]{1,128}$/

export function newRequestId(incoming?: string | null): string {
  const clean = incoming?.trim()
  if (clean && requestIdPattern.test(clean)) return clean
  return `PC-${randomUUID().replaceAll('-', '').slice(0, 12).toUpperCase()}`
}

export function requestIdMiddleware(): MiddlewareHandler<AppEnv> {
  return async (c, next) => {
    const requestId = newRequestId(c.req.header('X-Request-Id'))
    c.set('requestId', requestId)
    const startedAt = Date.now()

    await next()

    c.header('X-Request-Id', requestId)
    c.header('Server-Timing', `app;dur=${Math.max(0, Date.now() - startedAt)}`)
  }
}

export function safeRequestId(c: Context<AppEnv>): string {
  return c.get('requestId') || newRequestId()
}

export function errorPayload(
  c: Context<AppEnv>,
  message: string,
  code: string,
  details: Record<string, unknown> = {},
) {
  return {
    erro: message,
    codigo: code,
    requestId: safeRequestId(c),
    ...details,
  }
}

function safeDatabaseIdentifier(value: unknown): string | null {
  if (typeof value !== 'string') return null
  const clean = value.trim()
  return safeDatabaseIdentifierPattern.test(clean) ? clean : null
}

export function safeErrorDescriptor(error: unknown) {
  const err = error instanceof Error ? error : new Error(String(error))
  const databaseError = err as Error & {
    code?: unknown
    constraint?: unknown
    table?: unknown
    column?: unknown
    routine?: unknown
    severity?: unknown
  }
  const dbCode = typeof databaseError.code === 'string' ? databaseError.code : null
  const message = err.message.toLowerCase()

  const code = dbCode === '42501'
    ? 'DATABASE_PERMISSION'
    : dbCode === '42P01' || dbCode === '42703'
      ? 'DATABASE_SCHEMA'
      : message.includes('timeout') || message.includes('connection') || message.includes('socket') || message.includes('econn')
        ? 'DATABASE_CONNECTION'
        : 'UNEXPECTED_ERROR'

  return {
    code,
    type: err.name,
    databaseCode: dbCode && /^[A-Z0-9]{4,16}$/i.test(dbCode) ? dbCode : null,
    databaseConstraint: safeDatabaseIdentifier(databaseError.constraint),
    databaseTable: safeDatabaseIdentifier(databaseError.table),
    databaseColumn: safeDatabaseIdentifier(databaseError.column),
    databaseRoutine: safeDatabaseIdentifier(databaseError.routine),
    databaseSeverity: safeDatabaseIdentifier(databaseError.severity),
  }
}

export function logServerError(
  c: Context<AppEnv>,
  event: string,
  error: unknown,
  extra: Record<string, unknown> = {},
) {
  console.error(JSON.stringify({
    evento: event,
    requestId: safeRequestId(c),
    ...safeErrorDescriptor(error),
    ...extra,
  }))
}
