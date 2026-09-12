-- Liga uma conta de acesso (Administrador ou Supervisor) ao colaborador que
-- essa pessoa é.
--
-- Até aqui eram dois mundos sem ponte: `user` é quem administra, `colaboradores`
-- é quem bate ponto. Um Supervisor que também toma café não existia do lado de
-- quem bate ponto, e por isso não tinha código nem QR próprio -- o QR que o
-- totem lê é `PONTOCAFE1|<uuid do colaborador>|<código>`, e ele não tinha uuid
-- de colaborador nenhum.
--
-- A coluna é aditiva e opcional. Uma conta sem vínculo continua a funcionar
-- exactamente como antes: administra, e não bate ponto. Só quem for vinculado
-- passa a contar como colaborador -- e aí conta mesmo, nos relatórios e nos
-- limites de pausa, como qualquer outra pessoa.
--
-- Aplicar esta migração com o Worker antigo é seguro: a coluna fica nula e o
-- código anterior não a lê. O caminho inverso não: um Worker que já oferece o
-- código próprio sobre um banco sem a 015 falha ao procurar o vínculo.

alter table "user"
  add column if not exists colaborador_id uuid;

do $$
begin
  if not exists (
    select 1 from pg_constraint where conname = 'user_colaborador_id_fkey'
  ) then
    alter table "user"
      add constraint user_colaborador_id_fkey
      foreign key (colaborador_id) references colaboradores(id)
      on delete set null;
  end if;
end
$$;

-- Um colaborador não pode estar em duas contas: se estivesse, duas pessoas
-- emitiriam o código "próprio" da mesma pessoa e o diário de pausas deixaria
-- de dizer quem realmente saiu.
create unique index if not exists ux_user_colaborador_id
  on "user" (colaborador_id)
  where colaborador_id is not null;
