# Deploy do Ponto Café no Cloudflare Workers

O backend está preparado para Cloudflare Workers + Neon PostgreSQL. A conexão de produção usa **Cloudflare Hyperdrive**.

## Estado preparado no projeto

- Worker: `pontocafe`
- Entrypoint: `src/cloudflare.ts`
- Runtime: Node.js compatibility
- PostgreSQL: `pg` via Hyperdrive
- Banco: Neon / `neondb`
- Role dedicada no Neon: `ponto_cafe_api`
- API em desenvolvimento: `0.7.0`
- Android anunciado: `0.7.0`
- `npm run validate`: testes + TypeScript
- `npm run deploy`: valida antes de publicar
- `npm run preview`: valida antes de enviar preview
- `npm run deploy:dry`: valida antes do dry-run
- Request IDs `PC-*` e `Server-Timing` para rastreabilidade
- Cron diário para retenção biométrica

## Workers Builds + GitHub

No painel Cloudflare, conecte o repositório GitHub `Bernard301094/PontoCafe` ao Worker.

Configuração:

- **Root directory:** `backend`
- **Production branch:** `main`, somente depois da validação e merge
- Branch de desenvolvimento atual: `agent/backend-api-v2-review`
- **Build command:** deixar vazio
- **Deploy command:** `npm run deploy`
- **Non-production deploy command:** `npm run preview`

Cloudflare usa a versão do Wrangler declarada em `backend/package.json`.

## Secrets de runtime

Nunca grave valores reais no GitHub ou no `wrangler.jsonc`.

O Worker usa como secrets:

- `BETTER_AUTH_SECRET`
- `CODE_PEPPER`
- `BIOMETRIC_MASTER_KEY`
- `FIRST_ADMIN_SETUP_KEY`

O Hyperdrive fornece `DATABASE_URL` em runtime através de `HYPERDRIVE.connectionString`.

`BETTER_AUTH_URL` pode ser derivado da origem HTTPS da requisição. Depois que o domínio definitivo estiver estabilizado, pode ser fixado como configuração de runtime.

## Variáveis não secretas relevantes

A configuração 0.7 inclui:

```text
APP_TIMEZONE=America/Fortaleza
SESSION_TTL_HOURS=168
FACE_MATCH_THRESHOLD=0.72
FACE_IDENTIFICATION_MARGIN=0.06
FACE_ENROLLMENT_DUPLICATE_THRESHOLD=0.88
AUTHORIZATION_TTL_SECONDS=180
FACE_VERIFICATION_TTL_SECONDS=180
OFFLINE_MAX_EVENT_AGE_HOURS=24
BIOMETRIC_RETENTION_DAYS=90
APP_LATEST_ANDROID_VERSION=0.7.0
APP_MIN_ANDROID_VERSION=0.4.0
```

A regra de duração do café não fica em variável de ambiente. Ela é armazenada em `regras_cafe`. O padrão atual confirmado é **15 minutos = 900 segundos** para manhã e tarde.

## Hyperdrive + Neon

A role `ponto_cafe_api` tem os privilégios operacionais necessários sem reutilizar o proprietário `neondb_owner`.

Ao configurar Hyperdrive, use a connection string direta do Neon. O Hyperdrive mantém seu próprio pool global e evita a necessidade de pooling duplicado.

Binding esperado:

```text
HYPERDRIVE
```

O ID real já é referenciado no `wrangler.jsonc` do projeto. Não troque o binding ou projeto Neon durante um deploy normal.

## Retenção biométrica

O `wrangler.jsonc` declara um Cron Trigger diário:

```text
15 3 * * *
```

Cron do Cloudflare usa UTC; essa configuração executa aproximadamente às 00:15 em Fortaleza enquanto o offset for UTC-3.

A manutenção:
- procura templates associados a colaboradores já desativados;
- remove apenas os que ultrapassaram `BIOMETRIC_RETENTION_DAYS`;
- registra auditoria de sistema quando remove dados;
- não guarda foto nem cria cópia do template.

Se o Admin excluir explicitamente uma biometria ou eliminar um colaborador pelo fluxo que já remove o rosto, a exclusão é imediata; os 90 dias funcionam como retenção máxima para templates que permaneçam após simples desativação.

## Observabilidade segura

Cada requisição recebe um identificador:

```text
X-Request-Id: PC-XXXXXXXXXXXX
```

O backend também pode devolver:

```text
Server-Timing: app;dur=...
```

Erros 4xx/5xx novos incluem `requestId` quando aplicável. O objetivo é localizar a requisição em Cloudflare sem logar senha, PIN, token, foto ou embedding.

## Validação obrigatória

Antes de publicar:

```bash
npm install
npm run validate
npm run deploy:dry
```

Opcionalmente gere tipos do ambiente:

```bash
npm run cf:types
```

Somente depois:

```bash
npm run deploy
```

Depois do deploy, validar sem criar dados de teste em produção:

```text
GET /
GET /health
GET /app-status
GET /setup/status
```

Esperado para a 0.7:
- `/` informa API `0.7.0`;
- `/health` retorna `status: ok`, `banco: ok`, latência e Request ID;
- `/app-status` anuncia Android `0.7.0` e retenção biométrica;
- criação de dados reais só deve ser testada com uma operação intencional do usuário.

## Funcionalidades administrativas 0.7

Com sessão Admin válida:
- `/admin/diagnostico`
- regras de café em segundos
- gestão de dispositivos
- gestão de contas
- auditoria

Em `/gestao`:
- histórico individual;
- importação CSV;
- edição em lote;
- resumo biométrico;
- calibração com score/margem;
- métricas empíricas FAR/FRR/Top-1;
- retenção biométrica.

Supervisor continua sem administrar contas/dispositivos/regras globais, mas pode acompanhar a operação, gerenciar colaboradores/biometria conforme os endpoints compartilhados e gerar autorização fora de horário.

## Segurança

- Não versionar `.dev.vars`, connection strings, senhas, keystores ou secrets.
- Não expor `DATABASE_URL` no APK.
- Não armazenar fotos faciais no servidor por padrão.
- Templates no banco continuam cifrados.
- Catálogo facial local continua cifrado com Android Keystore.
- O servidor continua sendo autoridade para confirmação facial, horário, autorização e registro da pausa.
- A role de runtime do banco não é proprietária do banco.

## Ordem de publicação

Quando backend e APK mudam juntos:

1. `npm run validate`;
2. dry-run do Worker;
3. deploy do Worker;
4. verificar `/health` e `/app-status`;
5. executar testes Android;
6. compilar/assinar APK;
7. instalar no Galaxy A55;
8. executar `docs/RELEASE_CHECKLIST.md`;
9. só então fazer merge em `main` e criar tag.
