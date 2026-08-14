# Deploy do Ponto Café no Cloudflare Workers

O backend está preparado para Cloudflare Workers + Neon PostgreSQL. A conexão recomendada com o banco é via **Hyperdrive**.

## Estado preparado no projeto

- Worker: `ponto-cafe-api`
- Entrypoint: `src/cloudflare.ts`
- Runtime: Node.js compatibility
- PostgreSQL: `pg` compatível com Hyperdrive
- Banco: Neon / `neondb`
- Role dedicada no Neon: `ponto_cafe_api`
- `npm run deploy` executa o `typecheck` antes de publicar
- `npm run preview` executa o `typecheck` antes de enviar uma versão de preview
- `wrangler.jsonc` declara os secrets obrigatórios

## Opção recomendada: Workers Builds + GitHub

No painel Cloudflare, conecte o repositório GitHub `Bernard301094/PontoCafe` ao Worker.

Configuração do build:

- **Root directory:** `backend`
- **Production branch:** `main` depois que o PR estiver validado e mesclado
- Para testar a branch atual antes do merge: `agent/backend-api-v2-review`
- **Build command:** deixar vazio
- **Deploy command:** `npm run deploy`
- **Non-production deploy command:** `npm run preview`

Cloudflare usa a versão do Wrangler declarada em `backend/package.json`.

## Secrets de runtime

Nunca grave valores reais no GitHub nem em `wrangler.jsonc`.

O Worker exige:

- `BETTER_AUTH_SECRET`
- `CODE_PEPPER`
- `BIOMETRIC_MASTER_KEY`
- `FIRST_ADMIN_SETUP_KEY`

Eles devem ser cadastrados em **Settings > Variables & Secrets** como secrets de runtime.

`BETTER_AUTH_URL` não precisa ser fixado antes do primeiro deploy: o entrypoint usa a origem HTTPS da própria requisição quando a variável não estiver definida. Depois que o domínio definitivo estiver estabilizado, ela pode ser fixada explicitamente.

## Hyperdrive + Neon

Foi criada no Neon uma role dedicada chamada `ponto_cafe_api`, com acesso operacional às tabelas e sequências do schema `public`, sem reutilizar o usuário proprietário `neondb_owner`.

Ao criar Hyperdrive, use a **connection string direta do Neon**, sem `-pooler` no hostname. Neon entrega a connection string pooled por padrão, mas o Hyperdrive já mantém o próprio pool global; usar a conexão direta evita pooling duplicado.

Crie no Cloudflare uma configuração Hyperdrive usando a connection string direta dessa role e adicione o binding ao Worker com o nome exato:

```text
HYPERDRIVE
```

Depois de criar a configuração, adicione ao `wrangler.jsonc` o ID real retornado pelo Cloudflare:

```jsonc
"hyperdrive": [
  {
    "binding": "HYPERDRIVE",
    "id": "<ID_REAL_DO_HYPERDRIVE>"
  }
]
```

O entrypoint `src/cloudflare.ts` usa `HYPERDRIVE.connectionString` automaticamente e injeta a conexão antes de carregar Hono/Better Auth.

### Fallback sem Hyperdrive

É possível usar temporariamente `DATABASE_URL` como secret do Worker, mas não é a arquitetura recomendada para produção em Workers.

## Validação obrigatória

Antes de considerar o backend pronto:

```bash
npm install
npm run cf:types
npm run typecheck
npm run deploy:dry
```

No Workers Builds, `npm run deploy` já bloqueia a publicação se o TypeScript falhar.

Depois do deploy, validar:

```text
GET /
GET /health
GET /setup/status
```

`/health` deve retornar `status: ok` e `banco: ok`.

## Configurar o Android

Somente depois de confirmar a URL HTTPS real do Worker, compile o Android com:

```text
PONTOCAFE_API_URL=https://<worker-final>/
```

A URL deve terminar em `/`, pois Retrofit exige uma base URL com barra final.

## Checklist antes da APK

- Worker responde `/health`.
- Neon responde através de Hyperdrive.
- Better Auth cria sessão de Administrador e Supervisor.
- Primeiro Administrador criado apenas pelo fluxo de instalação.
- Dispositivo recebe token próprio.
- Cadastro facial funciona.
- Catálogo facial é sincronizado e cifrado no Android.
- Identificação 1:N acontece localmente.
- Confirmação 1:1 do servidor gera token facial curto e de uso único.
- Saída e retorno respeitam horário e limite configurados.
- Supervisor permanece somente leitura.
- APK aponta para a URL HTTPS definitiva.

## Segurança

- Não versionar `.dev.vars`, connection strings, senhas ou secrets.
- Não expor `DATABASE_URL` no APK.
- Não armazenar fotos faciais no servidor por padrão.
- O catálogo facial local é cifrado com chave mantida no Android Keystore.
- O servidor continua sendo a autoridade para confirmação facial, horário e registro da pausa.
- A role de runtime do banco não é a proprietária do banco.
