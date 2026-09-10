# Ponto Café — backup e disaster recovery

Este procedimento existe para que a 1.0 não dependa da hipótese de que o banco nunca falhará. O objetivo é permitir criar uma cópia verificável e demonstrar que ela pode ser restaurada em **outro banco** antes de qualquer incidente real.

## Princípios

- Nunca testar restauração diretamente sobre produção.
- Nunca versionar `DATABASE_URL`, dumps ou credenciais no Git.
- O backup só é considerado validado depois de uma restauração de teste bem-sucedida.
- Antes de uma migração/rollout relevante, criar um backup identificável por data e commit.
- Em incidente, preservar evidências e evitar alterações destrutivas até entender o estado real.

## Pré-requisitos

Ferramentas PostgreSQL compatíveis com o servidor:

```bash
pg_dump --version
pg_restore --version
psql --version
```

As conexões devem entrar por variável de ambiente ou gerenciador seguro de segredos. Não cole URLs com senha em scripts versionados.

Exemplo de sessão local:

```bash
export SOURCE_DATABASE_URL='postgresql://...'
export RESTORE_DATABASE_URL='postgresql://...'
```

`RESTORE_DATABASE_URL` precisa apontar para um banco separado e descartável.

## 1. Criar backup lógico

```bash
mkdir -p backups
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
pg_dump \
  --format=custom \
  --no-owner \
  --no-acl \
  --dbname="$SOURCE_DATABASE_URL" \
  --file="backups/pontocafe-${STAMP}.dump"

sha256sum "backups/pontocafe-${STAMP}.dump" \
  > "backups/pontocafe-${STAMP}.dump.sha256"
```

Verifique que o arquivo não está vazio e guarde dump + SHA-256 em armazenamento controlado fora do repositório.

## 2. Registrar estado da origem

Antes do restore, capture apenas contagens operacionais — sem exportar embeddings ou segredos para logs:

```bash
psql "$SOURCE_DATABASE_URL" <<'SQL'
select 'colaboradores' as tabela, count(*) from colaboradores
union all select 'pausas_cafe', count(*) from pausas_cafe
union all select 'dispositivos', count(*) from dispositivos
union all select 'regras_cafe', count(*) from regras_cafe
union all select 'autorizacoes', count(*) from autorizacoes
union all select 'auditoria', count(*) from auditoria
union all select 'operacoes_ponto_idempotentes', count(*) from operacoes_ponto_idempotentes;
SQL
```

Registre o resultado no relatório interno do teste de recuperação.

## 3. Restaurar em banco separado

Confirme manualmente que origem e destino são diferentes:

```bash
printf 'ORIGEM:  %s\n' "${SOURCE_DATABASE_URL%%\?*}"
printf 'DESTINO: %s\n' "${RESTORE_DATABASE_URL%%\?*}"
```

O banco de destino deve estar vazio. Depois:

```bash
pg_restore \
  --exit-on-error \
  --no-owner \
  --no-acl \
  --dbname="$RESTORE_DATABASE_URL" \
  "backups/pontocafe-${STAMP}.dump"
```

Não use `--clean` contra produção.

## 4. Validar estrutura crítica

```bash
psql "$RESTORE_DATABASE_URL" <<'SQL'
select to_regclass('public.colaboradores') is not null as colaboradores_ok;
select to_regclass('public.pausas_cafe') is not null as pausas_ok;
select to_regclass('public.dispositivos') is not null as dispositivos_ok;
select to_regclass('public.auditoria') is not null as auditoria_ok;
select to_regclass('public.operacoes_ponto_idempotentes') is not null as idempotencia_ok;

select 'colaboradores' as tabela, count(*) from colaboradores
union all select 'pausas_cafe', count(*) from pausas_cafe
union all select 'dispositivos', count(*) from dispositivos
union all select 'regras_cafe', count(*) from regras_cafe
union all select 'autorizacoes', count(*) from autorizacoes
union all select 'auditoria', count(*) from auditoria
union all select 'operacoes_ponto_idempotentes', count(*) from operacoes_ponto_idempotentes;
SQL
```

As contagens devem ser compatíveis com o snapshot de origem no instante do backup. Diferenças posteriores em produção são normais e não devem ser confundidas com falha do restore.

## 5. Sanidade relacional

Executar no banco restaurado:

```bash
psql "$RESTORE_DATABASE_URL" <<'SQL'
select count(*) as pausas_com_colaborador_inexistente
from pausas_cafe p
left join colaboradores c on c.id=p.colaborador_id
where c.id is null;

select count(*) as operacoes_sem_dispositivo
from operacoes_ponto_idempotentes o
left join dispositivos d on d.id=o.dispositivo_id
where d.id is null;

select count(*) as operacoes_sem_colaborador
from operacoes_ponto_idempotentes o
left join colaboradores c on c.id=o.colaborador_id
where c.id is null;
SQL
```

Os três resultados devem ser `0`.

## 6. Teste de aplicação

Usando um backend isolado conectado ao banco restaurado:

- consultar `/health`;
- autenticar uma conta de teste autorizada;
- abrir diagnóstico Admin;
- listar colaboradores/dispositivos;
- consultar histórico/relatórios;
- não executar batidas reais nem enviar notificações a produção.

## 7. Evidência de recuperação

Para cada teste, registrar:

- data/hora UTC;
- commit da aplicação;
- versão do PostgreSQL/pg_dump;
- SHA-256 do dump;
- duração de backup;
- duração de restore;
- resultado das contagens;
- resultado da sanidade relacional;
- quem executou e quem revisou.

Não registrar URLs completas, senhas ou chaves.

## Em um incidente real

1. determinar se o problema é aplicação, Worker, conectividade ou corrupção/perda de banco;
2. interromper mutações se continuar operando puder aumentar o dano;
3. preservar request IDs, logs e o estado da base;
4. identificar o último backup validado;
5. preparar banco de recuperação separado;
6. restaurar e validar antes de redirecionar o backend;
7. registrar qualquer perda temporal de dados observada entre backup e incidente;
8. somente depois promover o banco recuperado.

O tempo e a perda máxima aceitáveis devem ser definidos operacionalmente pela empresa e medidos em cada exercício. Este documento não promete RPO/RTO que ainda não tenha sido testado.
