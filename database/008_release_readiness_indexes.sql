-- Ponto Café 1.0.0
-- Índices de observabilidade/retenção usados pela central Saúde do sistema e
-- pela manutenção agendada. Aplicar depois da migração 007.

create index if not exists idx_operacoes_ponto_concluido_em
  on operacoes_ponto_idempotentes (concluido_em);

-- Leitura da telemetria mais recente por dispositivo.
create index if not exists idx_auditoria_app_health_dispositivo_criado
  on auditoria (entidade_id, criado_em desc)
  where acao='APP_HEALTH' and entidade='DISPOSITIVO';

-- Retenção cronológica de APP_HEALTH em toda a frota. O índice acima começa por
-- entidade_id e é ótimo para lookup por dispositivo; este evita varrer toda a
-- auditoria durante a limpeza diária por criado_em.
create index if not exists idx_auditoria_app_health_criado
  on auditoria (criado_em)
  where acao='APP_HEALTH' and entidade='DISPOSITIVO';

comment on index idx_operacoes_ponto_concluido_em is
  'Acelera a retenção programada do diário idempotente do Ponto.';

comment on index idx_auditoria_app_health_dispositivo_criado is
  'Acelera a leitura da última telemetria técnica APP_HEALTH por dispositivo.';

comment on index idx_auditoria_app_health_criado is
  'Acelera a retenção cronológica da telemetria técnica APP_HEALTH.';
