import { Hono } from 'hono'
import { requireRole, requireUser, type AppEnv } from '../auth-runtime.js'
import { config } from '../config.js'
import { query } from '../db.js'
import { errorPayload, logServerError } from '../observability.js'

export const reliabilityRoutes = new Hono<AppEnv>()
reliabilityRoutes.use('*', requireUser, requireRole('ADMIN'))

type DiagnosticCounters = {
  servidor: string
  colaboradores_ativos: string
  dispositivos_ativos: string
  pausas_abertas: string
  sessoes_ativas: string
  pausas_24h: string
  operacoes_24h: string
  registro_rapido_24h: string
  inicios_24h: string
  retornos_24h: string
}

type FleetRow = {
  id: string
  nome: string
  ativo: boolean
  ultimoAcessoEm: string | null
  telemetriaEm: string | null
  detalhes: Record<string, unknown> | null
}

function parseCounter(value: unknown): number {
  const parsed = Number(value ?? 0)
  return Number.isFinite(parsed) && parsed >= 0 ? Math.trunc(parsed) : 0
}

function parseTelemetryNumber(value: unknown): number {
  const parsed = Number(value ?? 0)
  return Number.isFinite(parsed) && parsed >= 0 ? Math.trunc(parsed) : 0
}

function versionParts(value: string): number[] {
  if (!/^\d+\.\d+\.\d+$/.test(value.trim())) return []
  return value.split('.').map((part) => Number(part))
}

function compareVersions(left: string, right: string): number {
  const a = versionParts(left)
  const b = versionParts(right)
  if (a.length === 0 || b.length === 0) return 0
  for (let index = 0; index < Math.max(a.length, b.length); index += 1) {
    const delta = (a[index] ?? 0) - (b[index] ?? 0)
    if (delta !== 0) return delta
  }
  return 0
}

function recentHealthAlert(details: Record<string, unknown> | null): boolean {
  if (!details) return false
  const cutoff = Date.now() - 24 * 60 * 60 * 1000
  const lastCrash = parseTelemetryNumber(details.lastCrashMillis)
  const lastStall = parseTelemetryNumber(details.lastStallMillis)
  return lastCrash >= cutoff || lastStall >= cutoff
}

reliabilityRoutes.get('/diagnostico', async (c) => {
  const startedAt = Date.now()
  try {
    const database = await query<DiagnosticCounters>(
      `select now()::text as servidor,
              (select count(*) from colaboradores where ativo=true)::text as colaboradores_ativos,
              (select count(*) from dispositivos where ativo=true)::text as dispositivos_ativos,
              (select count(*) from pausas_cafe where fim_em is null)::text as pausas_abertas,
              (select count(*) from session where "expiresAt">now())::text as sessoes_ativas,
              (select count(*) from pausas_cafe where inicio_em>=now()-interval '24 hours')::text as pausas_24h,
              (select count(*) from operacoes_ponto_idempotentes where criado_em>=now()-interval '24 hours')::text as operacoes_24h,
              (select count(*) from operacoes_ponto_idempotentes where criado_em>=now()-interval '24 hours' and tipo='REGISTRO_RAPIDO')::text as registro_rapido_24h,
              (select count(*) from operacoes_ponto_idempotentes where criado_em>=now()-interval '24 hours' and tipo='INICIAR')::text as inicios_24h,
              (select count(*) from operacoes_ponto_idempotentes where criado_em>=now()-interval '24 hours' and tipo='FINALIZAR')::text as retornos_24h`,
    )
    const row = database.rows[0]

    const fleet = await query<FleetRow>(
      `select d.id,
              d.nome,
              d.ativo,
              greatest(
                d.atualizado_em,
                coalesce((select max(p.inicio_em) from pausas_cafe p where p.dispositivo_inicio_id=d.id),d.criado_em),
                coalesce((select max(p.fim_em) from pausas_cafe p where p.dispositivo_fim_id=d.id),d.criado_em)
              )::text as "ultimoAcessoEm",
              h.criado_em::text as "telemetriaEm",
              h.detalhes
         from dispositivos d
         left join lateral (
           select a.criado_em,a.detalhes
             from auditoria a
            where a.acao='APP_HEALTH'
              and a.entidade='DISPOSITIVO'
              and a.entidade_id::text=d.id::text
            order by a.criado_em desc
            limit 1
         ) h on true
        where d.ativo=true
        order by d.nome`,
    )

    const activeDevices = fleet.rows.map((device) => {
      const details = device.detalhes ?? {}
      const appVersion = typeof details.appVersion === 'string' ? details.appVersion : null
      const outdated = appVersion != null && compareVersions(appVersion, config.latestAndroidVersion) < 0
      const healthAlert = recentHealthAlert(device.detalhes)
      return {
        id: device.id,
        nome: device.nome,
        ativo: device.ativo,
        ultimoAcessoEm: device.ultimoAcessoEm,
        telemetriaEm: device.telemetriaEm,
        appVersion,
        deviceModel: typeof details.deviceModel === 'string' ? details.deviceModel : null,
        androidVersion: typeof details.androidVersion === 'string' ? details.androidVersion : null,
        crashCount: parseTelemetryNumber(details.crashCount),
        stallCount: parseTelemetryNumber(details.stallCount),
        alertaSaude: healthAlert,
        desatualizado: outdated,
      }
    })

    const telemetryRecentCutoff = Date.now() - 24 * 60 * 60 * 1000
    const withRecentTelemetry = activeDevices.filter((device) => {
      const timestamp = device.telemetriaEm ? Date.parse(device.telemetriaEm) : Number.NaN
      return Number.isFinite(timestamp) && timestamp >= telemetryRecentCutoff
    }).length

    return c.json({
      status: 'ok',
      requestId: c.get('requestId'),
      banco: {
        status: 'ok',
        latenciaMs: Math.max(0, Date.now() - startedAt),
        servidor: row?.servidor ?? null,
      },
      operacao: {
        colaboradoresAtivos: parseCounter(row?.colaboradores_ativos),
        dispositivosAtivos: parseCounter(row?.dispositivos_ativos),
        pausasAbertas: parseCounter(row?.pausas_abertas),
        sessoesAtivas: parseCounter(row?.sessoes_ativas),
      },
      integridade: {
        pausasUltimas24h: parseCounter(row?.pausas_24h),
        operacoesProtegidasUltimas24h: parseCounter(row?.operacoes_24h),
        registroRapidoUltimas24h: parseCounter(row?.registro_rapido_24h),
        iniciosUltimas24h: parseCounter(row?.inicios_24h),
        retornosUltimas24h: parseCounter(row?.retornos_24h),
      },
      frota: {
        totalAtivos: activeDevices.length,
        comTelemetriaRecente: withRecentTelemetry,
        semTelemetriaRecente: Math.max(0, activeDevices.length - withRecentTelemetry),
        desatualizados: activeDevices.filter((device) => device.desatualizado).length,
        alertasSaude: activeDevices.filter((device) => device.alertaSaude).length,
        dispositivos: activeDevices,
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
