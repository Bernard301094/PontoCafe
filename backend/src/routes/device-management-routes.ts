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
    ultimoAcessoEm: string
    pinConfigurado: boolean
  }>(
    `select d.id,d.nome,d.ativo,
            d.criado_em::text as "criadoEm",
            d.atualizado_em::text as "atualizadoEm",
            greatest(
              d.atualizado_em,
              coalesce((select max(p.inicio_em) from pausas_cafe p where p.dispositivo_inicio_id=d.id),d.criado_em),
              coalesce((select max(p.fim_em) from pausas_cafe p where p.dispositivo_fim_id=d.id),d.criado_em)
            )::text as "ultimoAcessoEm",
            (d.unlock_pin_hash is not null) as "pinConfigurado"
       from dispositivos d
      order by d.ativo desc,d.nome`,
  )
  return c.json({ dispositivos: result.rows })
})

deviceManagementRoutes.put('/devices/:id/unlock-pin', async (c) => {
  const deviceId = c.req.param('id')
  if (!uuidSchema.safeParse(deviceId).success) return c.json({ erro: 'Dispositivo inválido.' }, 400)

  const body = await parseJson(c, z.object({ pin: pinSchema }))
  if (!body.ok) return body.response

  const device = await transaction(async (client) => {
    const updated = await client.query<{ id: string; nome: string }>(
      `update dispositivos
          set unlock_pin_hash=$2,
              unlock_pin_updated_at=now(),
              unlock_fail_count=0,
              unlock_locked_until=null,
              atualizado_em=now()
        where id=$1 and ativo=true
        returning id,nome`,
      [deviceId, hashDeviceUnlockPin(deviceId, body.data.pin)],
    )
    const row = updated.rows[0]
    if (!row) return null
    await auditDevice(client, c.get('user').id, 'ALTERAR_PIN_DISPOSITIVO', deviceId, { nome: row.nome })
    return row
  })
  if (!device) return c.json({ erro: 'Dispositivo não encontrado ou inativo.' }, 404)

  return c.json({ ok: true, dispositivoId: device.id, nome: device.nome, pinConfigurado: true })
})

deviceManagementRoutes.put('/devices/:id/nome', async (c) => {
  const deviceId = c.req.param('id')
  if (!uuidSchema.safeParse(deviceId).success) return c.json({ erro: 'Dispositivo inválido.' }, 400)
  const body = await parseJson(c, z.object({ nome: nameSchema }))
  if (!body.ok) return body.response

  const result = await transaction(async (client) => {
    const previous = await client.query<{ nome: string }>(
      'select nome from dispositivos where id=$1 for update',
      [deviceId],
    )
    const currentName = previous.rows[0]?.nome
    if (!currentName) return null

    const updated = await client.query<{ id: string; nome: string; ativo: boolean }>(
      `update dispositivos set nome=$2,atualizado_em=now() where id=$1 returning id,nome,ativo`,
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
    const updated = await client.query<{ id: string; nome: string }>(
      `update dispositivos
          set ativo=false,
              unlock_fail_count=0,
              unlock_locked_until=null,
              atualizado_em=now()
        where id=$1 and ativo=true
        returning id,nome`,
      [deviceId],
    )
    const row = updated.rows[0]
    if (!row) return null
    await auditDevice(client, c.get('user').id, 'DESATIVAR_DISPOSITIVO', deviceId, { nome: row.nome })
    return row
  })
  if (!device) return c.json({ erro: 'Dispositivo não encontrado ou já está inativo.' }, 404)

  return c.json({ ok: true, dispositivoId: device.id, nome: device.nome, ativo: false })
})

deviceManagementRoutes.post('/devices/:id/novo-token', async (c) => {
  const deviceId = c.req.param('id')
  if (!uuidSchema.safeParse(deviceId).success) return c.json({ erro: 'Dispositivo inválido.' }, 400)

  const activationToken = newDeviceToken(10)
  const device = await transaction(async (client) => {
    const updated = await client.query<{ id: string; nome: string }>(
      `update dispositivos
          set token_hash=$2,
              ativo=true,
              unlock_fail_count=0,
              unlock_locked_until=null,
              atualizado_em=now()
        where id=$1
        returning id,nome`,
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
    aviso: 'O token anterior foi revogado. Use este código de 10 caracteres para ativar novamente o dispositivo.',
  })
})

deviceManagementRoutes.post('/devices/:id/excluir', async (c) => {
  const deviceId = c.req.param('id')
  if (!uuidSchema.safeParse(deviceId).success) return c.json({ erro: 'Dispositivo inválido.' }, 400)

  const result = await transaction(async (client) => {
    const found = await client.query<{ id: string; nome: string }>(
      'select id,nome from dispositivos where id=$1 for update',
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
      return { status: 'HAS_HISTORY' as const, totalHistory }
    }

    // Verificações faciais são temporárias e podem ser removidas junto com um
    // aparelho nunca usado em pausas. O histórico de ponto, quando existe,
    // bloqueia a exclusão física para preservar rastreabilidade.
    await client.query('delete from verificacoes_faciais where dispositivo_id=$1', [deviceId])
    await auditDevice(client, c.get('user').id, 'EXCLUIR_DISPOSITIVO', deviceId, {
      nome: device.nome,
      exclusaoPermanente: true,
    })
    await client.query('delete from dispositivos where id=$1', [deviceId])
    return { status: 'OK' as const, nome: device.nome }
  })

  if (result.status === 'NOT_FOUND') return c.json({ erro: 'Dispositivo não encontrado.' }, 404)
  if (result.status === 'HAS_HISTORY') {
    return c.json({
      erro: `Este dispositivo possui ${result.totalHistory} registro(s) histórico(s) de pausa. Para preservar a auditoria, desative-o em vez de excluí-lo.`,
      codigo: 'DISPOSITIVO_COM_HISTORICO',
      registrosHistoricos: result.totalHistory,
    }, 409)
  }

  return c.json({ ok: true, dispositivoId: deviceId, nome: result.nome, excluido: true })
})
