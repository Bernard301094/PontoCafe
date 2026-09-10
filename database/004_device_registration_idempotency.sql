-- Ponto Café - replay seguro e temporário de cadastro de dispositivos.
-- Migração aditiva: não altera a estrutura existente de dispositivos.

create table if not exists device_registration_idempotency (
    idempotency_key text primary key,
    ator_auth_id text not null,
    request_fingerprint text not null,
    request_nonce uuid not null,
    dispositivo_id uuid not null,
    token_ciphertext bytea not null,
    token_iv bytea not null,
    token_auth_tag bytea not null,
    http_status smallint not null default 201,
    criado_em timestamptz not null default now(),
    expira_em timestamptz not null,
    constraint ck_device_registration_idempotency_key
        check (char_length(idempotency_key) between 16 and 128),
    constraint ck_device_registration_fingerprint
        check (request_fingerprint ~ '^[0-9a-f]{64}$'),
    constraint ck_device_registration_http_status
        check (http_status between 200 and 299),
    constraint ck_device_registration_expiry
        check (expira_em > criado_em),
    constraint fk_device_registration_device
        foreign key (dispositivo_id)
        references dispositivos(id)
        on delete cascade
        deferrable initially deferred
);

create index if not exists ix_device_registration_idempotency_expira_em
    on device_registration_idempotency (expira_em);

create index if not exists ix_device_registration_idempotency_device
    on device_registration_idempotency (dispositivo_id);

grant select, insert, update, delete on table device_registration_idempotency to ponto_cafe_api;
