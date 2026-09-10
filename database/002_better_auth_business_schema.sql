-- Ponto Café - schema de negócio compatível com Better Auth.
-- O Better Auth mantém suas próprias tabelas; este arquivo contém apenas dados do Ponto Café.

create table if not exists colaboradores (
    id uuid primary key,
    matricula text unique,
    nome text not null,
    setor text,
    turno text,
    ativo boolean not null default true,
    criado_em timestamptz not null default now(),
    atualizado_em timestamptz not null default now()
);

create table if not exists dispositivos (
    id uuid primary key,
    nome text not null,
    token_hash text not null unique,
    ativo boolean not null default true,
    criado_em timestamptz not null default now(),
    atualizado_em timestamptz not null default now()
);

create table if not exists regras_cafe (
    id uuid primary key,
    periodo text not null unique check (periodo in ('MANHA','TARDE')),
    inicio time not null,
    fim time not null,
    limite_segundos integer not null default 900 check (limite_segundos > 0),
    ativo boolean not null default true
);

create table if not exists templates_faciais (
    id uuid primary key,
    colaborador_id uuid not null unique references colaboradores(id) on delete cascade,
    template_cifrado bytea not null,
    iv bytea not null,
    auth_tag bytea not null,
    dimensao integer not null check (dimensao between 64 and 2048),
    modelo text not null,
    versao_modelo text not null,
    criado_em timestamptz not null default now(),
    atualizado_em timestamptz not null default now()
);

create table if not exists verificacoes_faciais (
    id uuid primary key,
    colaborador_id uuid not null references colaboradores(id) on delete cascade,
    dispositivo_id uuid not null references dispositivos(id),
    token_hash text not null unique,
    score double precision not null,
    expira_em timestamptz not null,
    usado_em timestamptz,
    criado_em timestamptz not null default now()
);

create table if not exists autorizacoes (
    id uuid primary key,
    colaborador_id uuid not null references colaboradores(id),
    supervisor_auth_id text not null,
    periodo text not null check (periodo in ('MANHA','TARDE')),
    codigo_hash text not null,
    motivo text not null,
    expira_em timestamptz not null,
    usado_em timestamptz,
    cancelada_em timestamptz,
    criado_em timestamptz not null default now()
);

create table if not exists pausas_cafe (
    id uuid primary key,
    colaborador_id uuid not null references colaboradores(id),
    periodo text not null check (periodo in ('MANHA','TARDE')),
    inicio_em timestamptz not null default now(),
    fim_em timestamptz,
    limite_segundos integer not null default 900 check (limite_segundos > 0),
    fora_horario boolean not null default false,
    autorizacao_id uuid references autorizacoes(id),
    dispositivo_inicio_id uuid not null references dispositivos(id),
    dispositivo_fim_id uuid references dispositivos(id),
    verificacao_inicio_id uuid not null references verificacoes_faciais(id),
    verificacao_fim_id uuid references verificacoes_faciais(id),
    criado_em timestamptz not null default now(),
    check (fim_em is null or fim_em >= inicio_em)
);

create unique index if not exists ux_pausa_periodo_dia
    on pausas_cafe (colaborador_id, periodo, ((inicio_em at time zone 'America/Fortaleza')::date));
create unique index if not exists ux_pausa_aberta_por_colaborador
    on pausas_cafe (colaborador_id) where fim_em is null;

create table if not exists auditoria (
    id bigint generated always as identity primary key,
    ator_auth_id text,
    ator_tipo text not null default 'SISTEMA',
    acao text not null,
    entidade text,
    entidade_id text,
    detalhes jsonb,
    criado_em timestamptz not null default now()
);

create index if not exists ix_pausas_colaborador on pausas_cafe (colaborador_id, inicio_em desc);
create index if not exists ix_autorizacoes_expiracao on autorizacoes (expira_em) where usado_em is null and cancelada_em is null;
create index if not exists ix_verificacoes_expiracao on verificacoes_faciais (expira_em) where usado_em is null;

insert into regras_cafe (id, periodo, inicio, fim, limite_segundos)
values
    ('00000000-0000-4000-8000-000000000001','MANHA','08:00','10:00',900),
    ('00000000-0000-4000-8000-000000000002','TARDE','15:00','17:00',900)
on conflict (periodo) do update set
    inicio=excluded.inicio,
    fim=excluded.fim,
    limite_segundos=excluded.limite_segundos,
    ativo=true;
