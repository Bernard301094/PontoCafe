-- Ponto Café - PIN de desbloqueio individual por dispositivo.
-- O PIN nunca é persistido em texto puro; o backend salva somente o hash HMAC.

alter table dispositivos add column if not exists unlock_pin_hash text;
alter table dispositivos add column if not exists unlock_pin_updated_at timestamptz;
alter table dispositivos add column if not exists unlock_fail_count integer not null default 0;
alter table dispositivos add column if not exists unlock_locked_until timestamptz;
