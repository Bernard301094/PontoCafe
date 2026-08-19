import { config } from './config.js'
import { query, transaction } from './db.js'

export async function cleanupExpiredDeviceRegistrations() {
  const deleted = await query(
    `delete from device_registration_idempotency
      where expira_em <= now()`,
  )
  return { removed: deleted.rowCount ?? 0 }
}

export async function cleanupExpiredBiometrics() {
  return transaction(async (client) => {
    const deleted = await client.query<{ colaborador_id: string }>(
      `delete from templates_faciais t
        using colaboradores c
        where c.id=t.colaborador_id
          and c.ativo=false
          and c.atualizado_em < now() - ($1::text || ' days')::interval
        returning t.colaborador_id`,
      [config.biometricRetentionDays],
    )

    if ((deleted.rowCount ?? 0) > 0) {
      await client.query(
        `insert into auditoria (ator_tipo,acao,entidade,detalhes)
         values ('SISTEMA','LIMPEZA_RETENCAO_BIOMETRICA','BIOMETRIA',$1::jsonb)`,
        [JSON.stringify({
          retencaoDias: config.biometricRetentionDays,
          removidos: deleted.rowCount ?? 0,
          colaboradorIds: deleted.rows.map((row) => row.colaborador_id),
        })],
      )
    }

    return {
      removed: deleted.rowCount ?? 0,
      retentionDays: config.biometricRetentionDays,
    }
  })
}
