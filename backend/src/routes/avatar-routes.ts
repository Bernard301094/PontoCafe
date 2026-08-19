import { Hono } from 'hono'
import { createMiddleware } from 'hono/factory'
import { requireRole, requireUser, type AppEnv, type Device } from '../auth-runtime.js'
import {
  AVATAR_MAX_BYTES,
  avatarObjectKey,
  avatarUrl,
  isWebP,
  validateAvatarSignature,
} from '../avatar-storage.js'
import { query } from '../db.js'
import { hashToken } from '../security.js'
import { uuidSchema } from './shared.js'

const requireDevice = createMiddleware<AppEnv>(async (c, next) => {
  const token = c.req.header('X-Device-Token')?.trim()
  if (!token) return c.json({ erro: 'Dispositivo não autenticado.' }, 401)

  const result = await query<Device>(
    'select id,nome from dispositivos where token_hash=$1 and ativo=true limit 1',
    [hashToken(token)],
  )
  const device = result.rows[0]
  if (!device) return c.json({ erro: 'Dispositivo inválido.' }, 401)

  c.set('device', device)
  await next()
})

export const avatarManagementRoutes = new Hono<AppEnv>()
avatarManagementRoutes.use('*', requireUser, requireRole('ADMIN', 'SUPERVISOR'))

// Esta rota vem antes da lista legada de colaboradores e a enriquece com avatar.
// A foto em si nunca sai do R2 para o banco: somente avatar_version é lido aqui.
avatarManagementRoutes.get('/colaboradores', async (c) => {
  const result = await query<{
    id: string
    nome: string
    setor: string | null
    turno: string | null
    ativo: boolean
    rostoCadastrado: boolean
    avatarVersion: number
  }>(
    `select col.id,col.nome,col.setor,col.turno,col.ativo,
            col.avatar_version as "avatarVersion",
            exists(select 1 from templates_faciais t where t.colaborador_id=col.id) as "rostoCadastrado"
       from colaboradores col
      where col.ativo=true
      order by col.nome`,
  )
  const origin = new URL(c.req.url).origin
  return c.json({
    colaboradores: result.rows.map((row) => ({
      id: row.id,
      nome: row.nome,
      setor: row.setor,
      turno: row.turno,
      ativo: row.ativo,
      rostoCadastrado: row.rostoCadastrado,
      avatarUrl: avatarUrl(origin, row.id, row.avatarVersion),
    })),
  })
})

avatarManagementRoutes.put('/colaboradores/:id/avatar', async (c) => {
  const collaboratorId = c.req.param('id')
  if (!uuidSchema.safeParse(collaboratorId).success) {
    return c.json({ erro: 'Colaborador inválido.' }, 400)
  }

  const bucket = c.env.AVATARS
  if (!bucket) {
    return c.json({ erro: 'Armazenamento de avatar ainda não está configurado.' }, 503)
  }

  const current = await query<{ id: string; nome: string; avatar_version: number }>(
    'select id,nome,avatar_version from colaboradores where id=$1 and ativo=true limit 1',
    [collaboratorId],
  )
  const collaborator = current.rows[0]
  if (!collaborator) return c.json({ erro: 'Colaborador não encontrado ou inativo.' }, 404)

  const contentType = c.req.header('content-type')?.split(';')[0]?.trim().toLowerCase()
  if (contentType !== 'image/webp') {
    return c.json({ erro: 'O avatar precisa ser enviado em WebP otimizado.' }, 415)
  }

  const declaredLength = Number(c.req.header('content-length') || '0')
  if (Number.isFinite(declaredLength) && declaredLength > AVATAR_MAX_BYTES) {
    return c.json({ erro: `Avatar muito grande. Limite: ${AVATAR_MAX_BYTES} bytes.` }, 413)
  }

  const payload = await c.req.arrayBuffer()
  if (payload.byteLength <= 0) return c.json({ erro: 'A imagem enviada está vazia.' }, 400)
  if (payload.byteLength > AVATAR_MAX_BYTES) {
    return c.json({ erro: `Avatar muito grande. Limite: ${AVATAR_MAX_BYTES} bytes.` }, 413)
  }
  if (!isWebP(new Uint8Array(payload))) {
    return c.json({ erro: 'O arquivo enviado não é um WebP válido.' }, 400)
  }

  await bucket.put(avatarObjectKey(collaboratorId), payload, {
    httpMetadata: {
      contentType: 'image/webp',
      cacheControl: 'private, max-age=31536000, immutable',
    },
  })

  const updated = await query<{ avatar_version: number }>(
    `update colaboradores
        set avatar_version=case
              when avatar_version>=2147483646 then 1
              else avatar_version+1
            end,
            atualizado_em=now()
      where id=$1 and ativo=true
      returning avatar_version`,
    [collaboratorId],
  )
  const version = updated.rows[0]?.avatar_version
  if (!version) return c.json({ erro: 'Não foi possível atualizar o avatar do colaborador.' }, 409)

  const actor = c.get('user')
  await query(
    `insert into auditoria (ator_auth_id,ator_tipo,acao,entidade,entidade_id,detalhes)
     values ($1,$2,'ATUALIZAR_AVATAR','COLABORADOR',$3,$4::jsonb)`,
    [actor.id, actor.papel, collaboratorId, JSON.stringify({ bytes: payload.byteLength, formato: 'WEBP' })],
  )

  return c.json({
    ok: true,
    colaboradorId: collaboratorId,
    avatarUrl: avatarUrl(new URL(c.req.url).origin, collaboratorId, version),
    bytes: payload.byteLength,
  })
})

avatarManagementRoutes.post('/colaboradores/:id/avatar/excluir', async (c) => {
  const collaboratorId = c.req.param('id')
  if (!uuidSchema.safeParse(collaboratorId).success) {
    return c.json({ erro: 'Colaborador inválido.' }, 400)
  }

  const bucket = c.env.AVATARS
  if (!bucket) {
    return c.json({ erro: 'Armazenamento de avatar ainda não está configurado.' }, 503)
  }

  const updated = await query<{ id: string }>(
    `update colaboradores
        set avatar_version=0,atualizado_em=now()
      where id=$1 and ativo=true
      returning id`,
    [collaboratorId],
  )
  if (!updated.rows[0]) return c.json({ erro: 'Colaborador não encontrado ou inativo.' }, 404)

  // Se a remoção física falhar depois daqui, a imagem já ficou inacessível porque
  // avatar_version=0 não produz URL assinada. O objeto órfão pode ser removido depois.
  try {
    await bucket.delete(avatarObjectKey(collaboratorId))
  } catch (error) {
    console.error(JSON.stringify({
      evento: 'avatar_r2_delete_failure',
      colaboradorId: collaboratorId,
      erro: error instanceof Error ? error.message : 'erro desconhecido',
    }))
  }

  const actor = c.get('user')
  await query(
    `insert into auditoria (ator_auth_id,ator_tipo,acao,entidade,entidade_id,detalhes)
     values ($1,$2,'EXCLUIR_AVATAR','COLABORADOR',$3,'{}'::jsonb)`,
    [actor.id, actor.papel, collaboratorId],
  )

  return c.json({ ok: true, colaboradorId: collaboratorId, avatarUrl: null })
})

export const avatarPontoRoutes = new Hono<AppEnv>()
avatarPontoRoutes.use('*', requireDevice)

// Catálogo minúsculo separado do catálogo biométrico. Isso evita colocar bytes de
// imagem junto dos embeddings e permite atualizar o avatar sem alterar biometria.
avatarPontoRoutes.get('/avatares', async (c) => {
  const result = await query<{ id: string; avatarVersion: number }>(
    `select id,avatar_version as "avatarVersion"
       from colaboradores
      where ativo=true and avatar_version>0
      order by id`,
  )
  const origin = new URL(c.req.url).origin
  return c.json({
    avatares: result.rows.map((row) => ({
      colaboradorId: row.id,
      avatarUrl: avatarUrl(origin, row.id, row.avatarVersion),
    })),
  })
})

export const avatarMediaRoutes = new Hono<AppEnv>()

avatarMediaRoutes.get('/avatars/:id', async (c) => {
  const collaboratorId = c.req.param('id')
  if (!uuidSchema.safeParse(collaboratorId).success) return c.body(null, 404)

  const version = Number(c.req.query('v') || '0')
  const signature = c.req.query('sig') || ''
  if (!validateAvatarSignature(collaboratorId, version, signature)) return c.body(null, 404)

  const metadata = await query<{ avatar_version: number }>(
    'select avatar_version from colaboradores where id=$1 and ativo=true limit 1',
    [collaboratorId],
  )
  if (metadata.rows[0]?.avatar_version !== version) return c.body(null, 404)

  const bucket = c.env.AVATARS
  if (!bucket) return c.body(null, 503)

  const stored = await bucket.get(avatarObjectKey(collaboratorId))
  if (!stored) return c.body(null, 404)

  const bytes = await stored.arrayBuffer()
  return new Response(bytes, {
    status: 200,
    headers: {
      'Content-Type': stored.httpMetadata?.contentType || 'image/webp',
      'Cache-Control': 'private, max-age=31536000, immutable',
      ...(stored.etag ? { ETag: stored.etag } : {}),
    },
  })
})