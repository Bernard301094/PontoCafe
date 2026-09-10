import { config } from './config.js'
import { query, transaction } from './db.js'

export async function cleanupExpiredDeviceRegistrations() {
  const deleted = await query(
    `delete from device_registration_idempotency
      where expira_em <= now()`,
  )
  return { removed: deleted.rowCount ?? 0 }
}

export async function cleanupExpiredPontoOperations() {
  const deleted = await query(
    `delete from operacoes_ponto_idempotentes
      where concluido_em < now() - ($1::text || ' days')::interval`,
    [config.pontoOperationRetentionDays],
  )
  return {
    removed: deleted.rowCount ?? 0,
    retentionDays: config.pontoOperationRetentionDays,
  }
}

export async function cleanupExpiredDeviceHealthTelemetry() {
  const deleted = await query(
    `delete from auditoria
      where acao in ('APP_HEALTH','DEVICE_HEARTBEAT')
        and entidade='DISPOSITIVO'
        and criado_em < now() - ($1::text || ' days')::interval`,
    [config.deviceHealthRetentionDays],
  )
  return {
    removed: deleted.rowCount ?? 0,
    retentionDays: config.deviceHealthRetentionDays,
  }
}

/**
 * Retenção dos códigos de acesso já esgotados.
 *
 * Só saem códigos que não têm mais nada a dizer: cancelados, já usados no
 * retorno, ou que expiraram sem ninguém os apresentar. Um código com saída
 * registada e sem retorno NUNCA é removido — é a única coisa que ainda permite
 * fechar aquela pausa, por mais antiga que seja.
 */
export async function cleanupExpiredAccessCodes() {
  return transaction(async (client) => {
    const deleted = await client.query<{ id: string }>(
      `delete from codigos_acesso
        where criado_em < now() - ($1::text || ' days')::interval
          and (
            cancelado_em is not null
            or retorno_em is not null
            or (saida_em is null and expira_em <= now())
          )
        returning id`,
      [config.accessCodeRetentionDays],
    )

    if ((deleted.rowCount ?? 0) > 0) {
      await client.query(
        `insert into auditoria (ator_tipo,acao,entidade,detalhes)
         values ('SISTEMA','LIMPEZA_RETENCAO_CODIGOS','CODIGO_ACESSO',$1::jsonb)`,
        [JSON.stringify({
          retencaoDias: config.accessCodeRetentionDays,
          removidos: deleted.rowCount ?? 0,
        })],
      )
    }

    return {
      removed: deleted.rowCount ?? 0,
      retentionDays: config.accessCodeRetentionDays,
    }
  })
}

/** Tentativas de código erradas só interessam dentro da janela de bloqueio. */
export async function cleanupAccessCodeAttempts() {
  const deleted = await query(
    `delete from auditoria
      where acao='CODIGO_ACESSO_TENTATIVA_INVALIDA'
        and criado_em < now() - ($1::text || ' days')::interval`,
    [config.accessCodeRetentionDays],
  )
  return { removed: deleted.rowCount ?? 0, retentionDays: config.accessCodeRetentionDays }
}
