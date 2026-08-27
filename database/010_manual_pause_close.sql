-- 010_manual_pause_close.sql
--
-- Fecho manual de pausa por Admin/Supervisor.
--
-- O cliente Android ja envia para admin/pausas/manual/finalizar e espera
-- `registradoManualmente` e `registradoPor` na resposta, mas o esquema nao tinha
-- onde guardar isso. Sem estas colunas o fecho manual seria indistinguivel de um
-- fecho biometrico no historico — exatamente a informacao que uma auditoria de
-- jornada precisa ver.
--
-- verificacao_fim_id continua nullable, entao um fecho manual simplesmente nao
-- preenche esse campo: a sessao autenticada de quem registrou substitui a prova
-- biometrica, e fica rastreada nas colunas abaixo.

alter table pausas_cafe
    add column if not exists fim_registrado_manualmente boolean not null default false,
    add column if not exists fim_motivo_manual text,
    add column if not exists fim_ator_auth_id text,
    add column if not exists fim_ator_tipo text,
    add column if not exists fim_registrado_em timestamptz;

-- Um fecho manual exige motivo. Um fecho biometrico nao pode ter um.
alter table pausas_cafe
    drop constraint if exists ck_pausa_fim_manual_coerente;
alter table pausas_cafe
    add constraint ck_pausa_fim_manual_coerente check (
        (fim_registrado_manualmente = false
            and fim_motivo_manual is null
            and fim_ator_auth_id is null
            and fim_ator_tipo is null)
        or
        (fim_registrado_manualmente = true
            and fim_em is not null
            and fim_motivo_manual is not null
            and length(btrim(fim_motivo_manual)) > 0
            and fim_ator_auth_id is not null
            and fim_ator_tipo is not null)
    );

-- Relatorios de jornada precisam separar fechos manuais rapidamente.
create index if not exists ix_pausa_fim_manual
    on pausas_cafe (fim_registrado_manualmente, fim_registrado_em)
    where fim_registrado_manualmente = true;
