import { Hono } from 'hono'
import { z } from 'zod'
import { auth, requireRole, requireUser, type AppEnv } from '../auth-runtime.js'
import { query } from '../db.js'
import { parseJson } from './shared.js'

export const adminRoutes = new Hono<AppEnv>()
adminRoutes.use('*', requireUser, requireRole('ADMIN'))

async function authUser(userId: string) {
  const result = await query<{ id: string; name: string; email: string; role: string | null; banned: boolean | null }>(
    'select id,name,email,role,banned from "user" where id=$1 limit 1',
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

async function auditUser(actorId: string, action: string, entityId: string, details: Record<string, unknown>) {
  await query(
    `insert into auditoria (ator_auth_id,ator_tipo,acao,entidade,entidade_id,detalhes)
     values ($1,'ADMIN',$2,'USUARIO',$3,$4::jsonb)`,
    [actorId, action, entityId, JSON.stringify(details)],
  )
}

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
  await auditUser(adminAtual.id, 'DESATIVAR_CONTA', targetId, {})
  return c.json({ ok: true, ativo: false })
})

adminRoutes.post('/usuarios/:id/reativar', async (c) => {
  const targetId = c.req.param('id')
  const adminAtual = c.get('user')
  const target = await authUser(targetId)
  if (!target) return c.json({ erro: 'Conta não encontrada.' }, 404)

  await auth.api.unbanUser({ body: { userId: targetId }, headers: c.req.raw.headers })
  await auditUser(adminAtual.id, 'REATIVAR_CONTA', targetId, {})
  return c.json({ ok: true, ativo: true })
})

adminRoutes.post('/usuarios/:id/excluir', async (c) => {
  const targetId = c.req.param('id')
  const adminAtual = c.get('user')
  if (targetId === adminAtual.id) return c.json({ erro: 'Você não pode excluir a própria conta.' }, 409)

  const target = await authUser(targetId)
  if (!target) return c.json({ erro: 'Conta não encontrada.' }, 404)
  if (target.role === 'admin' && !(await hasAnotherActiveAdmin(targetId))) {
    return c.json({ erro: 'Não é possível excluir o último administrador ativo.' }, 409)
  }

  await auth.api.removeUser({
    body: { userId: targetId },
    headers: c.req.raw.headers,
  })
  await auditUser(adminAtual.id, 'EXCLUIR_CONTA', targetId, {
    email: target.email,
    nome: target.name,
    perfil: target.role === 'admin' ? 'ADMIN' : 'SUPERVISOR',
  })
  return c.json({ ok: true, excluido: true })
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
  await auditUser(c.get('user').id, 'REDEFINIR_SENHA', targetId, { sessoesRevogadas: true })
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
  await auditUser(adminAtual.id, 'ALTERAR_PERFIL', targetId, { perfil: body.data.perfil })
  return c.json({ ok: true, perfil: body.data.perfil })
})
