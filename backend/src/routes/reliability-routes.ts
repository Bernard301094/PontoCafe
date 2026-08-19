import { Hono } from 'hono'
import { requireRole, requireUser, type AppEnv } from '../auth-runtime.js'
import { config } from '../config.js'
import { query } from '../db.js'
import { errorPayload, logServerError } from '../observability.js'

export const reliabilityRoutes = new Hono<AppEnv>()
reliabilityRoutes.use('*', requireUser, requireRole('ADMIN'))

reliabilityRoutes.get('/diagnostico', async (c) => {
  const startedAt = Date.now()
  try {
    const database = await query<{
      servidor: string
      colaboradores_ativos: string
      dispositivos_ativos: string
      pausas_abertas: string
      sessoes_ativas: string
    }>(
      `select now()::text as servidor,
              (select count(*) from colaboradores where ativo=true)::text as colaboradores_ativos,
              (select count(*) from dispositivos where ativo=true)::text as dispositivos_ativos,
              (select count(*) from pausas_cafe where fim_em is null)::text as pausas_abertas,
              (select count(*) from session where "expiresAt">now())::text as sessoes_ativas`,
    )
    const row = database.rows[0]

    return c.json({
      status: 'ok',
      requestId: c.get('requestId'),
      banco: {
        status: 'ok',
        latenciaMs: Math.max(0, Date.now() - startedAt),
        servidor: row?.servidor ?? null,
      },
      operacao: {
        colaboradoresAtivos: Number(row?.colaboradores_ativos ?? 0),
        dispositivosAtivos: Number(row?.dispositivos_ativos ?? 0),
        pausasAbertas: Number(row?.pausas_abertas ?? 0),
        sessoesAtivas: Number(row?.sessoes_ativas ?? 0),
      },
      configuracao: {
        timezone: config.appTimezone,
        sessaoHoras: config.sessionTtlHours,
        limiteFacial: config.faceThreshold,
        margemFacial: config.faceIdentificationMargin,
        offlineMaxHoras: config.offlineMaxEventAgeHours,
        retencaoBiometricaDias: config.biometricRetentionDays,
        androidMaisRecente: config.latestAndroidVersion,
        androidMinimo: config.minimumAndroidVersion,
      },
    })
  } catch (error) {
    logServerError(c, 'admin_diagnostic_failure', error)
    return c.json(errorPayload(c, 'Não foi possível concluir o diagnóstico.', 'DIAGNOSTIC_FAILED'), 500)
  }
})
