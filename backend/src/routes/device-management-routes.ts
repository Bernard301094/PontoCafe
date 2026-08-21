import { Hono } from 'hono'
import type { PoolClient } from 'pg'
import { z } from 'zod'
import { requireRole, requireUser, type AppEnv } from '../auth-runtime.js'
import { query, transaction } from '../db.js'
import { hashDeviceUnlockPin, hashToken, newDeviceToken } from '../security.js'
import { parseJson, uuidSchema } from './shared.js'

export const deviceManagementRoutes = new Hono<AppEnv>()
deviceManagementRoutes.use('*', requireUser, requireRole('ADMIN'))

const pinSchema = z.string().trim().regex(/^\d{4,12}$/, 'O PIN deve ter entre 4 e 12 números.')
const nameSchema = z.string().trim().min(2).max(120)

function telemetryCounter(value: unknown): number {
  const parsed = Number(value ?? 0)
  return Number.isFinite(parsed) && parsed >= 0 ? Math.trunc(parsed) : 0
}

function hasRecentHealthAlert(details: Record<string, unknown> | null): boolean {
  if (!details) return false
  const cutoff = Date.now() - 24 * 60 * 60 * 1000
  return telemetryCounter(details.lastCrashMillis) >= cutoff || telemetryCounter(details.lastStallMillis) >= cutoff
}

async function auditDevice(
  client: PoolClient,
  actorId: string,
  action: string,
  deviceId: string,
  details: Record<string, unknown>,
) {
  await client.query(
    `insert into auditoria (ator_auth_id,ator_tipo,acao,entidade,entidade_id,detalhes)
     values ($1,'ADMIN',$2,'DISPOSITIVO',$3,$4::jsonb)`,
    [actorId, action, deviceId, JSON.stringify(details)],
  )
}

deviceManagementRoutes.get('/devices', async (c) => {
  const result = await query<{
    id: string
    nome: string
    ativo: boolean
    criadoEm: string
    atualizadoEm: string
    ultimoAcessoEm: string | null
    pinConfigurado: boolean
    telemetriaEm: string | null
    telemetriaDetalhes: Record<string, unknown> | null
    ultimaAtivacaoEm: string | null
    ultimaRotacaoEm: string | null
    aguardandoAtivacao: boolean
    registrosHistoricos: number
  }>(
    `select d.id,d.nome,d.ativo,
            d.criado_em::text as "criadoEm",
            d.atualizado_em::text as "atualizadoEm",
            greatest(
              d.criado_em,
              coalesce((select max(p.inicio_em) from pausas_cafe p where p.dispositivo_inicio_id=d.id),d.criado_em),
              coalesce((select max(p.fim_em) from pausas_cafe p where p.dispositivo_fim_id=d.id),d.criado_em),
              coalesce(h.criado_em,d.criado_em),
              coalesce(activation.ultima_ativacao_em,d.criado_em)
            )::text as "ultimoAcessoEm",
            (d.unlock_pin_hash is not null) as "pinConfigurado",
            h.criado_em::text as "telemetriaEm",
            h.detalhes as "telemetriaDetalhes",
            activation.ultima_ativacao_em::text as "ultimaAtivacaoEm",
            rotation.ultima_rotacao_em::text as "ultimaRotacaoEm",
            (
              activation.ultima_ativacao_em is null or
              rotation.ultima_rotacao_em>activation.ultima_ativacao_em
            ) as "aguardandoAtivacao",
            history.total as "registrosHistoricos"
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
       left join lateral (
         select max(a.criado_em) as ultima_ativacao_em
           from auditoria a
          where a.acao='ATIVAR_DISPOSITIVO'
            and a.entidade='DISPOSITIVO'
            and a.entidade_id::text=d.id::text
       ) activation on true
       left join lateral (
         select max(a.criado_em) as ultima_rotacao_em
           from auditoria a
          where a.acao='ROTACIONAR_TOKEN_DISPOSITIVO'
            and a.entidade='DISPOSITIVO'
            and a.entidade_id::text=d.id::text
       ) rotation on true
       left join lateral (
         select count(*)::int as total
           from pausas_cafe p
          where p.dispositivo_inicio_id=d.id or p.dispositivo_fim_id=d.id
       ) history on true
      where not exists (
        select 1
          from auditoria archived
         where archived.acao='ARQUIVAR_DISPOSITIVO'
           and archived.entidade='DISPOSITIVO'
           and archived.entidade_id::text=d.id::text
      )
      order by d.ativo desc,d.nome`,
  )
  return c.json({
    dispositivos: result.rows.map((device) => {
      const details = device.telemetriaDetalhes ?? {}

      return {
        id: device.id,
        nome: device.nome,
        ativo: device.ativo,
        criadoEm: device.criadoEm,
        atualizadoEm: device.atualizadoEm,
        ultimoAcessoEm: device.ultimoAcessoEm,
        pinConfigurado: device.pinConfigurado,
        statusAtivacao: !device.ativo ? 'INATIVO' : device.aguardandoAtivacao ? 'AGUARDANDO_ATIVACAO' : 'ATIVADO',
        ativadoEm: device.ultimaAtivacaoEm,
        telemetriaEm: device.telemetriaEm,
        appVersion: typeof details.appVersion === 'string' ? details.appVersion : null,
        deviceModel: typeof details.deviceModel === 'string' ? details.deviceModel : null,
        androidVersion: typeof details.androidVersion === 'string' ? details.androidVersion : null,
        crashCount: telemetryCounter(details.crashCount),
        stallCount: telemetryCounter(details.stallCount),
        alertaSaude: hasRecentHealthAlert(device.telemetriaDetalhes),
        registrosHistoricos: device.registrosHistoricos,
        exclusaoPermanentePermitida: device.registrosHistoricos === 0,
      }
    }),
  })
})

deviceManagementRoutes.put('/devices/:id/unlock-pin', async (c) => {
  const deviceId = c.req.param('id')
  if (!uuidSchema.safeParse(deviceId).success) return c.json({ erro: 'Dispositivo inválido.' }, 400)

  const body = await parseJson(c, z.object({ pin: pinSchema }))
  if (!body.ok) return body.response

  const result = await query<{ id: string; nome: string }>(
    `with dispositivo_atualizado as (
       update dispositivos d
          set unlock_pin_hash=$2,
              unlock_pin_updated_at=now(),
              unlock_fail_count=0,
              unlock_locked_until=null,
              atualizado_em=now()
        where d.id=$1
          and not exists (
            select 1
              from auditoria archived
             where archived.acao='ARQUIVAR_DISPOSITIVO'
               and archived.entidade='DISPOSITIVO'
               and archived.entidade_id::text=d.id::text
          )
        returning d.id,d.nome
     ),
     auditoria_pin as (
       insert into auditoria (ator_auth_id,ator_tipo,acao,entidade,entidade_id,detalhes)
       select $3,'ADMIN','ALTERAR_PIN_DISPOSITIVO','DISPOSITIVO',d.id::text,
              jsonb_build_object('nome',d.nome)
         from dispositivo_atualizado d
       returning id
     )
     select d.id,d.nome
       from dispositivo_atualizado d
       join auditoria_pin a on true`,
    [deviceId, hashDeviceUnlockPin(deviceId, body.data.pin), c.get('user').id],
  )
  const device = result.rows[0]
  if (!device) return c.json({ erro: 'Dispositivo não encontrado.' }, 404)

  return c.json({ ok: true, dispositivoId: device.id, nome: device.nome, pinConfigurado: true })
})

deviceManagementRoutes.put('/devices/:id/nome', async (c) => {
  const deviceId = c.req.param('id')
  if (!uuidSchema.safeParse(deviceId).success) return c.json({ erro: 'Dispositivo inválido.' }, 400)
  const body = await parseJson(c, z.object({ nome: nameSchema }))
  if (!body.ok) return body.response

  const result = await transaction(async (client) => {
    const previous = await client.query<{ nome: string }>(
      `select d.nome
         from dispositivos d
        where d.id=$1
          and not exists (
            select 1
              from auditoria archived
             where archived.acao='ARQUIVAR_DISPOSITIVO'
               and archived.entidade='DISPOSITIVO'
               and archived.entidade_id::text=d.id::text
          )
        for update`,
      [deviceId],
    )
    const currentName = previous.rows[0]?.nome
    if (!currentName) return null

    const updated = await client.query<{ id: string; nome: string; ativo: boolean }>(
      'update dispositivos set nome=$2,atualizado_em=now() where id=$1 returning id,nome,ativo',
      [deviceId, body.data.nome],
    )
    const device = updated.rows[0]
    if (!device) return null

    await auditDevice(client, c.get('user').id, 'RENOMEAR_DISPOSITIVO', deviceId, {
      nomeAnterior: currentName,
      nomeNovo: device.nome,
    })
    return device
  })
  if (!result) return c.json({ erro: 'Dispositivo não encontrado.' }, 404)

  return c.json({ ok: true, dispositivo: result })
})

deviceManagementRoutes.post('/devices/:id/desativar', async (c) => {
  const deviceId = c.req.param('id')
  if (!uuidSchema.safeParse(deviceId).success) return c.json({ erro: 'Dispositivo inválido.' }, 400)

  const device = await transaction(async (client) => {
    const found = await client.query<{ id: string; nome: string; ativo: boolean }>(
      `select d.id,d.nome,d.ativo
         from dispositivos d
        where d.id=$1
          and not exists (
            select 1
              from auditoria archived
             where archived.acao='ARQUIVAR_DISPOSITIVO'
               and archived.entidade='DISPOSITIVO'
               and archived.entidade_id::text=d.id::text
          )
        for update`,
      [deviceId],
    )
    const current = found.rows[0]
    if (!current) return null

    if (current.ativo) {
      await client.query(
        `update dispositivos
            set ativo=false,
                unlock_fail_count=0,
                unlock_locked_until=null,
                atualizado_em=now()
          where id=$1`,
        [deviceId],
      )
      await auditDevice(client, c.get('user').id, 'DESATIVAR_DISPOSITIVO', deviceId, {
        nome: current.nome,
        acessoBloqueado: true,
      })
    }

    return current
  })
  if (!device) return c.json({ erro: 'Dispositivo não encontrado.' }, 404)

  return c.json({
    ok: true,
    dispositivoId: device.id,
    nome: device.nome,
    ativo: false,
    jaEstavaInativo: !device.ativo,
  })
})

deviceManagementRoutes.post('/devices/:id/novo-token', async (c) => {
  const deviceId = c.req.param('id')
  if (!uuidSchema.safeParse(deviceId).success) return c.json({ erro: 'Dispositivo inválido.' }, 400)

  const activationToken = newDeviceToken(10)
  const device = await transaction(async (client) => {
    const updated = await client.query<{ id: string; nome: string }>(
      `update dispositivos d
          set token_hash=$2,
              ativo=true,
              unlock_fail_count=0,
              unlock_locked_until=null,
              atualizado_em=now()
        where d.id=$1
          and not exists (
            select 1
              from auditoria archived
             where archived.acao='ARQUIVAR_DISPOSITIVO'
               and archived.entidade='DISPOSITIVO'
               and archived.entidade_id::text=d.id::text
          )
        returning d.id,d.nome`,
      [deviceId, hashToken(activationToken)],
    )
    const row = updated.rows[0]
    if (!row) return null

    await auditDevice(client, c.get('user').id, 'ROTACIONAR_TOKEN_DISPOSITIVO', deviceId, {
      nome: row.nome,
      tokenAnteriorRevogado: true,
    })
    return row
  })
  if (!device) return c.json({ erro: 'Dispositivo não encontrado.' }, 404)

  return c.json({
    ok: true,
    dispositivoId: device.id,
    nome: device.nome,
    token: activationToken,
    ativo: true,
    aviso: 'O token anterior foi revogado. Use este novo token de 10 caracteres para ativar novamente o dispositivo.',
  })
})

deviceManagementRoutes.post('/devices/:id/excluir', async (c) => {
  const deviceId = c.req.param('id')
  if (!uuidSchema.safeParse(deviceId).success) return c.json({ erro: 'Dispositivo inválido.' }, 400)

  const result = await transaction(async (client) => {
    const found = await client.query<{ id: string; nome: string }>(
      `select d.id,d.nome
         from dispositivos d
        where d.id=$1
          and not exists (
            select 1
              from auditoria archived
             where archived.acao='ARQUIVAR_DISPOSITIVO'
               and archived.entidade='DISPOSITIVO'
               and archived.entidade_id::text=d.id::text
          )
        for update`,
      [deviceId],
    )
    const device = found.rows[0]
    if (!device) return { status: 'NOT_FOUND' as const }

    const history = await client.query<{ total: number }>(
      `select count(*)::int as total
         from pausas_cafe
        where dispositivo_inicio_id=$1 or dispositivo_fim_id=$1`,
      [deviceId],
    )
    const totalHistory = history.rows[0]?.total ?? 0

    if (totalHistory > 0) {
      // A exclusão administrativa não pode quebrar as FKs do histórico de ponto.
      // O aparelho é removido da gestão ativa e bloqueado, enquanto os registros
      // históricos permanecem íntegros e auditáveis.
      await client.query(
        `update dispositivos
            set ativo=false,
                unlock_fail_count=0,
                unlock_locked_until=null,
                atualizado_em=now()
          where id=$1`,
        [deviceId],
      )
      await client.query(
        'delete from verificacoes_faciais where dispositivo_id=$1 and usado_em is null',
        [deviceId],
      )
      await auditDevice(client, c.get('user').id, 'ARQUIVAR_DISPOSITIVO', deviceId, {
        nome: device.nome,
        registrosHistoricos: totalHistory,
        removidoDaGestao: true,
        historicoPreservado: true,
      })
      return { status: 'ARCHIVED' as const, nome: device.nome, totalHistory }
    }

    // Sem histórico de pausa, a exclusão física é segura.
    await client.query('delete from verificacoes_faciais where dispositivo_id=$1', [deviceId])
    await auditDevice(client, c.get('user').id, 'EXCLUIR_DISPOSITIVO', deviceId, {
      nome: device.nome,
      exclusaoPermanente: true,
    })
    await client.query('delete from dispositivos where id=$1', [deviceId])
    return { status: 'DELETED' as const, nome: device.nome, totalHistory: 0 }
  })

  if (result.status === 'NOT_FOUND') return c.json({ erro: 'Dispositivo não encontrado.' }, 404)
  if (result.status === 'ARCHIVED') {
    return c.json({
      ok: true,
      dispositivoId: deviceId,
      nome: result.nome,
      excluido: true,
      arquivado: true,
      registrosHistoricos: result.totalHistory,
      aviso: 'Dispositivo removido da gestão. O histórico de ponto foi preservado para auditoria.',
    })
  }

  return c.json({
    ok: true,
    dispositivoId: deviceId,
    nome: result.nome,
    excluido: true,
    arquivado: false,
    registrosHistoricos: 0,
  })
})
