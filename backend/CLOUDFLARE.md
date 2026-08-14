# Deploy do Ponto Café no Cloudflare Workers

Este backend foi preparado para rodar em Cloudflare Workers usando PostgreSQL Neon. A opção recomendada é conectar o Worker ao Neon por **Hyperdrive**.

## 1. Pré-requisitos

- Conta Cloudflare com Workers habilitado.
- Projeto Neon `PontoCafe` já criado.
- Node.js 20+.
- Dependências instaladas em `backend/`.

```bash
npm install
npx wrangler whoami
```

## 2. Secrets

Nunca grave os valores reais no GitHub nem em `wrangler.jsonc`.

Cadastre como secrets:

```bash
npx wrangler secret put BETTER_AUTH_SECRET
npx wrangler secret put CODE_PEPPER
npx wrangler secret put BIOMETRIC_MASTER_KEY
npx wrangler secret put FIRST_ADMIN_SETUP_KEY
```

Para uma implantação inicial sem Hyperdrive, também é possível cadastrar temporariamente:

```bash
npx wrangler secret put DATABASE_URL
```

Depois que o endereço definitivo do Worker existir, cadastre:

```bash
npx wrangler secret put BETTER_AUTH_URL
```

O valor deve ser a origem HTTPS final da API, sem caminhos adicionais.

## 3. Hyperdrive recomendado

Crie uma configuração Hyperdrive apontando para a connection string do Neon e adicione o binding ao `wrangler.jsonc`:

```jsonc
"hyperdrive": [
  {
    "binding": "HYPERDRIVE",
    "id": "<ID_DA_CONFIGURACAO_HYPERDRIVE>"
  }
]
```

O entrypoint `src/cloudflare.ts` detecta o binding e utiliza `HYPERDRIVE.connectionString` automaticamente. O Worker mantém apenas uma conexão concorrente por isolate para evitar pressionar o PostgreSQL.

Após Hyperdrive estar validado, `DATABASE_URL` deixa de ser necessária no Worker.

## 4. Validar antes de publicar

```bash
npm run cf:types
npm run typecheck
npm run deploy:dry
```

Nenhum erro deve ser ignorado antes do deploy de produção.

## 5. Publicar

```bash
npm run deploy
```

Teste imediatamente:

```text
GET /
GET /health
GET /setup/status
```

`/health` deve retornar `status: ok` e `banco: ok`.

## 6. Configurar Android

Depois que a URL final estiver confirmada, compile o Android definindo:

```text
PONTOCAFE_API_URL=https://<worker-final>/
```

A URL precisa terminar em `/`, conforme exigido pelo Retrofit.

## 7. Checklist antes da APK

- Worker responde `/health`.
- Neon responde através de Hyperdrive.
- Better Auth cria sessão de Administrador e Supervisor.
- Dispositivo recebe token próprio.
- Cadastro facial funciona.
- Catálogo facial sincroniza no Android.
- Identificação 1:N acontece localmente.
- Confirmação 1:1 do servidor retorna token facial de uso único.
- Saída e retorno respeitam o horário e o limite de 15 minutos.
- Supervisor permanece somente leitura.
- APK aponta para a URL HTTPS definitiva.

## Segurança

- Não versionar `.dev.vars`, connection strings ou secrets.
- Não expor `DATABASE_URL` no APK.
- Não armazenar fotos faciais no servidor por padrão.
- O catálogo facial local é cifrado com chave mantida no Android Keystore.
- O servidor continua sendo a autoridade para confirmação facial, horário e registro da pausa.
