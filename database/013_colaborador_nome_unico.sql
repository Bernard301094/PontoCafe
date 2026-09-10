-- 013_colaborador_nome_unico.sql
--
-- Impede dois colaboradores ativos com o mesmo nome.
--
-- Até aqui nada garantia isso: `colaboradores` só tinha `matricula` como única,
-- e matrícula deixou de ser usada. As três portas de entrada -- criação manual,
-- edição e importação CSV -- inseriam sem perguntar. Criar "João Silva" duas
-- vezes era um clique repetido.
--
-- E o custo disso não é cosmético. O quiosque lista nomes para a pessoa tocar
-- no próprio: com dois "João Silva" idênticos, ela não tem como saber qual é o
-- dela, e metade das vezes vai abrir a pausa na linha errada. O relatório
-- também passa a dividir a jornada de uma pessoa entre dois registos.
--
-- A unicidade é sobre o nome NORMALIZADO, não sobre o texto cru: "JOÃO SILVA",
-- "João Silva" e "Joao  Silva" são a mesma pessoa a tentar entrar três vezes.
--
-- Só vale para ativos (`where ativo=true`). Quem sai da empresa é arquivado com
-- ativo=false, e o histórico fica; se essa pessoa voltar, ou se outra com o
-- mesmo nome for contratada, o cadastro tem de ser possível.

begin;

-- Imutável de propósito: um índice não aceita função voláteis, e `unaccent()`
-- não é imutável sem um wrapper. `translate` resolve o mesmo problema sem
-- depender de extensão nenhuma estar instalada no servidor.
create or replace function pontocafe_nome_normalizado(nome text)
returns text
language sql
immutable
strict
as $$
  select lower(
           regexp_replace(
             translate(
               btrim(nome),
               'ÁÀÂÃÄáàâãäÉÈÊËéèêëÍÌÎÏíìîïÓÒÔÕÖóòôõöÚÙÛÜúùûüÇçÑñ',
               'AAAAAaaaaaEEEEeeeeIIIIiiiiOOOOOoooooUUUUuuuuCcNn'
             ),
             '\s+', ' ', 'g'
           )
         )
$$;

comment on function pontocafe_nome_normalizado(text) is
  'Nome sem acento, sem caixa e sem espaço duplo. Base da unicidade de colaborador ativo.';

-- Se esta linha falhar com "could not create unique index", já existem
-- duplicados no banco. Encontre-os com:
--
--   select pontocafe_nome_normalizado(nome) as chave,
--          count(*), array_agg(id), array_agg(nome)
--     from colaboradores where ativo=true
--    group by 1 having count(*) > 1;
--
-- Resolva antes de reaplicar: arquive o registo repetido (ativo=false) OU
-- corrija o nome. NÃO apague -- as pausas dele apontam para esse id.
create unique index if not exists ux_colaborador_nome_ativo
    on colaboradores (pontocafe_nome_normalizado(nome))
    where ativo = true;

commit;
