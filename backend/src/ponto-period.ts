/**
 * O período de café vigente agora, como fragmento SQL.
 *
 * `resolvePeriod` em `ponto-registration.ts` responde a mesma pergunta em
 * TypeScript, no momento em que uma pausa é aberta. Aqui a mesma regra precisa
 * existir dentro de uma consulta: as listas de pessoas têm de saber, por linha,
 * se aquele colaborador já fechou a pausa deste período — e trazer isso com um
 * ida-e-volta por colaborador seria uma consulta por pessoa numa lista de quase
 * cem.
 *
 * A regra, nas duas implementações, é a mesma e nesta ordem:
 *
 *   1. a janela ativa que contém a hora local; se não houver,
 *   2. a janela ativa temporalmente mais próxima — porque o código de acesso
 *      libera café a qualquer hora, e a pausa fora de janela ainda precisa de
 *      uma etiqueta MANHÃ/TARDE para o relatório.
 *
 * Se as duas divergirem, o quiosque esconde uma pessoa que o servidor ainda
 * deixaria sair, ou mostra uma que ele vai recusar. O teste de contrato
 * `access-code-flow-contract` prende as duas a esta descrição.
 */
export const CURRENT_PERIOD_CTE = `
  periodo_atual as (
    select coalesce(
      (select periodo
         from regras_cafe
        where ativo=true
          and (now() at time zone $1)::time>=inicio
          and (now() at time zone $1)::time<fim
        order by inicio
        limit 1),
      (select periodo
         from regras_cafe
        where ativo=true
        order by least(
                   abs(extract(epoch from ((now() at time zone $1)::time - inicio))),
                   abs(extract(epoch from ((now() at time zone $1)::time - fim)))
                 ) asc,
                 inicio asc
        limit 1)
    ) as periodo
  )`

/**
 * Verdadeiro quando a pessoa já **fechou** a pausa do período vigente hoje.
 *
 * Fechou, não abriu: quem está no café agora tem `fim_em is null` e continua
 * na lista, porque é exatamente essa pessoa que ainda precisa do quiosque para
 * registar o retorno. Some depois de voltar, e reaparece no período seguinte.
 *
 * [colaboradorRef] é a coluna do id na consulta que usa este fragmento — o
 * chamador escolhe o alias, e o timezone continua sendo `$1`.
 */
export const periodPauseDoneSql = (colaboradorRef: string) => `
    exists (
      select 1
        from pausas_cafe p, periodo_atual pa
       where p.colaborador_id=${colaboradorRef}
         and p.periodo=pa.periodo
         and p.fim_em is not null
         and (p.inicio_em at time zone $1)::date=(now() at time zone $1)::date
    )`
