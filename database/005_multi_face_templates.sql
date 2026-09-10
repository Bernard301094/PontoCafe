-- Ponto Café - múltiplas aparências faciais por colaborador.
-- Permite reconhecer a mesma pessoa com/sem touca, com/sem óculos e outras
-- variações reais sem reduzir o limiar de segurança do Ponto.

begin;

alter table templates_faciais
    drop constraint if exists templates_faciais_colaborador_id_key;

alter table templates_faciais
    add column if not exists tipo text not null default 'LEGADO';

alter table templates_faciais
    add column if not exists lote_id uuid;

-- A coluna foi adicionada sem CHECK para manter a migração idempotente em bancos
-- que já receberam versões intermediárias. A aplicação só grava os tipos abaixo.
update templates_faciais
   set tipo='LEGADO'
 where tipo is null or btrim(tipo)='';

create index if not exists ix_templates_faciais_colaborador
    on templates_faciais (colaborador_id, atualizado_em desc);

create index if not exists ix_templates_faciais_modelo
    on templates_faciais (modelo, versao_modelo, colaborador_id);

create index if not exists ix_templates_faciais_lote
    on templates_faciais (lote_id)
    where lote_id is not null;

commit;
