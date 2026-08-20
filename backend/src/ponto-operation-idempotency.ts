import type { PoolClient } from 'pg'

export type PontoOperationType = 'REGISTRO_RAPIDO'

export type PontoOperationIdentity = {
  operationId: string
  deviceId: string
  collaboratorId: string
  type: PontoOperationType
}

type PontoOperationRow = {
  operacao_id: string
  dispositivo_id: string
  colaborador_id: string
  tipo: PontoOperationType
  pausa_id: string | null
  resposta: unknown
}

export type StoredPontoOperation<T> = {
  operationId: string
  pauseId: string | null
  response: T
}

export class PontoOperationConflictError extends Error {
  constructor() {
    super('O identificador desta operação já foi utilizado por outro registro do Ponto.')
    this.name = 'PontoOperationConflictError'
  }
}

/**
 * Serializa concorrência pelo par dispositivo/operação sem exigir uma linha
 * pré-existente. O lock é transacional e some automaticamente no COMMIT/ROLLBACK.
 */
export async function lockPontoOperation(
  client: PoolClient,
  operationId: string,
  deviceId: string,
): Promise<void> {
  await client.query(
    'select pg_advisory_xact_lock(hashtextextended($1, 0))',
    [`pontocafe:ponto:${deviceId}:${operationId}`],
  )
}

export async function findPontoOperation<T>(
  client: PoolClient,
  identity: PontoOperationIdentity,
): Promise<StoredPontoOperation<T> | null> {
  const result = await client.query<PontoOperationRow>(
    `select operacao_id,dispositivo_id,colaborador_id,tipo,pausa_id,resposta
       from operacoes_ponto_idempotentes
      where operacao_id=$1
      limit 1`,
    [identity.operationId],
  )
  const row = result.rows[0]
  if (!row) return null

  if (
    row.dispositivo_id !== identity.deviceId ||
    row.colaborador_id !== identity.collaboratorId ||
    row.tipo !== identity.type
  ) {
    throw new PontoOperationConflictError()
  }

  return {
    operationId: row.operacao_id,
    pauseId: row.pausa_id,
    response: row.resposta as T,
  }
}

export async function savePontoOperation<T extends object>(
  client: PoolClient,
  identity: PontoOperationIdentity,
  pauseId: string | null,
  response: T,
): Promise<StoredPontoOperation<T>> {
  await client.query(
    `insert into operacoes_ponto_idempotentes
       (operacao_id,dispositivo_id,colaborador_id,tipo,pausa_id,resposta)
     values ($1,$2,$3,$4,$5,$6::jsonb)
     on conflict (operacao_id) do nothing`,
    [
      identity.operationId,
      identity.deviceId,
      identity.collaboratorId,
      identity.type,
      pauseId,
      JSON.stringify(response),
    ],
  )

  const stored = await findPontoOperation<T>(client, identity)
  if (!stored) throw new Error('Falha ao persistir a identidade da operação do Ponto.')
  return stored
}
