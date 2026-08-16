-- Ponto Café - registro seguro e idempotente de dispositivos

create table if not exists dispositivos (
    id uuid primary key default gen_random_uuid(),
    installation_id text not null unique,
    nome text not null,
    pin_hash text not null,
    ativo boolean not null default false,
    criado_em timestamptz not null default now(),
    atualizado_em timestamptz not null default now(),
    constraint ck_dispositivos_installation_id check (char_length(installation_id) between 8 and 128),
    constraint ck_dispositivos_nome check (char_length(nome) between 2 and 80)
);

create table if not exists device_activation_tokens (
    id uuid primary key default gen_random_uuid(),
    dispositivo_id uuid not null references dispositivos(id) on delete cascade,
    token_hash text not null unique,
    expira_em timestamptz not null,
    usado_em timestamptz,
    criado_em timestamptz not null default now()
);

create table if not exists device_registration_requests (
    id uuid primary key default gen_random_uuid(),
    idempotency_key text not null unique,
    status_code integer not null check (status_code between 200 and 299),
    response_body jsonb not null,
    criado_em timestamptz not null default now(),
    constraint ck_device_registration_idempotency_key check (char_length(idempotency_key) between 16 and 128)
);

create index if not exists ix_device_activation_tokens_pending
on device_activation_tokens (dispositivo_id, expira_em)
where usado_em is null;

create index if not exists ix_device_registration_requests_created
on device_registration_requests (criado_em);
