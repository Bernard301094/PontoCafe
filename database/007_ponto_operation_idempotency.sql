-- Ponto Café 0.15.0
-- Identidade durável das operações críticas do Ponto.
--
-- A mesma operação pode atravessar online -> resposta incerta -> fila offline
-- sem virar um novo início/retorno. O resultado confirmado é persistido na
-- MESMA transação que altera pausas_cafe e pode ser reproduzido com segurança.

create table if not exists operacoes_ponto_idempotentes (
  operacao_id uuid primary key,
  dispositivo_id uuid not null references dispositivos(id) on delete cascade,
  colaborador_id uuid not null references colaboradores(id),
  tipo varchar(32) not null,
  pausa_id uuid null references pausas_cafe(id) on delete set null,
  resposta jsonb not null,
  criado_em timestamptz not null default now(),
  concluido_em timestamptz not null default now()
);

-- Compatível também com uma aplicação antecipada de uma revisão anterior da 007,
-- que aceitava somente REGISTRO_RAPIDO.
alter table operacoes_ponto_idempotentes
  drop constraint if exists operacoes_ponto_idempotentes_tipo_check;
alter table operacoes_ponto_idempotentes
  drop constraint if exists chk_operacoes_ponto_tipo;
alter table operacoes_ponto_idempotentes
  add constraint chk_operacoes_ponto_tipo
  check (tipo in ('REGISTRO_RAPIDO','INICIAR','FINALIZAR'));

create index if not exists idx_operacoes_ponto_dispositivo_criado
  on operacoes_ponto_idempotentes(dispositivo_id, criado_em desc);

create index if not exists idx_operacoes_ponto_colaborador_criado
  on operacoes_ponto_idempotentes(colaborador_id, criado_em desc);

comment on table operacoes_ponto_idempotentes is
  'Resultados idempotentes de mutações do Ponto, usados para replay seguro após resposta de rede incerta.';
