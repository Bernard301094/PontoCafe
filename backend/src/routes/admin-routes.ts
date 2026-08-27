import { Hono } from 'hono'
import { z } from 'zod'
import { auth, requireRole, requireUser, type AppEnv } from '../auth-runtime.js'
import { query } from '../db.js'
import { generateTemporaryPassword } from '../supervisor-onboarding.js'
import { parseJson } from './shared.js'

export const adminRoutes = new Hono<AppEnv>()
adminRoutes.use('*', requireUser, requireRole('ADMIN'))

type ManagedUser = {
  id: string
  name: string
  email: string
  role: string | null
  banned: boolean | null
  turno: string | null
  mustChangePassword: boolean
}

async function authUser(userId: string): Promise<ManagedUser | null> {
  const result = await query<ManagedUser>(
    `select id,name,email,role,banned,turno,
            "mustChangePassword" as "mustChangePassword"
       from "user" where id=$1 limit 1`,
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
    turno: string | null
    mustChangePassword: boolean
  }>(
    `select id,name,email,role,banned,"createdAt"::text as "createdAt",turno,
            "mustChangePassword" as "mustChangePassword"
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
      turno: user.role === 'user' ? user.turno : null,
      trocaSenhaPendente: user.role === 'user' && user.mustChangePassword,
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
    turno: target.turno,
  })
  return c.json({ ok: true, excluido: true })
})

adminRoutes.put('/usuarios/:id/senha', async (c) => {
  const targetId = c.req.param('id')
  const body = await parseJson(c, z.object({
    novaSenha: z.string().min(10).max(128).optional(),
  }))
  if (!body.ok) return body.response

  const target = await authUser(targetId)
  if (!target) return c.json({ erro: 'Conta não encontrada.' }, 404)

  const supervisor = target.role === 'user'
  // Clientes novos geram a senha temporária no Android para que o Admin possa
  // copiá-la antes de enviar. Clientes antigos continuam seguros: o Worker gera
  // um fallback criptográfico quando novaSenha não vier no request.
  const temporaryPassword = supervisor
    ? (body.data.novaSenha ?? generateTemporaryPassword())
    : null
  const newPassword = temporaryPassword ?? body.data.novaSenha
  if (!newPassword) {
    return c.json({ erro: 'Informe a nova senha do Administrador.' }, 400)
  }

  await auth.api.setUserPassword({
    body: { userId: targetId, newPassword },
    headers: c.req.raw.headers,
  })
  await query(
    `update "user"
        set "mustChangePassword"=$2,"updatedAt"=now()
      where id=$1`,
    [targetId, supervisor],
  )
  await auth.api.revokeUserSessions({ body: { userId: targetId }, headers: c.req.raw.headers })
  await auditUser(c.get('user').id, 'REDEFINIR_SENHA', targetId, {
    sessoesRevogadas: true,
    senhaTemporariaGerada: supervisor,
    trocaSenhaObrigatoria: supervisor,
  })

  c.header('Cache-Control', 'no-store')
  return c.json({
    ok: true,
    sessoesRevogadas: true,
    trocaSenhaObrigatoria: supervisor,
    senhaTemporaria: temporaryPassword,
  })
})

adminRoutes.put('/usuarios/:id/perfil', async (c) => {
  const targetId = c.req.param('id')
  const body = await parseJson(c, z.object({
    perfil: z.enum(['SUPERVISOR', 'ADMIN']),
    turno: z.enum(['A', 'B', 'C', 'D']).optional().nullable(),
  }).superRefine((value, ctx) => {
    if (value.perfil === 'SUPERVISOR' && !value.turno) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['turno'], message: 'Informe o turno do Supervisor.' })
    }
  }))
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

  const becomingSupervisor = body.data.perfil === 'SUPERVISOR'
  const role = becomingSupervisor ? 'user' : 'admin'
  const temporaryPassword = becomingSupervisor ? generateTemporaryPassword() : null

  await auth.api.setRole({ body: { userId: targetId, role }, headers: c.req.raw.headers })
  if (temporaryPassword) {
    await auth.api.setUserPassword({
      body: { userId: targetId, newPassword: temporaryPassword },
      headers: c.req.raw.headers,
    })
  }
  await query(
    `update "user"
        set turno=$2,"mustChangePassword"=$3,"updatedAt"=now()
      where id=$1`,
    [targetId, becomingSupervisor ? body.data.turno : null, becomingSupervisor],
  )
  await auth.api.revokeUserSessions({ body: { userId: targetId }, headers: c.req.raw.headers })
  await auditUser(adminAtual.id, 'ALTERAR_PERFIL', targetId, {
    perfil: body.data.perfil,
    turno: becomingSupervisor ? body.data.turno : null,
    senhaTemporariaGerada: becomingSupervisor,
    sessoesRevogadas: true,
  })

  c.header('Cache-Control', 'no-store')
  return c.json({
    ok: true,
    perfil: body.data.perfil,
    turno: becomingSupervisor ? body.data.turno : null,
    trocaSenhaObrigatoria: becomingSupervisor,
    senhaTemporaria: temporaryPassword,
  })
})
