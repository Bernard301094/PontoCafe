/**
 * Valores de ambiente inertes para testes que importam módulos do Worker.
 *
 * `src/config.ts` lança na importação quando falta DATABASE_URL ou CODE_PEPPER —
 * de propósito, para um deploy sem configuração falhar cedo em vez de responder
 * 500 em produção. O efeito colateral é que qualquer teste que importe algo da
 * cadeia `routes/* -> db.ts -> config.ts` morre no import, mesmo quando o código
 * sob teste nunca toca no banco.
 *
 * Importar este módulo ANTES do código sob teste resolve isso sem afrouxar a
 * validação real: os valores são visivelmente falsos e nenhuma conexão é aberta.
 *
 * ```ts
 * import './helpers/test-env.js'
 * import { deviceAuthContractMiddleware } from '../src/routes/shared.js'
 * ```
 *
 * A ordem importa: ESM avalia os imports na ordem em que são declarados.
 */
const INERT_ENV: Record<string, string> = {
  DATABASE_URL: 'postgresql://test:test@127.0.0.1:5432/pontocafe_test?sslmode=disable',
  CODE_PEPPER: 'pepper-inerte-somente-para-testes',
  // Base64 de 32 bytes: encryptionKey() valida o tamanho ao ser chamada.
  APP_ENCRYPTION_KEY: Buffer.alloc(32, 7).toString('base64'),
  BETTER_AUTH_SECRET: 'segredo-inerte-somente-para-testes',
}

for (const [name, value] of Object.entries(INERT_ENV)) {
  // Nunca sobrescreve o que já estiver definido: rodar a suíte contra um
  // ambiente real continua possível.
  process.env[name] ??= value
}
