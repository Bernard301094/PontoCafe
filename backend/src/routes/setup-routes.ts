import { createHash, timingSafeEqual } from 'node:crypto'
import { Hono } from 'hono'
import { z } from 'zod'
import { getAuth, type AppEnv } from '../auth-runtime.js'
import { query, transaction } from '../db.js'
import { parseJson } from './shared.js'

export const setupRoutes = new Hono<AppEnv>()

type SetupStage = 'bloqueio' | 'verificacao' | 'criacao_usuario' | 'credencial' | 'auditoria'

function sameSecret(received: string, expected: string): boolean {
  const left = createHash('sha256').update(received).digest()
  const right = createHash('sha256').update(expected).digest()
  return timingSafeEqual(left, right)
}

function setupKeyFingerprint(value: string): string {
  return createHash('sha256').update(value).digest('hex').slice(0, 16)
}

function currentSetupKey(bindings: AppEnv['Bindings'] | undefined): string | null {
  const runtimeValue = bindings?.FIRST_ADMIN_SETUP_KEY
  if (typeof runtimeValue === 'string' && runtimeValue.trim()) return runtimeValue.trim()

  const localValue = process.env.FIRST_ADMIN_SETUP_KEY
  return localValue?.trim() || null
}

function safeSetupErrorCode(error: unknown): string {
  if (!(error instanceof Error)) return 'SETUP_UNKNOWN_ERROR'
  const message = error.message.toLowerCase()
  if (message.includes('timeout') || message.includes('connection')) return 'SETUP_DATABASE_CONNECTION'
  if (message.includes('duplicate') || message.includes('unique') || message.includes('already')) return 'SETUP_DUPLICATE'
  if (message.includes('password')) return 'SETUP_PASSWORD'
  return 'SETUP_OPERATION_FAILED'
}

setupRoutes.get('/status', async (c) => {
  const result = await query<{ total: string }>('select count(*)::text as total from "user"')
  const primeiroAdminNecessario = Number(result.rows[0]?.total ?? 0) === 0
  const setupKey = currentSetupKey(c.env)

  return c.json({
    primeiroAdminNecessario,
    instalacaoConfigurada: Boolean(setupKey),
    chaveInstalacaoFingerprint:
      primeiroAdminNecessario && setupKey
        ? setupKeyFingerprint(setupKey)
        : null,
  })
})

setupRoutes.post('/primeiro-admin', async (c) => {
  const body = await parseJson(c, z.object({
    nome: z.string().trim().min(2).max(120),
    email: z.string().trim().toLowerCase().email().max(254),
    senha: z.string().min(10).max(128),
    chaveInstalacao: z.string().min(16).max(256),
  }))
  if (!body.ok) return body.response

  const setupKey = currentSetupKey(c.env)
  if (!setupKey) {
    return c.json({ erro: 'Instalação inicial não habilitada no servidor.' }, 503)
  }
  if (!sameSecret(body.data.chaveInstalacao, setupKey)) {
    return c.json({ erro: 'Chave de instalação inválida.' }, 403)
  }

  let stage: SetupStage = 'bloqueio'
  let createdUserId: string | null = null

  try {
    const created = await transaction(async (client) => {
      stage = 'bloqueio'
      await client.query('select pg_advisory_xact_lock(7266680041)')

      stage = 'verificacao'
      const count = await client.query<{ total: string }>('select count(*)::text as total from "user"')
      if (Number(count.rows[0]?.total ?? 0) > 0) {
        throw new Error('SETUP_ALREADY_COMPLETED')
      }

      const authContext = await getAuth().$context
      const internalAdapter = authContext.internalAdapter

      stage = 'criacao_usuario'
      const now = new Date()
      const user = await internalAdapter.createUser({
        name: body.data.nome,
        email: body.data.email,
        emailVerified: true,
        role: 'admin',
        createdAt: now,
        updatedAt: now,
      })
      createdUserId = user.id

      stage = 'credencial'
      const passwordHash = await authContext.password.hash(body.data.senha)
      await internalAdapter.linkAccount({
        accountId: user.id,
        providerId: 'credential',
        userId: user.id,
        password: passwordHash,
      })

      stage = 'auditoria'
      try {
        await client.query(
          `insert into auditoria (ator_tipo,acao,entidade,entidade_id,detalhes)
           values ('SETUP','CRIAR_PRIMEIRO_ADMIN','USUARIO',$1,$2::jsonb)`,
          [user.id, JSON.stringify({ email: user.email, nome: user.name, perfil: 'ADMIN' })],
        )
      } catch (auditError) {
        console.error('Falha ao auditar criação do primeiro administrador.', auditError)
      }

      return user
    })

    return c.json({
      concluido: true,
      usuario: {
        id: created.id,
        nome: created.name,
        email: created.email,
        perfil: 'ADMIN',
      },
    }, 201)
  } catch (error) {
    if (error instanceof Error && error.message === 'SETUP_ALREADY_COMPLETED') {
      return c.json({ erro: 'A instalação inicial já foi concluída.' }, 409)
    }

    if (createdUserId) {
      try {
        await query('delete from "user" where id=$1', [createdUserId])
      } catch (cleanupError) {
        console.error('Falha ao limpar usuário parcial do setup.', cleanupError)
      }
    }

    console.error(JSON.stringify({
      evento: 'first_admin_setup_failure',
      etapa: stage,
      codigo: safeSetupErrorCode(error),
      tipo: error instanceof Error ? error.name : typeof error,
    }))

    return c.json({
      erro: 'Erro interno do servidor.',
      etapa: stage,
      codigo: safeSetupErrorCode(error),
    }, 500)
  }
})
