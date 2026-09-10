-- Ponto Café - esquema PostgreSQL portátil
-- Não depende de Neon Auth, Supabase Auth, extensões proprietárias ou APIs do provedor.
-- UUIDs, hashes e criptografia dos templates faciais são gerados no backend.

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

create table if not exists usuarios (
    id uuid primary key,
    nome text not null,
    email text not null,
    senha_hash text not null,
    papel text not null check (papel in ('ADMIN','SUPERVISOR')),
    ativo boolean not null default true,
    criado_em timestamptz not null default now(),
    atualizado_em timestamptz not null default now()
);

create unique index if not exists ux_usuarios_email_normalizado
    on usuarios (lower(email));

create table if not exists sessoes (
    id uuid primary key,
    usuario_id uuid not null references usuarios(id) on delete cascade,
    refresh_token_hash text not null,
    dispositivo text,
    ip_origem inet,
    expira_em timestamptz not null,
    revogada_em timestamptz,
    criado_em timestamptz not null default now()
);

create table if not exists regras_cafe (
    id uuid primary key,
    periodo text not null unique check (periodo in ('MANHA','TARDE')),
    inicio time not null,
    fim time not null,
    limite_segundos integer not null default 450 check (limite_segundos > 0),
    ativo boolean not null default true
);

create table if not exists autorizacoes (
    id uuid primary key,
    colaborador_id uuid not null references colaboradores(id),
    supervisor_id uuid not null references usuarios(id),
    periodo text not null check (periodo in ('MANHA','TARDE')),
    codigo_hash text not null,
    motivo text,
    expira_em timestamptz not null,
    usado_em timestamptz,
    criado_em timestamptz not null default now()
);

create table if not exists pausas_cafe (
    id uuid primary key,
    colaborador_id uuid not null references colaboradores(id),
    periodo text not null check (periodo in ('MANHA','TARDE')),
    inicio_em timestamptz not null default now(),
    fim_em timestamptz,
    limite_segundos integer not null default 450 check (limite_segundos > 0),
    fora_horario boolean not null default false,
    autorizacao_id uuid references autorizacoes(id),
    criado_em timestamptz not null default now(),
    check (fim_em is null or fim_em >= inicio_em)
);

create unique index if not exists ux_pausa_periodo_dia
    on pausas_cafe (
        colaborador_id,
        periodo,
        ((inicio_em at time zone 'America/Fortaleza')::date)
    );

create table if not exists templates_faciais (
    id uuid primary key,
    colaborador_id uuid not null unique references colaboradores(id) on delete cascade,
    template_cifrado bytea not null,
    modelo text not null,
    versao_modelo text not null,
    criado_em timestamptz not null default now(),
    atualizado_em timestamptz not null default now()
);

create table if not exists auditoria (
    id bigint generated always as identity primary key,
    ator_usuario_id uuid references usuarios(id),
    ator_tipo text not null default 'SISTEMA',
    acao text not null,
    entidade text,
    entidade_id text,
    detalhes jsonb,
    criado_em timestamptz not null default now()
);

create index if not exists ix_pausas_abertas
    on pausas_cafe (inicio_em)
    where fim_em is null;

create index if not exists ix_pausas_colaborador
    on pausas_cafe (colaborador_id, inicio_em desc);

create index if not exists ix_autorizacoes_expiracao
    on autorizacoes (expira_em)
    where usado_em is null;

create index if not exists ix_sessoes_usuario_ativas
    on sessoes (usuario_id, expira_em)
    where revogada_em is null;

-- Os UUIDs são fixos apenas para permitir que este seed seja idempotente e portátil.
insert into regras_cafe (id, periodo, inicio, fim, limite_segundos)
values
    ('00000000-0000-4000-8000-000000000001', 'MANHA', '08:00', '10:00', 450),
    ('00000000-0000-4000-8000-000000000002', 'TARDE', '15:00', '17:00', 450)
on conflict (periodo) do nothing;
