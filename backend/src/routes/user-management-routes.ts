import { Hono } from 'hono'
import { z } from 'zod'
import { getAuth, requireRole, requireUser, type AppEnv } from '../auth-runtime.js'
import { query } from '../db.js'
import { newId } from '../security.js'
import { parseJson } from './shared.js'

export const userManagementRoutes = new Hono<AppEnv>()
userManagementRoutes.use('*', requireUser, requireRole('ADMIN'))

type CreateUserStage = 'hash_senha' | 'persistencia'

function databaseErrorCode(error: unknown): string | null {
  if (!error || typeof error !== 'object') return null
  const code = (error as { code?: unknown }).code
  return typeof code === 'string' ? code : null
}

function safeCreateUserCode(error: unknown, stage: CreateUserStage): string {
  const code = databaseErrorCode(error)
  if (code === '23505') return 'USER_ALREADY_EXISTS'
  if (code === '42501') return 'USER_DATABASE_PERMISSION'
  if (code === '42P01' || code === '42703') return 'USER_DATABASE_SCHEMA'

  const message = error instanceof Error ? error.message.toLowerCase() : ''
  if (message.includes('scrypt') || message.includes('password') || message.includes('crypto')) {
    return 'USER_PASSWORD_HASH'
  }
  if (message.includes('connection') || message.includes('socket') || message.includes('timeout')) {
    return 'USER_DATABASE_CONNECTION'
  }
  return stage === 'hash_senha' ? 'USER_PASSWORD_HASH' : 'USER_CREATE_FAILED'
}

userManagementRoutes.post('/usuarios', async (c) => {
  const body = await parseJson(c, z.object({
    nome: z.string().trim().min(2).max(120),
    email: z.string().trim().toLowerCase().email().max(254),
    senha: z.string().min(10).max(128),
    perfil: z.enum(['SUPERVISOR', 'ADMIN']).default('SUPERVISOR'),
  }))
  if (!body.ok) return body.response

  const actor = c.get('user')
  const role = body.data.perfil === 'ADMIN' ? 'admin' : 'user'
  const userId = newId()
  const accountId = newId()
  let stage: CreateUserStage = 'hash_senha'

  try {
    // Usa exatamente o mesmo hasher configurado pelo Better Auth. A senha
    // nunca é persistida, retornada ou registrada em texto puro.
    const authContext = await getAuth().$context
    const passwordHash = await authContext.password.hash(body.data.senha)

    stage = 'persistencia'
    const result = await query<{ id: string; name: string; email: string }>(
      `with created_user as (
         insert into "user"
           (id,name,email,"emailVerified","createdAt","updatedAt",role,banned)
         values ($1,$2,$3,true,now(),now(),$4,false)
         returning id,name,email
       ), created_account as (
         insert into account
           (id,"accountId","providerId","userId",password,"createdAt","updatedAt")
         select $5,id,'credential',id,$6,now(),now()
           from created_user
         returning id
       ), created_audit as (
         insert into auditoria
           (ator_auth_id,ator_tipo,acao,entidade,entidade_id,detalhes)
         select $7,'ADMIN','CRIAR_CONTA','USUARIO',id,$8::jsonb
           from created_user
         returning id
       )
       select id,name,email from created_user`,
      [
        userId,
        body.data.nome,
        body.data.email,
        role,
        accountId,
        passwordHash,
        actor.id,
        JSON.stringify({
          email: body.data.email,
          nome: body.data.nome,
          perfil: body.data.perfil,
        }),
      ],
    )

    const created = result.rows[0]
    if (!created) throw new Error('Conta não retornada após persistência.')

    return c.json({
      usuario: {
        id: created.id,
        nome: created.name,
        email: created.email,
        perfil: body.data.perfil,
      },
    }, 201)
  } catch (error) {
    const code = databaseErrorCode(error)
    const message = error instanceof Error ? error.message.toLowerCase() : ''
    if (code === '23505' || message.includes('unique') || message.includes('duplicate')) {
      return c.json({ erro: 'Já existe uma conta cadastrada com este e-mail.' }, 409)
    }

    const safeCode = safeCreateUserCode(error, stage)
    console.error(JSON.stringify({
      evento: 'admin_user_create_failure',
      etapa: stage,
      codigo: safeCode,
      codigoBanco: code && /^[A-Z0-9]{4,6}$/i.test(code) ? code : null,
      tipo: error instanceof Error ? error.name : typeof error,
    }))

    return c.json({
      erro: 'Não foi possível criar a conta. Tente novamente.',
      codigo: safeCode,
      etapa: stage,
    }, 500)
  }
})
