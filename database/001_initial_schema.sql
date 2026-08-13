-- Ponto Café - esquema inicial para Neon PostgreSQL

create extension if not exists pgcrypto;

create table if not exists colaboradores (
    id uuid primary key default gen_random_uuid(),
    matricula text unique,
    nome text not null,
    setor text,
    turno text,
    ativo boolean not null default true,
    criado_em timestamptz not null default now(),
    atualizado_em timestamptz not null default now()
);

create table if not exists perfis (
    id uuid primary key default gen_random_uuid(),
    auth_user_id text unique not null,
    nome text not null,
    papel text not null check (papel in ('ADMIN','SUPERVISOR')),
    ativo boolean not null default true,
    criado_em timestamptz not null default now()
);

create table if not exists regras_cafe (
    id uuid primary key default gen_random_uuid(),
    periodo text not null unique check (periodo in ('MANHA','TARDE')),
    inicio time not null,
    fim time not null,
    limite_segundos integer not null default 450 check (limite_segundos > 0),
    ativo boolean not null default true
);

insert into regras_cafe (periodo, inicio, fim, limite_segundos)
values
    ('MANHA', '08:00', '10:00', 450),
    ('TARDE', '15:00', '17:00', 450)
on conflict (periodo) do nothing;

create table if not exists autorizacoes (
    id uuid primary key default gen_random_uuid(),
    colaborador_id uuid not null references colaboradores(id),
    supervisor_id uuid not null references perfis(id),
    periodo text not null check (periodo in ('MANHA','TARDE')),
    codigo_hash text not null,
    motivo text,
    expira_em timestamptz not null,
    usado_em timestamptz,
    criado_em timestamptz not null default now()
);

create table if not exists pausas_cafe (
    id uuid primary key default gen_random_uuid(),
    colaborador_id uuid not null references colaboradores(id),
    periodo text not null check (periodo in ('MANHA','TARDE')),
    inicio_em timestamptz not null default now(),
    fim_em timestamptz,
    limite_segundos integer not null default 450,
    fora_horario boolean not null default false,
    autorizacao_id uuid references autorizacoes(id),
    criado_em timestamptz not null default now()
);

create unique index if not exists ux_pausa_periodo_dia
on pausas_cafe (colaborador_id, periodo, ((inicio_em at time zone 'America/Fortaleza')::date));

create table if not exists templates_faciais (
    id uuid primary key default gen_random_uuid(),
    colaborador_id uuid not null unique references colaboradores(id) on delete cascade,
    template_cifrado bytea not null,
    modelo text not null,
    criado_em timestamptz not null default now(),
    atualizado_em timestamptz not null default now()
);

create table if not exists auditoria (
    id bigserial primary key,
    ator text,
    acao text not null,
    entidade text,
    entidade_id text,
    detalhes jsonb,
    criado_em timestamptz not null default now()
);

create index if not exists ix_pausas_abertas on pausas_cafe (inicio_em) where fim_em is null;
create index if not exists ix_pausas_colaborador on pausas_cafe (colaborador_id, inicio_em desc);
create index if not exists ix_autorizacoes_expiracao on autorizacoes (expira_em) where usado_em is null;
