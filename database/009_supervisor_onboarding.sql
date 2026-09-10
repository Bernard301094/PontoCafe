-- Ponto Café 1.0.x — onboarding seguro de Supervisor.
-- Adiciona somente metadados de conta; não altera senhas, sessões ou dados operacionais existentes.

alter table "user"
  add column if not exists turno text;

alter table "user"
  add column if not exists "mustChangePassword" boolean not null default false;

-- O DROP IF EXISTS mantém a migração reaplicável sem depender de bloco DO $$,
-- que alguns executores de migrations dividem incorretamente por ponto e vírgula.
alter table "user"
  drop constraint if exists user_turno_valid_chk;

alter table "user"
  add constraint user_turno_valid_chk
  check (turno is null or turno in ('A','B','C','D')) not valid;

alter table "user" validate constraint user_turno_valid_chk;

comment on column "user".turno is
  'Turno operacional do Supervisor: A, B, C ou D. Administradores podem manter NULL.';

comment on column "user"."mustChangePassword" is
  'Quando true, o usuário autenticado deve trocar a senha temporária antes de acessar rotas protegidas.';
