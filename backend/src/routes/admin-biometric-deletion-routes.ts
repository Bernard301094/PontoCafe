import { Hono } from 'hono'
import { requireRole, requireUser, type AppEnv } from '../auth-runtime.js'
import { transaction } from '../db.js'
import { uuidSchema } from './shared.js'

/**
 * Exclusão biométrica destrutiva destinada exclusivamente ao Administrador.
 *
 * Mantemos o colaborador, pausas e verificações históricas para preservar
 * integridade referencial/auditoria. Todos os templates faciais são removidos e
 * qualquer verificação facial ainda válida e não utilizada é expirada no mesmo
 * transaction boundary, impedindo que um token biométrico recém-emitido sobreviva
 * à exclusão do rosto.
 */
export const adminBiometricDeletionRoutes = new Hono<AppEnv>()
adminBiometricDeletionRoutes.use('*', requireUser, requireRole('ADMIN'))

adminBiometricDeletionRoutes.post('/colaboradores/:id/biometria/excluir', async (c) => {
  const colaboradorId = c.req.param('id')
  if (!uuidSchema.safeParse(colaboradorId).success) {
    return c.json({ erro: 'Colaborador inválido.' }, 400)
  }

  const actor = c.get('user')
  const result = await transaction(async (client) => {
    const collaborator = await client.query<{ id: string; nome: string; ativo: boolean }>(
      'select id,nome,ativo from colaboradores where id=$1 for update',
      [colaboradorId],
    )
    const row = collaborator.rows[0]
    if (!row || !row.ativo) return { status: 'NOT_FOUND' as const }

    const openPause = await client.query<{ id: string }>(
      'select id from pausas_cafe where colaborador_id=$1 and fim_em is null limit 1',
      [colaboradorId],
    )
    if (openPause.rows[0]) return { status: 'OPEN_PAUSE' as const }

    const deleted = await client.query<{ id: string }>(
      'delete from templates_faciais where colaborador_id=$1 returning id',
      [colaboradorId],
    )
    if (deleted.rowCount === 0) return { status: 'NO_BIOMETRIC' as const }

    // Não apagamos verificações históricas porque pausas_cafe referencia seus IDs.
    // Apenas revogamos imediatamente qualquer verificação ainda utilizável.
    const revoked = await client.query<{ id: string }>(
      `update verificacoes_faciais
          set expira_em=least(expira_em, now())
        where colaborador_id=$1
          and usado_em is null
          and expira_em>now()
        returning id`,
      [colaboradorId],
    )

    await client.query(
      `insert into auditoria (ator_auth_id,ator_tipo,acao,entidade,entidade_id,detalhes)
       values ($1,$2,'EXCLUIR_ROSTO','COLABORADOR',$3,$4::jsonb)`,
      [
        actor.id,
        actor.papel,
        colaboradorId,
        JSON.stringify({
          nome: row.nome,
          templatesExcluidos: deleted.rowCount,
          verificacoesRevogadas: revoked.rowCount,
          exclusaoBiometricaCompleta: true,
          colaboradorPreservado: true,
          historicoPreservado: true,
        }),
      ],
    )

    return {
      status: 'OK' as const,
      nome: row.nome,
      templatesExcluidos: deleted.rowCount,
      verificacoesRevogadas: revoked.rowCount,
    }
  })

  if (result.status === 'NOT_FOUND') {
    return c.json({ erro: 'Colaborador não encontrado ou inativo.' }, 404)
  }
  if (result.status === 'OPEN_PAUSE') {
    return c.json({ erro: 'Finalize a pausa aberta antes de excluir o rosto deste colaborador.' }, 409)
  }
  if (result.status === 'NO_BIOMETRIC') {
    return c.json({ erro: 'Este colaborador não possui rosto cadastrado.' }, 404)
  }

  return c.json({
    ok: true,
    colaboradorId,
    rostoExcluido: true,
    templatesExcluidos: result.templatesExcluidos,
    verificacoesRevogadas: result.verificacoesRevogadas,
    colaboradorPreservado: true,
    historicoPreservado: true,
  })
})
