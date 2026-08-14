import { createHash, timingSafeEqual } from 'node:crypto'
import { Hono } from 'hono'
import { z } from 'zod'
import { auth, type AppEnv } from '../auth-runtime.js'
import { config } from '../config.js'
import { query, transaction } from '../db.js'
import { parseJson } from './shared.js'

export const setupRoutes = new Hono<AppEnv>()

type SetupStage = 'bloqueio' | 'verificacao' | 'criacao_usuario' | 'auditoria'

function sameSecret(received: string, expected: string): boolean {
  const left = createHash('sha256').update(received).digest()
  const right = createHash('sha256').update(expected).digest()
  return timingSafeEqual(left, right)
}

function setupKeyFingerprint(value: string): string {
  return createHash('sha256').update(value).digest('hex').slice(0, 16)
}

function safeSetupErrorCode(error: unknown): string {
  if (!(error instanceof Error)) return 'SETUP_UNKNOWN_ERROR'
  const message = error.message.toLowerCase()
  if (message.includes('timeout') || message.includes('connection')) return 'SETUP_DATABASE_CONNECTION'
  if (message.includes('duplicate') || message.includes('unique')) return 'SETUP_DUPLICATE'
  return 'SETUP_OPERATION_FAILED'
}

setupRoutes.get('/status', async (c) => {
  const result = await query<{ total: string }>('select count(*)::text as total from "user"')
  const primeiroAdminNecessario = Number(result.rows[0]?.total ?? 0) === 0
  return c.json({
    primeiroAdminNecessario,
    instalacaoConfigurada: Boolean(config.firstAdminSetupKey),
    chaveInstalacaoFingerprint:
      primeiroAdminNecessario && config.firstAdminSetupKey
        ? setupKeyFingerprint(config.firstAdminSetupKey)
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

  if (!config.firstAdminSetupKey) {
    return c.json({ erro: 'Instalação inicial não habilitada no servidor.' }, 503)
  }
  if (!sameSecret(body.data.chaveInstalacao, config.firstAdminSetupKey)) {
    return c.json({ erro: 'Chave de instalação inválida.' }, 403)
  }

  let stage: SetupStage = 'bloqueio'
  try {
    const created = await transaction(async (client) => {
      stage = 'bloqueio'
      await client.query('select pg_advisory_xact_lock(7266680041)')

      stage = 'verificacao'
      const count = await client.query<{ total: string }>('select count(*)::text as total from "user"')
      if (Number(count.rows[0]?.total ?? 0) > 0) {
        throw new Error('SETUP_ALREADY_COMPLETED')
      }

      stage = 'criacao_usuario'
      const newUser = await auth.api.createUser({
        body: {
          name: body.data.nome,
          email: body.data.email,
          password: body.data.senha,
          role: 'admin',
        },
      })

      stage = 'auditoria'
      try {
        await client.query(
          `insert into auditoria (ator_tipo,acao,entidade,entidade_id,detalhes)
           values ('SETUP','CRIAR_PRIMEIRO_ADMIN','USUARIO',$1,$2::jsonb)`,
          [newUser.user.id, JSON.stringify({ email: newUser.user.email, nome: newUser.user.name, perfil: 'ADMIN' })],
        )
      } catch (auditError) {
        console.error('Falha ao auditar criação do primeiro administrador.', auditError)
      }

      return newUser
    })

    return c.json({
      concluido: true,
      usuario: {
        id: created.user.id,
        nome: created.user.name,
        email: created.user.email,
        perfil: 'ADMIN',
      },
    }, 201)
  } catch (error) {
    if (error instanceof Error && error.message === 'SETUP_ALREADY_COMPLETED') {
      return c.json({ erro: 'A instalação inicial já foi concluída.' }, 409)
    }

    console.error(JSON.stringify({
      evento: 'first_admin_setup_failure',
      etapa: stage,
      codigo: safeSetupErrorCode(error),
    }))

    return c.json({
      erro: 'Erro interno do servidor.',
      etapa: stage,
      codigo: safeSetupErrorCode(error),
    }, 500)
  }
})
