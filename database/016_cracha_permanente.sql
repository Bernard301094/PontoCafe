-- Crachá permanente de quem tem conta: Supervisor e Administrador.
--
-- A cadeia do código passa a ser fixa por conta, e não sorteada a cada emissão.
-- Como o QR é `PONTOCAFE1|<uuid do colaborador>|<código>`, fixar o código fixa
-- a imagem: o mesmo QR serve sempre, e pode ser guardado no telemóvel.
--
-- ISTO É UMA TROCA DELIBERADA, e convém que fique escrita.
--
-- O código de café expira de propósito. Está no config.ts: "o código deve ser
-- emitido com a pessoa já diante do quiosque, e não guardado num papel para uso
-- mais tarde. Um passe que sobrevive meia hora vira um passe transferível."
-- Um crachá permanente é exactamente esse passe transferível -- quem o
-- fotografar pode registar a pausa daquela pessoa noutro dia.
--
-- O que limita o estrago:
--   * existe só para contas, que são poucas e têm nome na auditoria. Nenhum
--     colaborador tem crachá, e o botão de QR foi retirado da lista deles;
--   * continua a ser um código normal em tudo o resto. Vive dentro da janela do
--     período, é cancelado e reemitido como qualquer outro, e a regra de uma
--     pausa por período continua a valer -- não dá para sair duas vezes na
--     mesma manhã só por ter o QR à mão;
--   * quem tem conta já podia emitir um código a qualquer pessoa. O crachá não
--     lhe dá um poder que não tivesse.
--
-- Aditiva: uma conta sem crachá comporta-se como antes, com código sorteado.

alter table "user"
  add column if not exists cracha_codigo text;

-- Dois crachás iguais fariam a mesma cadeia valer para duas pessoas, e a
-- comparação em ponto-registration é por (colaborador, código) -- passaria a
-- haver dúvida sobre quem realmente saiu.
create unique index if not exists ux_user_cracha_codigo
  on "user" (cracha_codigo)
  where cracha_codigo is not null;
