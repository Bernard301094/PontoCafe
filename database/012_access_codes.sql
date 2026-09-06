-- 012_access_codes.sql
--
-- Substitui o reconhecimento facial pelo código de acesso emitido pelo Supervisor.
--
-- O que muda no modelo, e por quê:
--
--   1. `codigos_acesso` passa a ser a ÚNICA prova de que uma pausa foi liberada.
--      O código nasce ligado a um colaborador, vale uma vez na saída e uma vez no
--      retorno, e é a mesma linha que carrega as duas marcas temporais. Isso torna
--      impossível, no nível do esquema, fechar uma pausa com um código diferente
--      do que a abriu.
--
--   2. `templates_faciais` e `verificacoes_faciais` deixam de existir. Não é uma
--      desativação: o dado biométrico é apagado. Junto com elas saem as duas
--      colunas de pausas_cafe que apontavam para a verificação facial.
--
--   3. `autorizacoes` também sai. A liberação prévia fora de horário existia para
--      um mundo em que o rosto autorizava sozinho e o horário era o único freio.
--      Agora nenhuma pausa começa sem código, então "liberar previamente" e
--      "emitir um código" viraram a mesma ação -- manter as duas seria manter dois
--      caminhos para o mesmo efeito, com regras diferentes.
--
--   4. `carencia_segundos` guarda, POR PAUSA, quantos segundos correm antes de o
--      limite começar a contar. O valor fica na linha e não na configuração para
--      que um relatório de seis meses atrás continue sendo lido com a carência
--      que realmente valeu naquele dia.
--
-- Sobre guardar o código em texto puro: é deliberado. Ele não é credencial de
-- conta -- é um passe operacional de curta duração, preso a uma pessoa, que o
-- Supervisor precisa poder reler para ditar de novo a quem esqueceu. Só as rotas
-- autenticadas de Admin/Supervisor leem esta coluna; nenhuma rota de dispositivo
-- devolve o código, apenas o compara.

begin;

create table if not exists codigos_acesso (
    id uuid primary key,
    colaborador_id uuid not null references colaboradores(id) on delete cascade,
    codigo text not null,
    emitido_por_auth_id text not null,
    emitido_por_nome text,
    emitido_por_tipo text not null default 'SUPERVISOR',
    motivo text,
    criado_em timestamptz not null default now(),
    expira_em timestamptz not null,
    cancelado_em timestamptz,
    saida_em timestamptz,
    retorno_em timestamptz,
    pausa_id uuid,
    constraint ck_codigo_acesso_formato
        check (codigo ~ '^[A-Z0-9]{6}$'),
    constraint ck_codigo_acesso_expiracao
        check (expira_em > criado_em),
    -- Só existe retorno depois de uma saída, e o retorno nunca é anterior a ela.
    constraint ck_codigo_acesso_sequencia
        check (retorno_em is null or (saida_em is not null and retorno_em >= saida_em)),
    -- Cancelar um código já usado na saída deixaria a pessoa sem como voltar.
    constraint ck_codigo_acesso_cancelamento
        check (cancelado_em is null or saida_em is null)
);

-- Garante que a busca "este colaborador + este código" nunca devolva duas linhas
-- vivas. Códigos já consumidos ou cancelados saem do índice e liberam a
-- combinação para ser sorteada outra vez.
create unique index if not exists ux_codigo_acesso_vivo
    on codigos_acesso (colaborador_id, codigo)
    where cancelado_em is null and retorno_em is null;

-- A tela do Supervisor lista os códigos vivos da operação inteira.
create index if not exists ix_codigo_acesso_pendente
    on codigos_acesso (criado_em desc)
    where cancelado_em is null and retorno_em is null;

create index if not exists ix_codigo_acesso_colaborador
    on codigos_acesso (colaborador_id, criado_em desc);

alter table pausas_cafe
    add column if not exists codigo_acesso_id uuid references codigos_acesso(id);

-- A coluna entra com default 0 e só DEPOIS passa a nascer com 60: assim as
-- pausas antigas ficam com a carência que de facto tiveram (nenhuma) e as novas
-- nascem com o minuto de tolerância, sem precisar de um UPDATE que voltaria a
-- correr a cada reaplicação desta migração.
alter table pausas_cafe
    add column if not exists carencia_segundos integer not null default 0;
alter table pausas_cafe
    alter column carencia_segundos set default 60;

alter table pausas_cafe
    drop constraint if exists ck_pausa_carencia_valida;
alter table pausas_cafe
    add constraint ck_pausa_carencia_valida
    check (carencia_segundos >= 0 and carencia_segundos <= 3600);

create index if not exists ix_pausas_codigo_acesso
    on pausas_cafe (codigo_acesso_id)
    where codigo_acesso_id is not null;

-- O invariante de 011 dizia "toda pausa aberta tem prova biométrica OU um
-- responsável identificado". A prova biométrica deixou de existir, então o
-- primeiro ramo passa a ser o dispositivo do quiosque -- que é quem apresenta o
-- código. O segundo ramo, a abertura manual, fica exatamente como estava.
alter table pausas_cafe
    drop constraint if exists ck_pausa_inicio_coerente;

alter table pausas_cafe
    drop column if exists verificacao_inicio_id;
alter table pausas_cafe
    drop column if exists verificacao_fim_id;
alter table pausas_cafe
    drop column if exists autorizacao_id;

alter table pausas_cafe
    add constraint ck_pausa_inicio_coerente check (
        (inicio_registrado_manualmente = false
            and dispositivo_inicio_id is not null
            and inicio_motivo_manual is null
            and inicio_ator_auth_id is null
            and inicio_ator_tipo is null
            and inicio_registrado_em is null)
        or
        (inicio_registrado_manualmente = true
            and dispositivo_inicio_id is null
            and inicio_motivo_manual is not null
            and length(btrim(inicio_motivo_manual)) > 0
            and inicio_ator_auth_id is not null
            and inicio_ator_tipo is not null
            and inicio_registrado_em is not null)
    );

-- NOT VALID de propósito: as pausas anteriores a esta migração nasceram de um
-- rosto e não têm código nenhum para apontar -- não há como as tornar válidas
-- retroativamente. A restrição vale para tudo o que for gravado a partir daqui,
-- que é exatamente onde o invariante precisa de valer.
alter table pausas_cafe
    drop constraint if exists ck_pausa_codigo_obrigatorio;
alter table pausas_cafe
    add constraint ck_pausa_codigo_obrigatorio check (
        inicio_registrado_manualmente = true or codigo_acesso_id is not null
    ) not valid;

alter table codigos_acesso
    drop constraint if exists fk_codigo_acesso_pausa;
alter table codigos_acesso
    add constraint fk_codigo_acesso_pausa
    foreign key (pausa_id) references pausas_cafe(id) on delete set null;

-- O quiosque passou a ter uma única mutação ('REGISTRO'): o servidor é que
-- decide se aquele código é saída ou retorno. Os tipos antigos continuam aceites
-- para que o diário idempotente já gravado permaneça legível.
alter table operacoes_ponto_idempotentes
    drop constraint if exists chk_operacoes_ponto_tipo;
alter table operacoes_ponto_idempotentes
    add constraint chk_operacoes_ponto_tipo
    check (tipo in ('REGISTRO','REGISTRO_RAPIDO','INICIAR','FINALIZAR'));

drop table if exists verificacoes_faciais;
drop table if exists templates_faciais;
drop table if exists autorizacoes;

-- O avatar existia para o quiosque confirmar visualmente quem o rosto tinha
-- reconhecido. Sem reconhecimento, a pessoa escolhe o próprio nome numa lista e
-- a foto deixa de provar seja o que for.
alter table colaboradores
    drop constraint if exists colaboradores_avatar_version_check;
alter table colaboradores
    drop column if exists avatar_version;

-- O quiosque conta as tentativas erradas recentes de cada pessoa antes de
-- aceitar um código. Sem este índice parcial essa contagem varreria a auditoria
-- inteira a cada digitação.
create index if not exists ix_auditoria_codigo_tentativa
    on auditoria (entidade_id, criado_em desc)
    where acao='CODIGO_ACESSO_TENTATIVA_INVALIDA';

comment on table codigos_acesso is
  'Passe de café emitido pelo Supervisor: preso a um colaborador, vale uma saída e um retorno.';
comment on column codigos_acesso.saida_em is
  'Momento em que o código foi usado para SAIR. Depois disto o código já não pode ser cancelado.';
comment on column codigos_acesso.retorno_em is
  'Momento em que o mesmo código foi usado para VOLTAR. Depois disto o código está esgotado.';
comment on column pausas_cafe.carencia_segundos is
  'Segundos de tolerância antes de o limite começar a contar. O prazo real é inicio_em + carencia_segundos + limite_segundos.';

commit;
