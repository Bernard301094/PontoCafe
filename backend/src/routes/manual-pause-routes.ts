import { Hono } from 'hono'
import { z } from 'zod'
import { requireRole, requireUser, type AppEnv } from '../auth-runtime.js'
import { config } from '../config.js'
import { transaction } from '../db.js'
import { newId } from '../security.js'
import { parseJson } from './shared.js'

/**
 * Registo manual de pausa (abertura e fecho) por Admin/Supervisor.
 *
 * O cliente Android ja chamava admin/pausas/manual/finalizar e
 * supervisor/pausas/manual/finalizar, mas as rotas nunca existiram — o Worker
 * respondia 404 "Rota nao encontrada" e o supervisor via isso na tela.
 *
 * A diferenca em relacao a /pausas/finalizar e o que autentica a operacao: ali e
 * um verificacaoToken biometrico ligado ao dispositivo; aqui e a sessao de quem
 * registra. Por isso a rota exige motivo, grava quem fez, e escreve em auditoria.
 *
 * A abertura manual (/pausas/manual/iniciar) esteve deliberadamente de fora daqui:
 * abrir uma pausa sem evidencia biometrica cria um registo de jornada inteiro
 * apoiado so na sessao de quem regista. Essa decisao foi revertida de propósito --
 * o cliente Android ja oferecia o botao "manual" nas telas de autorizacao e o
 * operador levava "Rota nao encontrada" ao usa-lo.
 *
 * A garantia nao foi descartada, foi trocada. Ver 011_manual_pause_open.sql: o
 * esquema deixou de exigir prova biometrica em toda pausa e passou a exigir OU
 * prova biometrica OU um responsavel identificado com motivo -- nunca nenhuma das
 * duas. E mais fraco de propósito, e a diferenca esta escrita la.
 *
 * Continua em falta o que a nota original pedia e esta rota nao resolve: perfil
 * proprio (hoje qualquer ADMIN/SUPERVISOR abre), janela temporal (nao ha limite de
 * quao para tras) e aprovacao de um segundo ator.
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

const iniciarManualSchema = z.object({
  colaboradorId: uuidSchema,
  motivo: z.string().trim().min(3).max(200),
})

type ManualStartResponse = {
  id: string
  periodo: 'MANHA' | 'TARDE'
  limiteSegundos: number
  inicioEm: string
  inicioLocal: string
  retornoAteLocal: string
  registradoManualmente: true
  registradoPor: { atorTipo: 'ADMIN' | 'SUPERVISOR'; atorNome: string }
}

/**
 * Abre a pausa do colaborador sem passar pelo quiosque.
 *
 * As tres recusas abaixo sao as mesmas de /ponto/pausas (pausa ja aberta, pausa do
 * periodo ja usada hoje, colaborador inativo). Nao sao repetidas por simetria
 * estetica: sem elas esta rota vira o caminho facil para furar as regras que o
 * fluxo biometrico aplica, e o ux_pausa_periodo_dia estouraria como erro 500 em
 * vez de uma mensagem que o operador entende.
 *
 * Fora do horario NAO exige autorizacao previa aqui, ao contrario do fluxo do
 * quiosque: quem abre ja e o Admin/Supervisor que emitiria essa autorizacao. O
 * registo fica com fora_horario=true para o relatorio distinguir.
 */
async function iniciarPausaManual(
  colaboradorId: string,
  motivo: string,
  ator: { id: string; nome: string; papel: 'ADMIN' | 'SUPERVISOR' },
): Promise<ManualStartResponse | { erro: string; status: number }> {
  return transaction(async (client) => {
    const colaborador = await client.query<{ id: string; ativo: boolean }>(
      `select id,ativo from colaboradores where id=$1`,
      [colaboradorId],
    )
    if (!colaborador.rows[0]) return { erro: 'Colaborador nao encontrado.', status: 404 }
    if (!colaborador.rows[0].ativo) return { erro: 'Colaborador inativo.', status: 409 }

    const aberta = await client.query<{ id: string }>(
      `select id from pausas_cafe where colaborador_id=$1 and fim_em is null limit 1 for update`,
      [colaboradorId],
    )
    if (aberta.rows[0]) {
      return { erro: 'Este colaborador ja tem uma pausa aberta.', status: 409 }
    }

    const ativa = await client.query<{ periodo: 'MANHA' | 'TARDE'; limite_segundos: number }>(
      `select periodo,limite_segundos from regras_cafe where ativo=true
        and (now() at time zone $1)::time>=inicio and (now() at time zone $1)::time<fim
        order by inicio limit 1`,
      [config.appTimezone],
    )

    let periodo: 'MANHA' | 'TARDE'
    let limiteSegundos: number
    let foraHorario = false

    if (ativa.rows[0]) {
      periodo = ativa.rows[0].periodo
      limiteSegundos = ativa.rows[0].limite_segundos
    } else {
      // Sem janela ativa nao ha periodo declarado pela regra, entao ele vem do
      // relogio local. E uma escolha, nao um dado: uma abertura manual as 23h cai
      // em TARDE por convencao, e o fora_horario=true e o que conta a verdade.
      foraHorario = true
      const fallback = await client.query<{ periodo: 'MANHA' | 'TARDE'; limite_segundos: number }>(
        `select periodo,limite_segundos from regras_cafe
          where ativo=true
            and periodo = case when extract(hour from now() at time zone $1) < 12
                               then 'MANHA' else 'TARDE' end
          limit 1`,
        [config.appTimezone],
      )
      if (!fallback.rows[0]) {
        return { erro: 'Nenhuma regra de cafe ativa configurada para este periodo.', status: 409 }
      }
      periodo = fallback.rows[0].periodo
      limiteSegundos = fallback.rows[0].limite_segundos
    }

    const usada = await client.query<{ id: string }>(
      `select id from pausas_cafe
        where colaborador_id=$1 and periodo=$2
          and (inicio_em at time zone $3)::date=(now() at time zone $3)::date
        limit 1 for update`,
      [colaboradorId, periodo, config.appTimezone],
    )
    if (usada.rows[0]) {
      return { erro: 'Este colaborador ja registrou esta pausa hoje.', status: 409 }
    }

    const id = newId()
    const inserida = await client.query<{ inicio_em: string }>(
      `insert into pausas_cafe
         (id,colaborador_id,periodo,limite_segundos,fora_horario,
          inicio_registrado_manualmente,inicio_motivo_manual,
          inicio_ator_auth_id,inicio_ator_tipo,inicio_registrado_em)
       values ($1,$2,$3,$4,$5,true,$6,$7,$8,now())
       returning inicio_em::text`,
      [id, colaboradorId, periodo, limiteSegundos, foraHorario, motivo, ator.id, ator.papel],
    )

    const horario = await client.query<{ inicio_local: string; retorno_ate_local: string }>(
      `select to_char($1::timestamptz at time zone $3,'HH24:MI') as inicio_local,
              to_char(($1::timestamptz + ($2::int * interval '1 second')) at time zone $3,'HH24:MI')
                as retorno_ate_local`,
      [inserida.rows[0]!.inicio_em, limiteSegundos, config.appTimezone],
    )

    await client.query(
      `insert into auditoria (ator_auth_id,ator_tipo,acao,entidade,entidade_id,detalhes)
       values ($1,$2,'PAUSA_INICIADA_MANUALMENTE','PAUSA',$3,$4::jsonb)`,
      [
        ator.id,
        ator.papel,
        id,
        JSON.stringify({ colaboradorId, motivo, periodo, limiteSegundos, foraHorario }),
      ],
    )

    return {
      id,
      periodo,
      limiteSegundos,
      inicioEm: inserida.rows[0]!.inicio_em,
      inicioLocal: horario.rows[0]!.inicio_local,
      retornoAteLocal: horario.rows[0]!.retorno_ate_local,
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

  routes.post('/pausas/manual/iniciar', async (c) => {
    const body = await parseJson(c, iniciarManualSchema)
    if (!body.ok) return body.response

    const user = c.get('user')
    const result = await iniciarPausaManual(body.data.colaboradorId, body.data.motivo, {
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
