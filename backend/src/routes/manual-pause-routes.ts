import { Hono } from 'hono'
import { z } from 'zod'
import { requireRole, requireUser, type AppEnv } from '../auth-runtime.js'
import { config } from '../config.js'
import { transaction } from '../db.js'
import { parseJson } from './shared.js'

/**
 * Fecho manual de pausa por Admin/Supervisor.
 *
 * O cliente Android ja chamava admin/pausas/manual/finalizar e
 * supervisor/pausas/manual/finalizar, mas as rotas nunca existiram — o Worker
 * respondia 404 "Rota nao encontrada" e o supervisor via isso na tela.
 *
 * A diferenca em relacao a /pausas/finalizar e o que autentica a operacao: ali e
 * um verificacaoToken biometrico ligado ao dispositivo; aqui e a sessao de quem
 * registra. Por isso a rota exige motivo, grava quem fez, e escreve em auditoria.
 *
 * Deliberadamente NAO inclui /pausas/manual/iniciar. Abrir uma pausa retroativa
 * cria um registro de jornada completo sem nenhuma evidencia biometrica, o que
 * merece controlo proprio (perfil, janela temporal, aprovacao) em vez de vir de
 * carona nesta rota.
 */

const uuidSchema = z.string().uuid()

const finalizarManualSchema = z.object({
  colaboradorId: uuidSchema,
  motivo: z.string().trim().min(3).max(200),
})

type ManualFinishResponse = {
  id: string
  inicioLocal: string
  fimEm: string
  fimLocal: string
  duracaoSegundos: number
  limiteSegundos: number
  excedeuLimite: boolean
  registradoManualmente: true
  registradoPor: { atorTipo: 'ADMIN' | 'SUPERVISOR'; atorNome: string }
}

async function finalizarPausaManual(
  colaboradorId: string,
  motivo: string,
  ator: { id: string; nome: string; papel: 'ADMIN' | 'SUPERVISOR' },
): Promise<ManualFinishResponse | { erro: string; status: number }> {
  return transaction(async (client) => {
    const open = await client.query<{ id: string; inicio_em: string; limite_segundos: number }>(
      `select id,inicio_em::text,limite_segundos from pausas_cafe
        where colaborador_id=$1 and fim_em is null
        order by inicio_em desc limit 1 for update`,
      [colaboradorId],
    )
    if (!open.rows[0]) {
      return { erro: 'Nenhuma pausa aberta para este colaborador.', status: 404 }
    }

    const finished = await client.query<{ fim_em: string; duracao_segundos: number }>(
      `update pausas_cafe
          set fim_em=now(),
              fim_registrado_manualmente=true,
              fim_motivo_manual=$2,
              fim_ator_auth_id=$3,
              fim_ator_tipo=$4,
              fim_registrado_em=now()
        where id=$1
        returning fim_em::text,
                  floor(extract(epoch from (fim_em-inicio_em)))::int as duracao_segundos`,
      [open.rows[0].id, motivo, ator.id, ator.papel],
    )
    const row = finished.rows[0]!

    const horario = await client.query<{ inicio_local: string; fim_local: string }>(
      `select to_char($1::timestamptz at time zone $3,'HH24:MI') as inicio_local,
              to_char($2::timestamptz at time zone $3,'HH24:MI') as fim_local`,
      [open.rows[0].inicio_em, row.fim_em, config.appTimezone],
    )

    await client.query(
      `insert into auditoria (ator_auth_id,ator_tipo,acao,entidade,entidade_id,detalhes)
       values ($1,$2,'PAUSA_FINALIZADA_MANUALMENTE','PAUSA',$3,$4::jsonb)`,
      [
        ator.id,
        ator.papel,
        open.rows[0].id,
        JSON.stringify({
          colaboradorId,
          motivo,
          duracaoSegundos: row.duracao_segundos,
          limiteSegundos: open.rows[0].limite_segundos,
          excedeuLimite: row.duracao_segundos > open.rows[0].limite_segundos,
        }),
      ],
    )

    return {
      id: open.rows[0].id,
      inicioLocal: horario.rows[0]!.inicio_local,
      fimEm: row.fim_em,
      fimLocal: horario.rows[0]!.fim_local,
      duracaoSegundos: row.duracao_segundos,
      limiteSegundos: open.rows[0].limite_segundos,
      excedeuLimite: row.duracao_segundos > open.rows[0].limite_segundos,
      registradoManualmente: true as const,
      registradoPor: { atorTipo: ator.papel, atorNome: ator.nome },
    }
  })
}

function buildManualPauseRoutes(...roles: Array<'ADMIN' | 'SUPERVISOR'>) {
  const routes = new Hono<AppEnv>()
  routes.use('*', requireUser, requireRole(...roles))

  routes.post('/pausas/manual/finalizar', async (c) => {
    const body = await parseJson(c, finalizarManualSchema)
    if (!body.ok) return body.response

    const user = c.get('user')
    const result = await finalizarPausaManual(body.data.colaboradorId, body.data.motivo, {
      id: user.id,
      nome: user.nome,
      papel: user.papel,
    })
    if ('erro' in result) return c.json({ erro: result.erro }, result.status as 404)
    return c.json(result)
  })

  return routes
}

export const adminManualPauseRoutes = buildManualPauseRoutes('ADMIN')
export const supervisorManualPauseRoutes = buildManualPauseRoutes('ADMIN', 'SUPERVISOR')
