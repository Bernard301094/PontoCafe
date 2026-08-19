-- Ponto Café - metadado mínimo para avatar do colaborador.
--
-- A imagem NÃO é armazenada no PostgreSQL. Ela fica no Cloudflare R2 em WebP
-- otimizado; o banco guarda apenas um inteiro de versão (4 bytes úteis por linha)
-- para indicar existência e invalidar cache quando a imagem muda.

begin;

alter table colaboradores
    add column if not exists avatar_version integer not null default 0;

alter table colaboradores
    drop constraint if exists colaboradores_avatar_version_check;

alter table colaboradores
    add constraint colaboradores_avatar_version_check
    check (avatar_version >= 0);

commit;
