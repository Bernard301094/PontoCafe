/**
 * Sinais de que uma pausa pode não ter acontecido como está registada.
 *
 * O código de acesso prende a pausa a uma pessoa, mas não prende ao corpo dela:
 * quem sai pode entregar o código a um colega e pedir que registe o retorno
 * enquanto continua no café. O servidor não tem como recusar isso — o par
 * (nome, código) está correto. O que sobra é olhar o padrão depois.
 *
 * Nada aqui prova coisa nenhuma, e é importante que a interface não finja que
 * prova. O que sai daqui é uma lista ordenada para alguém olhar, não uma
 * acusação: um caso isolado é ruído, e o mesmo nome a repetir-se em sinais
 * diferentes na mesma semana é conversa a ter.
 */
import { query } from './db.js'
import { config } from './config.js'

export type AnomalySignal =
  | 'PAUSA_CURTA'
  | 'RETORNO_EM_RAJADA'
  | 'DISPOSITIVOS_DIFERENTES'
  | 'EXCESSO_RECORRENTE'

export type AnomalyRow = {
  colaboradorId: string
  nome: string
  setor: string | null
  totalPausas: number
  medianaSegundos: number
  pausasCurtas: number
  retornosEmRajada: number
  dispositivosDiferentes: number
  excessos: number
  pontuacao: number
  sinais: AnomalySignal[]
}

/**
 * Quantos segundos separam dois retornos para os considerarmos uma rajada.
 *
 * Trinta segundos é curto de propósito. Duas pessoas que voltam juntas do café
 * demoram mais do que isso a digitar dois códigos de seis caracteres, uma de
 * cada vez; o que cabe em trinta segundos é a mesma pessoa a bater por ambas.
 */
const RAJADA_SEGUNDOS = 30

/**
 * Uma pausa é "curta" quando fica abaixo de 40% da mediana da própria pessoa.
 *
 * O corte é relativo, e não um piso fixo, porque a distância até o café varia
 * muito: quem trabalha ao lado volta legitimamente em quatro minutos, e um piso
 * absoluto marcaria essa pessoa todos os dias. O que interessa é a mudança de
 * comportamento — quem sempre fica catorze minutos e passa a ficar três.
 */
const FRACAO_PAUSA_CURTA = 0.4

/** Abaixo disto não há padrão para comparar, e a mediana não significa nada. */
const MINIMO_DE_PAUSAS = 4

export async function anomalyReport(inicio: string, fim: string): Promise<AnomalyRow[]> {
  const result = await query<AnomalyRow & { sinais: string[] }>(
    `with pausas as (
       select p.id,
              p.colaborador_id,
              c.nome,
              c.setor,
              p.inicio_em,
              p.fim_em,
              p.dispositivo_inicio_id,
              p.dispositivo_fim_id,
              extract(epoch from (p.fim_em - p.inicio_em))::int as duracao,
              p.limite_segundos + p.carencia_segundos as teto
         from pausas_cafe p
         join colaboradores c on c.id = p.colaborador_id
        where p.fim_em is not null
          and (p.inicio_em at time zone $1)::date between $2::date and $3::date
     ),
     base as (
       select colaborador_id,
              count(*)::int as total,
              percentile_cont(0.5) within group (order by duracao)::int as mediana
         from pausas
        group by colaborador_id
     ),
     -- Retornos do MESMO quiosque separados por poucos segundos. A janela olha
     -- para trás e para a frente: numa rajada de três, a do meio tem vizinho
     -- dos dois lados e todas as três precisam de aparecer.
     rajadas as (
       select a.id, a.colaborador_id
         from pausas a
         join pausas b
           on b.dispositivo_fim_id = a.dispositivo_fim_id
          and b.id <> a.id
          and a.dispositivo_fim_id is not null
          and abs(extract(epoch from (b.fim_em - a.fim_em))) <= $4
        group by a.id, a.colaborador_id
     )
     select p.colaborador_id as "colaboradorId",
            max(p.nome) as nome,
            max(p.setor) as setor,
            max(b.total) as "totalPausas",
            max(b.mediana) as "medianaSegundos",
            count(*) filter (
              where b.total >= $5 and p.duracao < b.mediana * $6
            )::int as "pausasCurtas",
            count(*) filter (where r.id is not null)::int as "retornosEmRajada",
            count(*) filter (
              where p.dispositivo_inicio_id is not null
                and p.dispositivo_fim_id is not null
                and p.dispositivo_inicio_id <> p.dispositivo_fim_id
            )::int as "dispositivosDiferentes",
            count(*) filter (where p.duracao > p.teto)::int as excessos
       from pausas p
       join base b on b.colaborador_id = p.colaborador_id
       left join rajadas r on r.id = p.id
      group by p.colaborador_id
     having count(*) filter (where b.total >= $5 and p.duracao < b.mediana * $6) > 0
         or count(*) filter (where r.id is not null) > 0
         or count(*) filter (
              where p.dispositivo_inicio_id is not null
                and p.dispositivo_fim_id is not null
                and p.dispositivo_inicio_id <> p.dispositivo_fim_id
            ) > 0
         or count(*) filter (where p.duracao > p.teto) > 2
      order by "retornosEmRajada" desc, "pausasCurtas" desc, excessos desc
      limit 100`,
    [config.appTimezone, inicio, fim, RAJADA_SEGUNDOS, MINIMO_DE_PAUSAS, FRACAO_PAUSA_CURTA],
  )

  return result.rows.map((row) => {
    const sinais: AnomalySignal[] = []
    if (row.retornosEmRajada > 0) sinais.push('RETORNO_EM_RAJADA')
    if (row.pausasCurtas > 0) sinais.push('PAUSA_CURTA')
    if (row.dispositivosDiferentes > 0) sinais.push('DISPOSITIVOS_DIFERENTES')
    if (row.excessos > 2) sinais.push('EXCESSO_RECORRENTE')

    return {
      ...row,
      sinais,
      // A rajada pesa mais porque é o único sinal com uma explicação inocente
      // difícil: duas pessoas não digitam dois códigos em trinta segundos. Os
      // outros três têm leituras honestas e servem sobretudo para confirmar.
      pontuacao: row.retornosEmRajada * 5 +
        row.pausasCurtas * 3 +
        row.dispositivosDiferentes * 2 +
        Math.max(0, row.excessos - 2),
    }
  }).sort((a, b) => b.pontuacao - a.pontuacao)
}
