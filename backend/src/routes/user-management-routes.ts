import { Hono } from 'hono'
import { z } from 'zod'
import { getAuth, requireRole, requireUser, type AppEnv } from '../auth-runtime.js'
import { query } from '../db.js'
import { newId } from '../security.js'
import { generateTemporaryPassword } from '../supervisor-onboarding.js'
import { parseJson } from './shared.js'

export const userManagementRoutes = new Hono<AppEnv>()
userManagementRoutes.use('*', requireUser, requireRole('ADMIN'))

type CreateUserStage = 'hash_senha' | 'persistencia'

type SupervisorProfileInput = 'SUPERVISOR' | 'SUPERVISOR_A' | 'SUPERVISOR_B' | 'SUPERVISOR_C' | 'SUPERVISOR_D'

const createUserSchema = z.object({
  nome: z.string().trim().min(2).max(120),
  email: z.string().trim().toLowerCase().email().max(254),
  senha: z.string().min(10).max(128).optional(),
  perfil: z.enum(['SUPERVISOR', 'SUPERVISOR_A', 'SUPERVISOR_B', 'SUPERVISOR_C', 'SUPERVISOR_D', 'ADMIN'])
    .default('SUPERVISOR'),
  turno: z.enum(['A', 'B', 'C', 'D']).optional().nullable(),
}).superRefine((value, ctx) => {
  if (value.perfil === 'SUPERVISOR' && !value.turno) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      path: ['turno'],
      message: 'Informe o turno do Supervisor.',
    })
  }
  if (value.perfil === 'ADMIN' && !value.senha) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      path: ['senha'],
      message: 'Informe a senha inicial do Administrador.',
    })
  }
})

function isSupervisorProfile(perfil: string): perfil is SupervisorProfileInput {
  return perfil === 'SUPERVISOR' || perfil.startsWith('SUPERVISOR_')
}

function supervisorShift(perfil: SupervisorProfileInput, explicit: string | null | undefined): 'A' | 'B' | 'C' | 'D' | null {
  if (explicit === 'A' || explicit === 'B' || explicit === 'C' || explicit === 'D') return explicit
  const suffix = perfil.substringAfterLast?.('_')
  if (suffix === 'A' || suffix === 'B' || suffix === 'C' || suffix === 'D') return suffix
  return null
}

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
  const body = await parseJson(c, createUserSchema)
  if (!body.ok) return body.response

  const actor = c.get('user')
  const supervisor = isSupervisorProfile(body.data.perfil)
  const role = supervisor ? 'user' : 'admin'
  const normalizedProfile = supervisor ? 'SUPERVISOR' : 'ADMIN'
  const turno = supervisor ? supervisorShift(body.data.perfil, body.data.turno) : null
  if (supervisor && !turno) return c.json({ erro: 'Informe o turno do Supervisor.' }, 400)

  // O Android pode gerar a senha temporária com SecureRandom para exibi-la ao
  // Admin antes de sair da tela. Se o cliente ainda for antigo, o Worker gera
  // uma senha segura como fallback. Em ambos os casos só o hash é persistido.
  const temporaryPassword = supervisor ? (body.data.senha ?? generateTemporaryPassword()) : null
  const plaintextPassword = temporaryPassword ?? body.data.senha
  if (!plaintextPassword) return c.json({ erro: 'Senha inicial ausente.' }, 400)

  const userId = newId()
  const accountId = newId()
  let stage: CreateUserStage = 'hash_senha'

  try {
    const authContext = await getAuth().$context
    const passwordHash = await authContext.password.hash(plaintextPassword)

    stage = 'persistencia'
    const result = await query<{ id: string; name: string; email: string; turno: string | null }>(
      `with created_user as (
         insert into "user"
           (id,name,email,"emailVerified","createdAt","updatedAt",role,banned,turno,"mustChangePassword")
         values ($1,$2,$3,true,now(),now(),$4,false,$5,$6)
         returning id,name,email,turno
       ), created_account as (
         insert into account
           (id,"accountId","providerId","userId",password,"createdAt","updatedAt")
         select $7,id,'credential',id,$8,now(),now()
           from created_user
         returning id
       ), created_audit as (
         insert into auditoria
           (ator_auth_id,ator_tipo,acao,entidade,entidade_id,detalhes)
         select $9,'ADMIN','CRIAR_CONTA','USUARIO',id,$10::jsonb
           from created_user
         returning id
       )
       select id,name,email,turno from created_user`,
      [
        userId,
        body.data.nome,
        body.data.email,
        role,
        turno,
        supervisor,
        accountId,
        passwordHash,
        actor.id,
        JSON.stringify({
          email: body.data.email,
          nome: body.data.nome,
          perfil: normalizedProfile,
          turno,
          senhaTemporariaGerada: supervisor,
          trocaSenhaObrigatoria: supervisor,
        }),
      ],
    )

    const created = result.rows[0]
    if (!created) throw new Error('Conta não retornada após persistência.')

    c.header('Cache-Control', 'no-store')
    return c.json({
      usuario: {
        id: created.id,
        nome: created.name,
        email: created.email,
        perfil: normalizedProfile,
        turno: created.turno,
        trocaSenhaObrigatoria: supervisor,
      },
      // Resposta efêmera para clientes novos. Clientes que já exibiram a senha
      // gerada localmente podem simplesmente ignorar este campo.
      senhaTemporaria: temporaryPassword,
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
