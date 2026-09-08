# Arquitetura portátil do Ponto Café

## Princípio

Neon é o provedor PostgreSQL atual, mas não faz parte do contrato da aplicação.

A aplicação Android nunca acessa PostgreSQL diretamente. O fluxo é:

```text
Android (Kotlin/Compose)
        |
        | HTTPS + token de sessão
        v
API Ponto Café
        |
        | DATABASE_URL
        v
PostgreSQL
```

A API concentra autenticação, autorização, regras de horário, geração/validação de códigos temporários, auditoria e acesso aos templates faciais.

## O que fica independente de provedor

- Tabelas e índices usam PostgreSQL padrão.
- Nenhuma tabela depende de Neon Auth ou Supabase Auth.
- O backend recebe a conexão pela variável `DATABASE_URL`.
- UUIDs são gerados pela aplicação/backend.
- Senhas nunca são armazenadas em texto puro; apenas hashes produzidos no backend.
- Códigos temporários de supervisor são persistidos apenas como hash.
- Templates faciais são persistidos como dados cifrados, não como fotos.
- PDFs são gerados sob demanda e não são salvos no banco por padrão.

## Armazenamento biométrico

O reconhecimento facial foi removido. A liberação da pausa é um código de 6 caracteres emitido por Admin/Supervisor, preso a um colaborador, válido para uma saída e um retorno. Ver `docs/PRIVACIDADE_DADOS.md`.

Fluxo:

```text
Supervisor emite código -> pessoa digita no quiosque -> servidor decide saída/retorno -> PostgreSQL
```

O banco armazena somente o blob cifrado, a identificação do modelo e sua versão.

## Migração futura

Para trocar Neon por outro PostgreSQL:

1. Criar uma instância PostgreSQL no novo provedor.
2. Exportar o banco atual com ferramentas PostgreSQL (`pg_dump`).
3. Restaurar no novo banco (`pg_restore` ou `psql`).
4. Alterar apenas `DATABASE_URL` no backend.
5. Executar testes de integridade e autenticação.
6. Trocar o ambiente de produção para a nova conexão.

A aplicação Android não precisa conhecer o endereço do banco e, portanto, não precisa ser recompilada apenas porque o PostgreSQL mudou de provedor, desde que a URL pública da API permaneça a mesma.

## Política de retenção

Para preservar espaço no plano gratuito:

- não armazenar fotos ou vídeos de validações;
- não armazenar PDFs no PostgreSQL;
- manter somente embeddings cifrados;
- manter pausas e auditoria como registros estruturados;
- definir posteriormente uma política de retenção para logs de baixo valor operacional.
