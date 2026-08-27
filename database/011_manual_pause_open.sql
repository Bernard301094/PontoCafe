-- 011_manual_pause_open.sql
--
-- Abertura manual de pausa por Admin/Supervisor.
--
-- 010 registou que a abertura manual ficava DE FORA de proposito: abrir uma pausa
-- sem evidencia biometrica cria um registo de jornada inteiro apoiado apenas na
-- sessao de quem regista. Essa decisao foi revertida deliberadamente -- o cliente
-- Android ja mostrava o botao "manual" nas telas de autorizacao e o operador
-- recebia "Rota nao encontrada" ao usa-lo. Mas a garantia que a decisao protegia
-- NAO e simplesmente descartada aqui.
--
-- O esquema garantia essa prova com NOT NULL em verificacao_inicio_id e
-- dispositivo_inicio_id. Uma abertura manual nao tem nenhuma das duas, entao as
-- colunas passam a aceitar null. Para o invariante nao evaporar junto, ele deixa
-- de ser "toda pausa tem prova biometrica" e passa a ser, no mesmo nivel do
-- esquema:
--
--     toda pausa aberta tem OU prova biometrica (verificacao + dispositivo)
--     OU um responsavel identificado com motivo e data. Nunca nenhuma das duas.
--
-- Isto e mais fraco do que antes, e a diferenca e real: um Admin comprometido
-- passa a conseguir abrir jornada de terceiros sem tocar no quiosque. O que o
-- esquema ainda garante e que essa abertura fica nominal, datada e com motivo --
-- nunca anonima, e nunca confundivel com uma marcacao biometrica no historico.
--
-- O que esta migracao NAO faz, e que a nota de 010 pedia: perfil proprio (hoje
-- qualquer ADMIN/SUPERVISOR pode), janela temporal (nao ha limite de quao para
-- tras se pode abrir) e aprovacao de um segundo ator. Se algum desses for
-- exigido, e uma migracao a mais, nao uma alteracao desta.

alter table pausas_cafe alter column dispositivo_inicio_id drop not null;
alter table pausas_cafe alter column verificacao_inicio_id drop not null;

alter table pausas_cafe
    add column if not exists inicio_registrado_manualmente boolean not null default false,
    add column if not exists inicio_motivo_manual text,
    add column if not exists inicio_ator_auth_id text,
    add column if not exists inicio_ator_tipo text,
    add column if not exists inicio_registrado_em timestamptz;

-- Este check e o que substitui os dois NOT NULL removidos acima. Linhas antigas
-- continuam validas: nasceram com verificacao_inicio_id preenchido e com o
-- default false na coluna nova.
alter table pausas_cafe
    drop constraint if exists ck_pausa_inicio_coerente;
alter table pausas_cafe
    add constraint ck_pausa_inicio_coerente check (
        (inicio_registrado_manualmente = false
            and verificacao_inicio_id is not null
            and dispositivo_inicio_id is not null
            and inicio_motivo_manual is null
            and inicio_ator_auth_id is null
            and inicio_ator_tipo is null
            and inicio_registrado_em is null)
        or
        (inicio_registrado_manualmente = true
            and verificacao_inicio_id is null
            and dispositivo_inicio_id is null
            and inicio_motivo_manual is not null
            and length(btrim(inicio_motivo_manual)) > 0
            and inicio_ator_auth_id is not null
            and inicio_ator_tipo is not null
            and inicio_registrado_em is not null)
    );

-- Relatorios de jornada precisam separar aberturas manuais tao rapido quanto
-- separam fechos manuais (ix_pausa_fim_manual, em 010).
create index if not exists ix_pausa_inicio_manual
    on pausas_cafe (inicio_registrado_manualmente, inicio_registrado_em)
    where inicio_registrado_manualmente = true;
