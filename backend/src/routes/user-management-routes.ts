import { Hono } from 'hono'
import { z } from 'zod'
import { getAuth, requireRole, requireUser, type AppEnv } from '../auth-runtime.js'
import { query } from '../db.js'
import { newId } from '../security.js'
import { generateTemporaryPassword, isStrongPassword } from '../supervisor-onboarding.js'
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
    ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['turno'], message: 'Informe o turno do Supervisor.' })
  }
  if (value.perfil === 'ADMIN' && !value.senha) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['senha'], message: 'Informe a senha inicial do Administrador.' })
  }
})

function isSupervisorProfile(perfil: string): perfil is SupervisorProfileInput {
  return perfil === 'SUPERVISOR' || perfil.startsWith('SUPERVISOR_')
}

function supervisorShift(
  perfil: SupervisorProfileInput,
  explicit: string | null | undefined,
): 'A' | 'B' | 'C' | 'D' | null {
  if (explicit === 'A' || explicit === 'B' || explicit === 'C' || explicit === 'D') return explicit
  const suffix = perfil.split('_').at(-1)
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
  // O perfil sai do corpo para uma variável própria porque o estreitamento do
  // type guard não sobrevive ao acesso de propriedade em `body.data`.
  const perfil = body.data.perfil
  const supervisor = isSupervisorProfile(perfil)
  const role = supervisor ? 'user' : 'admin'
  const normalizedProfile = supervisor ? 'SUPERVISOR' : 'ADMIN'
  const turno = supervisor ? supervisorShift(perfil, body.data.turno) : null
  if (supervisor && !turno) return c.json({ erro: 'Informe o turno do Supervisor.' }, 400)

  const temporaryPassword = supervisor ? (body.data.senha ?? generateTemporaryPassword()) : null
  const plaintextPassword = temporaryPassword ?? body.data.senha
  if (!plaintextPassword) return c.json({ erro: 'Senha inicial ausente.' }, 400)
  if (supervisor && !isStrongPassword(plaintextPassword)) {
    return c.json({
      erro: 'A senha temporária gerada é inválida. Gere uma nova senha e tente novamente.',
      codigo: 'TEMPORARY_PASSWORD_POLICY',
    }, 400)
  }

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

/**
 * Vincula (ou desvincula) a conta ao colaborador que essa pessoa é.
 *
 * Só o Administrador o faz -- todo este módulo já exige ADMIN. Vincular tem
 * consequência real: a partir daí a pessoa conta como colaboradora, aparece
 * nos relatórios e as suas pausas medem-se pelos mesmos limites. Enviar
 * `colaboradorId: null` desfaz o vínculo sem apagar nada.
 */
userManagementRoutes.put('/usuarios/:id/colaborador', async (c) => {
  const usuarioId = c.req.param('id')
  const body = await parseJson(c, z.object({
    colaboradorId: z.string().uuid().nullable(),
  }))
  if (!body.ok) return body.response

  const alvo = (await query<{ id: string }>('select id from "user" where id=$1', [usuarioId])).rows[0]
  if (!alvo) return c.json({ erro: 'Conta não encontrada.' }, 404)

  if (body.data.colaboradorId) {
    const colaborador = (await query<{ id: string; nome: string }>(
      'select id,nome from colaboradores where id=$1 and ativo=true',
      [body.data.colaboradorId],
    )).rows[0]
    if (!colaborador) return c.json({ erro: 'Colaborador não encontrado ou inativo.' }, 404)

    // O índice único já recusa, mas uma mensagem clara poupa quem está a
    // configurar de decifrar um erro de constraint.
    const ocupado = (await query<{ id: string }>(
      'select id from "user" where colaborador_id=$1 and id<>$2',
      [body.data.colaboradorId, usuarioId],
    )).rows[0]
    if (ocupado) return c.json({ erro: 'Este colaborador já está vinculado a outra conta.' }, 409)
  }

  await query('update "user" set colaborador_id=$2, "updatedAt"=now() where id=$1', [
    usuarioId,
    body.data.colaboradorId,
  ])

  const actor = c.get('user')
  await query(
    `insert into auditoria (ator_auth_id,ator_tipo,acao,entidade,entidade_id,detalhes)
     values ($1,$2,$3,'CONTA',$4,$5::jsonb)`,
    [
      actor.id,
      actor.papel,
      body.data.colaboradorId ? 'VINCULAR_COLABORADOR' : 'DESVINCULAR_COLABORADOR',
      usuarioId,
      JSON.stringify({ colaboradorId: body.data.colaboradorId }),
    ],
  )

  return c.json({ ok: true, colaboradorId: body.data.colaboradorId })
})
