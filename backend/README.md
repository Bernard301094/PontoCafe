# Ponto Café API

## Configuração

Defina `DATABASE_URL` no ambiente de execução com a string de conexão PostgreSQL/Neon. A rota de saúde só responde como online quando a variável está configurada e o banco responde a `select 1`.

```bash
DATABASE_URL=postgresql://...
npm install
npm run dev
```

## Endpoints

### `GET /health`

Retorna `200` somente quando a API e o banco de dados estão disponíveis. Em falhas de configuração ou conexão, retorna `503` e um objeto de erro com `code` e `requestId`.

### `POST /devices/register`

Headers obrigatórios:

```text
Content-Type: application/json
Idempotency-Key: <valor-aleatorio-com-16-a-128-caracteres>
```

Body:

```json
{
  "deviceId": "PC-AB8B5AC00DBC",
  "name": "Galaxy A55",
  "pin": "2279"
}
```

Resposta `201`:

```json
{
  "deviceId": "PC-AB8B5AC00DBC",
  "activationToken": "token-entregue-uma-unica-vez",
  "expiresAt": "2026-08-16T00:00:00.000Z",
  "requestId": "uuid"
}
```

O token é aleatório, expira em 15 minutos e somente seu hash SHA-256 é persistido. Repetir exatamente a mesma tentativa com a mesma `Idempotency-Key` devolve a resposta já criada, impedindo múltiplos tokens causados por retentativas de rede.

## Erros

As respostas de erro seguem este formato:

```json
{
  "error": {
    "code": "DEVICE_REGISTER_FAILED",
    "message": "Could not create the device. Retry in a few seconds.",
    "requestId": "uuid"
  }
}
```

Códigos relevantes: `DATABASE_NOT_CONFIGURED`, `DATABASE_UNAVAILABLE`, `INVALID_IDEMPOTENCY_KEY`, `INVALID_DEVICE_REGISTRATION`, `DEVICE_REGISTER_FAILED` e `ROUTE_NOT_FOUND`.

## Deploy Vercel

Configure o diretório raiz do projeto Vercel para `backend`. O adaptador `api/index.ts` exporta a instância Hono para execução serverless; localmente `src/index.ts` inicia o servidor Node na porta definida por `PORT`.
