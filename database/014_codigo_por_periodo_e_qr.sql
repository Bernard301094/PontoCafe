-- 014_codigo_por_periodo_e_qr.sql
--
-- Dois códigos por dia, um por período, e a porta do QR no dispositivo.
--
-- O que muda, e por quê:
--
--   1. `codigos_acesso.periodo` + `dia_operacional`. Até aqui um colaborador
--      tinha, no máximo, UM código vivo: emitir outro cancelava o anterior. Isso
--      servia a um código que nascia no instante da pausa e morria em dois
--      minutos. Passa a servir a outra coisa -- o código da MANHÃ e o da TARDE
--      são emitidos juntos, de manhã, e têm de coexistir o dia inteiro. Sem a
--      etiqueta de período, emitir o segundo apagaria o primeiro.
--
--      `dia_operacional` existe porque um índice não pode chamar
--      `at time zone 'America/Fortaleza'`: essa conversão é STABLE, não
--      IMMUTABLE, e o Postgres recusa-a numa expressão indexada. O dia é
--      calculado por quem insere -- é o mesmo dia que o relatório usa.
--
--   2. `dispositivos.qr_habilitado`. A leitura de QR não nasce ligada. Um
--      aparelho só passa a aceitar código lido pela câmara depois de o
--      Administrador ou o Supervisor o liberar, e o registo dessa liberação
--      fica em `qr_atualizado_em` + auditoria.
--
--      Isto é um portão operacional, não uma fronteira de segurança: o QR
--      carrega o mesmo código de 6 caracteres que a pessoa poderia digitar à
--      mão. O que o portão dá é a decisão de quando a operação passa a aceitar
--      leitura por câmara, e o que a auditoria dá é a visibilidade de quais
--      batidas vieram por ali.
--
-- Sobre o alcance do prazo: um código de período vive até ao FIM da janela do
-- seu período, não dois minutos. É a troca que o modelo aceita para que o QR
-- possa estar no telemóvel da pessoa desde manhã. O vencimento continua a
-- travar só a SAÍDA -- quem já saiu volta com o mesmo código, sem prazo, porque
-- negar o retorno deixaria a pausa aberta para sempre.

begin;

-- ---------------------------------------------------------------------------
-- 1. Código por período
-- ---------------------------------------------------------------------------

alter table codigos_acesso
    add column if not exists periodo text;

alter table codigos_acesso
    add column if not exists dia_operacional date;

-- Nulos de propósito: os códigos emitidos antes desta migração nasceram sem
-- período nenhum e não há como lhes atribuir um retroativamente. Eles continuam
-- a funcionar pela regra antiga (um código vivo por pessoa) até se esgotarem.
alter table codigos_acesso
    drop constraint if exists ck_codigo_acesso_periodo;
alter table codigos_acesso
    add constraint ck_codigo_acesso_periodo
    check (periodo is null or periodo in ('MANHA','TARDE'));

-- Período e dia andam sempre juntos. Um código de período sem dia não teria
-- como ser encontrado pelo índice de unicidade abaixo, e um dia sem período
-- não diria a que pausa pertence.
alter table codigos_acesso
    drop constraint if exists ck_codigo_acesso_periodo_dia;
alter table codigos_acesso
    add constraint ck_codigo_acesso_periodo_dia
    check ((periodo is null) = (dia_operacional is null));

-- O invariante novo: uma pessoa tem no máximo um código vivo POR PERÍODO e POR
-- DIA. Um código já devolvido (retorno_em) ou cancelado sai do índice, e o par
-- fica livre outra vez -- o que importa para quem devolve o código da manhã e
-- precisa que a tarde continue a sua.
create unique index if not exists ux_codigo_acesso_periodo_dia
    on codigos_acesso (colaborador_id, periodo, dia_operacional)
    where cancelado_em is null and retorno_em is null and periodo is not null;

-- A tela do Supervisor passa a listar "os códigos de hoje", não só os vivos.
create index if not exists ix_codigo_acesso_dia
    on codigos_acesso (dia_operacional desc, periodo)
    where dia_operacional is not null;

comment on column codigos_acesso.periodo is
  'MANHA ou TARDE: a qual das duas pausas do dia este código pertence. Nulo nos códigos anteriores a 014.';
comment on column codigos_acesso.dia_operacional is
  'Dia local (America/Fortaleza) a que o código pertence. Calculado por quem insere, porque a conversão de fuso não é indexável.';

-- ---------------------------------------------------------------------------
-- 2. Liberação da leitura por QR, por aparelho
-- ---------------------------------------------------------------------------

alter table dispositivos
    add column if not exists qr_habilitado boolean not null default false;

alter table dispositivos
    add column if not exists qr_atualizado_em timestamptz;

comment on column dispositivos.qr_habilitado is
  'Se este aparelho aceita código lido por câmara. Nasce falso: a leitura por QR é liberada por Admin ou Supervisor.';
comment on column dispositivos.qr_atualizado_em is
  'Quando a liberação de QR deste aparelho foi mudada pela última vez.';

-- A auditoria passa a ter uma ação própria para a batida que veio de um QR.
-- Sem um índice parcial, responder "quais batidas de hoje vieram por câmara"
-- varreria a auditoria inteira -- que é a tabela que mais cresce no sistema.
create index if not exists ix_auditoria_ponto_qr
    on auditoria (criado_em desc)
    where acao='PONTO_VIA_QR';

commit;
