import { Hono } from 'hono'
import { z } from 'zod'
import { auth, requireRole, requireUser, type AppEnv } from '../auth-runtime.js'
import { query } from '../db.js'
import { encryptEmbedding, hashToken, newId, newToken } from '../security.js'
import { embeddingSchema, parseJson, uuidSchema } from './shared.js'

export const adminRoutes = new Hono<AppEnv>()
adminRoutes.use('*', requireUser, requireRole('ADMIN'))

async function authUser(userId: string) {
  const result = await query<{ id: string; role: string | null; banned: boolean | null }>(
    'select id,role,banned from "user" where id=$1 limit 1',
    [userId],
  )
  return result.rows[0] ?? null
}

async function hasAnotherActiveAdmin(userId: string): Promise<boolean> {
  const result = await query<{ total: string }>(
    `select count(*)::text as total from "user"
     where id<>$1 and role='admin' and coalesce(banned,false)=false`,
    [userId],
  )
  return Number(result.rows[0]?.total ?? 0) > 0
}

async function audit(actorId: string, action: string, entityId: string, details: Record<string, unknown>) {
  await query(
    `insert into auditoria (ator_auth_id,ator_tipo,acao,entidade,entidade_id,detalhes)
     values ($1,'ADMIN',$2,'USUARIO',$3,$4::jsonb)`,
    [actorId, action, entityId, JSON.stringify(details)],
  )
}

adminRoutes.post('/usuarios', async (c) => {
  const body = await parseJson(c, z.object({
    nome: z.string().trim().min(2).max(120),
    email: z.string().trim().toLowerCase().email().max(254),
    senha: z.string().min(10).max(128),
    perfil: z.enum(['SUPERVISOR', 'ADMIN']).default('SUPERVISOR'),
  }))
  if (!body.ok) return body.response

  const adminAtual = c.get('user')
  const role = body.data.perfil === 'ADMIN' ? 'admin' : 'user'

  try {
    const created = await auth.api.createUser({
      body: {
        name: body.data.nome,
        email: body.data.email,
        password: body.data.senha,
        role,
      },
    })

    await audit(adminAtual.id, 'CRIAR_CONTA', created.user.id, {
      email: created.user.email,
      nome: created.user.name,
      perfil: body.data.perfil,
    })

    return c.json({
      usuario: {
        id: created.user.id,
        nome: created.user.name,
        email: created.user.email,
        perfil: body.data.perfil,
      },
    }, 201)
  } catch (error) {
    const message = error instanceof Error ? error.message.toLowerCase() : ''
    if (message.includes('email') || message.includes('already') || message.includes('unique')) {
      return c.json({ erro: 'Já existe uma conta cadastrada com este e-mail.' }, 409)
    }
    throw error
  }
})

adminRoutes.get('/usuarios', async (c) => {
  const result = await query<{
    id: string
    name: string
    email: string
    role: string | null
    banned: boolean | null
    createdAt: string
  }>(
    `select id,name,email,role,banned,"createdAt"::text as "createdAt"
     from "user"
     order by "createdAt" desc`,
  )

  return c.json({
    usuarios: result.rows.map((user) => ({
      id: user.id,
      nome: user.name,
      email: user.email,
      perfil: user.role === 'admin' ? 'ADMIN' : 'SUPERVISOR',
      ativo: !user.banned,
      criadoEm: user.createdAt,
    })),
  })
})

adminRoutes.post('/usuarios/:id/bloquear', async (c) => {
  const targetId = c.req.param('id')
  const adminAtual = c.get('user')
  if (targetId === adminAtual.id) return c.json({ erro: 'Você não pode desativar a própria conta.' }, 409)

  const target = await authUser(targetId)
  if (!target) return c.json({ erro: 'Conta não encontrada.' }, 404)
  if (target.role === 'admin' && !(await hasAnotherActiveAdmin(targetId))) {
    return c.json({ erro: 'Não é possível desativar o último administrador ativo.' }, 409)
  }

  await auth.api.banUser({
    body: { userId: targetId, banReason: 'Conta desativada pelo administrador.' },
    headers: c.req.raw.headers,
  })
  await audit(adminAtual.id, 'DESATIVAR_CONTA', targetId, {})
  return c.json({ ok: true, ativo: false })
})

adminRoutes.post('/usuarios/:id/reativar', async (c) => {
  const targetId = c.req.param('id')
  const adminAtual = c.get('user')
  const target = await authUser(targetId)
  if (!target) return c.json({ erro: 'Conta não encontrada.' }, 404)

  await auth.api.unbanUser({ body: { userId: targetId }, headers: c.req.raw.headers })
  await audit(adminAtual.id, 'REATIVAR_CONTA', targetId, {})
  return c.json({ ok: true, ativo: true })
})

adminRoutes.put('/usuarios/:id/senha', async (c) => {
  const targetId = c.req.param('id')
  const body = await parseJson(c, z.object({ novaSenha: z.string().min(10).max(128) }))
  if (!body.ok) return body.response
  const target = await authUser(targetId)
  if (!target) return c.json({ erro: 'Conta não encontrada.' }, 404)

  await auth.api.setUserPassword({
    body: { userId: targetId, newPassword: body.data.novaSenha },
    headers: c.req.raw.headers,
  })
  await auth.api.revokeUserSessions({ body: { userId: targetId }, headers: c.req.raw.headers })
  await audit(c.get('user').id, 'REDEFINIR_SENHA', targetId, { sessoesRevogadas: true })
  return c.json({ ok: true, sessoesRevogadas: true })
})

adminRoutes.put('/usuarios/:id/perfil', async (c) => {
  const targetId = c.req.param('id')
  const body = await parseJson(c, z.object({ perfil: z.enum(['SUPERVISOR', 'ADMIN']) }))
  if (!body.ok) return body.response
  const adminAtual = c.get('user')
  const target = await authUser(targetId)
  if (!target) return c.json({ erro: 'Conta não encontrada.' }, 404)

  if (targetId === adminAtual.id && body.data.perfil !== 'ADMIN') {
    return c.json({ erro: 'Você não pode remover o próprio perfil de administrador.' }, 409)
  }
  if (target.role === 'admin' && body.data.perfil === 'SUPERVISOR' && !(await hasAnotherActiveAdmin(targetId))) {
    return c.json({ erro: 'Não é possível rebaixar o último administrador ativo.' }, 409)
  }

  const role = body.data.perfil === 'ADMIN' ? 'admin' : 'user'
  await auth.api.setRole({ body: { userId: targetId, role }, headers: c.req.raw.headers })
  await audit(adminAtual.id, 'ALTERAR_PERFIL', targetId, { perfil: body.data.perfil })
  return c.json({ ok: true, perfil: body.data.perfil })
})

adminRoutes.post('/dispositivos', async (c) => {
  const body = await parseJson(c, z.object({ nome: z.string().trim().min(2).max(120) }))
  if (!body.ok) return body.response
  const id = newId()
  const token = newToken()
  await query('insert into dispositivos (id,nome,token_hash) values ($1,$2,$3)', [id, body.data.nome, hashToken(token)])
  return c.json({ id, nome: body.data.nome, token, aviso: 'Este token é exibido uma única vez.' }, 201)
})

adminRoutes.get('/colaboradores', async (c) => {
  const result = await query('select id,matricula,nome,setor,turno,ativo,criado_em from colaboradores order by nome')
  return c.json({ colaboradores: result.rows })
})

adminRoutes.post('/colaboradores', async (c) => {
  const body = await parseJson(c, z.object({
    matricula: z.string().trim().max(50).optional().nullable(),
    nome: z.string().trim().min(2).max(160),
    setor: z.string().trim().max(120).optional().nullable(),
    turno: z.string().trim().max(80).optional().nullable(),
  }))
  if (!body.ok) return body.response
  const id = newId()
  await query('insert into colaboradores (id,matricula,nome,setor,turno) values ($1,$2,$3,$4,$5)', [id, body.data.matricula ?? null, body.data.nome, body.data.setor ?? null, body.data.turno ?? null])
  return c.json({ id, ...body.data, ativo: true }, 201)
})

adminRoutes.put('/colaboradores/:id/biometria', async (c) => {
  const colaboradorId = c.req.param('id')
  if (!uuidSchema.safeParse(colaboradorId).success) return c.json({ erro: 'Colaborador inválido.' }, 400)
  const body = await parseJson(c, z.object({ embedding: embeddingSchema, modelo: z.string().trim().min(2).max(100), versaoModelo: z.string().trim().min(1).max(50) }))
  if (!body.ok) return body.response
  const encrypted = encryptEmbedding(body.data.embedding)
  await query(`insert into templates_faciais (id,colaborador_id,template_cifrado,iv,auth_tag,dimensao,modelo,versao_modelo)
    values ($1,$2,$3,$4,$5,$6,$7,$8)
    on conflict (colaborador_id) do update set template_cifrado=excluded.template_cifrado,iv=excluded.iv,auth_tag=excluded.auth_tag,dimensao=excluded.dimensao,modelo=excluded.modelo,versao_modelo=excluded.versao_modelo,atualizado_em=now()`,
    [newId(), colaboradorId, encrypted.ciphertext, encrypted.iv, encrypted.authTag, body.data.embedding.length, body.data.modelo, body.data.versaoModelo])
  return c.json({ ok: true, colaboradorId, dimensao: body.data.embedding.length })
})
