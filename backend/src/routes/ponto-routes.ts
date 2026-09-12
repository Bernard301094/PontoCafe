import { Hono } from 'hono'
import { z } from 'zod'
import type { AppEnv } from '../auth-runtime.js'
import { config } from '../config.js'
import { query } from '../db.js'
import { ACCESS_CODE_LENGTH } from '../domain/access-code.js'
import { CURRENT_PERIOD_CTE, periodPauseDoneSql } from '../ponto-period.js'
import { deviceTokenMiddleware } from './shared.js'

export const pontoRoutes = new Hono<AppEnv>()
pontoRoutes.use('*', deviceTokenMiddleware)

/**
 * Lista de nomes do quiosque.
 *
 * É a primeira coisa que a pessoa toca no novo fluxo — escolhe-se aqui, e só
 * depois se digita o código. Por isso devolve apenas o mínimo para desenhar a
 * lista: nada de código, nada de estado de pausa de terceiros.
 *
 * Quem já fechou a pausa deste período hoje sai da lista aqui, no servidor, e
 * não por um sinalizador que o quiosque filtraria. É menos informação a sair
 * daqui, não mais: o aparelho recebe uma lista mais curta em vez de aprender
 * quem tomou café. Quem está no café **agora** continua na lista — é essa
 * pessoa que ainda precisa do quiosque para registar o retorno.
 *
 * O limite era 100, e a equipa tem 104: quatro pessoas nunca apareciam ao abrir
 * a lista -- só se escrevessem o nome. Um limite abaixo do tamanho da equipa
 * não é um limite, é gente invisível. Fica em 500, que é folga para crescer;
 * acima disso a lista deixa de se percorrer com o dedo e o caminho é a busca,
 * por nome ou por matrícula.
 *
 * A ordem continua alfabética de propósito. Pôr primeiro quem está em pausa
 * seria mais rápido para o retorno, mas mostraria a quem estiver diante do
 * quiosque quem foi ao café -- exactamente o que o parágrafo acima evita. O
 * atalho para não percorrer a lista é a matrícula.
 */
pontoRoutes.get('/colaboradores', async (c) => {
  const busca = c.req.query('q')?.trim() ?? ''
  const result = await query(
    `with ${CURRENT_PERIOD_CTE}
     select col.id,col.matricula,col.nome,col.setor,col.turno
       from colaboradores col
      where col.ativo=true
        and ($2='' or col.nome ilike '%'||$2||'%' or coalesce(col.matricula,'') ilike '%'||$2||'%')
        and not ${periodPauseDoneSql('col.id')}
      order by col.nome limit 500`,
    [config.appTimezone, busca],
  )
  return c.json({ colaboradores: result.rows })
})

/**
 * Estado da pausa de UMA pessoa, consultado depois de ela se escolher na lista.
 *
 * O quiosque usa isto só para escrever o texto certo no ecrã ("digite o código
 * para sair" vs "digite o mesmo código para voltar") e para retomar a contagem
 * depois de o aparelho reiniciar. A decisão real continua a ser do servidor no
 * momento do registo — este endpoint não autoriza nada e não devolve códigos.
 */
pontoRoutes.get('/colaboradores/:id/pausa', async (c) => {
  const parsed = z.string().uuid().safeParse(c.req.param('id'))
  if (!parsed.success) return c.json({ erro: 'Colaborador inválido.' }, 400)

  const collaborator = (await query<{ id: string; nome: string; setor: string | null; turno: string | null }>(
    'select id,nome,setor,turno from colaboradores where id=$1 and ativo=true limit 1',
    [parsed.data],
  )).rows[0]
  if (!collaborator) return c.json({ erro: 'Colaborador não encontrado ou inativo.' }, 404)

  const open = (await query<{
    id: string
    periodo: 'MANHA' | 'TARDE'
    inicio_em: string
    inicio_local: string
    limite_segundos: number
    carencia_segundos: number
    tempo_decorrido_segundos: number
    retorno_ate_local: string
  }>(
    `select p.id,p.periodo,p.inicio_em::text,
            to_char(p.inicio_em at time zone $2,'HH24:MI') as inicio_local,
            p.limite_segundos,
            p.carencia_segundos,
            greatest(0,floor(extract(epoch from (now()-p.inicio_em)))::int) as tempo_decorrido_segundos,
            to_char(
              (p.inicio_em + ((p.carencia_segundos + p.limite_segundos) * interval '1 second')) at time zone $2,
              'HH24:MI'
            ) as retorno_ate_local
       from pausas_cafe p
      where p.colaborador_id=$1 and p.fim_em is null
      order by p.inicio_em desc limit 1`,
    [parsed.data, config.appTimezone],
  )).rows[0]

  const usedToday = await query<{ periodo: 'MANHA' | 'TARDE' }>(
    `select periodo from pausas_cafe
      where colaborador_id=$1
        and (inicio_em at time zone $2)::date=(now() at time zone $2)::date
      order by inicio_em`,
    [parsed.data, config.appTimezone],
  )

  return c.json({
    colaborador: collaborator,
    acaoEsperada: open ? 'RETORNO' : 'SAIDA',
    tamanhoCodigo: ACCESS_CODE_LENGTH,
    periodosUsadosHoje: usedToday.rows.map((row) => row.periodo),
    pausaAberta: open
      ? {
          id: open.id,
          periodo: open.periodo,
          inicioEm: open.inicio_em,
          inicioLocal: open.inicio_local,
          limiteSegundos: open.limite_segundos,
          carenciaSegundos: open.carencia_segundos,
          tempoDecorridoSegundos: open.tempo_decorrido_segundos,
          retornoAteLocal: open.retorno_ate_local,
        }
      : null,
  })
})
