function required(name: string): string {
  const value = process.env[name]?.trim()
  if (!value) throw new Error(`Variável obrigatória ausente: ${name}`)
  return value
}

function numberEnv(name: string, fallback: number, min: number, max: number): number {
  const raw = process.env[name]?.trim()
  if (!raw) return fallback
  const value = Number(raw)
  if (!Number.isFinite(value) || value < min || value > max) {
    throw new Error(`Variável inválida: ${name}. Use um valor entre ${min} e ${max}.`)
  }
  return value
}

function versionEnv(name: string, fallback: string): string {
  const value = process.env[name]?.trim() || fallback
  if (!/^\d+\.\d+\.\d+$/.test(value)) {
    throw new Error(`Variável inválida: ${name}. Use uma versão numérica no formato 1.2.3.`)
  }
  return value
}

function revisionEnv(name: string, fallback: string): string {
  const value = process.env[name]?.trim() || fallback
  if (!/^[A-Za-z0-9._-]{1,64}$/.test(value)) {
    throw new Error(`Variável inválida: ${name}.`)
  }
  return value
}

/**
 * A chave já não cifra biometria — essa deixou de existir. Continua a cifrar o
 * token de registo de dispositivo guardado no diário de idempotência, por isso a
 * variável antiga permanece aceite: nenhum ambiente já implantado precisa de ser
 * reconfigurado para esta versão subir.
 */
function encryptionKeyEnv(): string {
  const value = process.env.APP_ENCRYPTION_KEY?.trim() || process.env.BIOMETRIC_MASTER_KEY?.trim()
  if (!value) throw new Error('Variável obrigatória ausente: APP_ENCRYPTION_KEY')
  return value
}

export const config = {
  databaseUrl: required('DATABASE_URL'),
  appTimezone: process.env.APP_TIMEZONE?.trim() || 'America/Fortaleza',
  codePepper: required('CODE_PEPPER'),
  appEncryptionKey: encryptionKeyEnv(),
  firstAdminSetupKey: process.env.FIRST_ADMIN_SETUP_KEY?.trim() || null,
  sessionTtlHours: numberEnv('SESSION_TTL_HOURS', 168, 1, 168),
  /** Janela para a pessoa levar o código ao quiosque e registar a SAÍDA. */
  accessCodeTtlSeconds: numberEnv('ACCESS_CODE_TTL_SECONDS', 900, 60, 7200),
  /** Tentativas erradas de código, por colaborador, antes de bloquear. */
  accessCodeMaxAttempts: numberEnv('ACCESS_CODE_MAX_ATTEMPTS', 8, 3, 50),
  accessCodeAttemptWindowSeconds: numberEnv('ACCESS_CODE_ATTEMPT_WINDOW_SECONDS', 300, 60, 3600),
  /** Tolerância antes de o limite da pausa começar a correr. */
  coffeeGraceSeconds: numberEnv('COFFEE_GRACE_SECONDS', 60, 0, 3600),
  offlineMaxEventAgeHours: numberEnv('OFFLINE_MAX_EVENT_AGE_HOURS', 24, 1, 72),
  accessCodeRetentionDays: numberEnv('ACCESS_CODE_RETENTION_DAYS', 90, 1, 3650),
  pontoOperationRetentionDays: numberEnv('PONTO_OPERATION_RETENTION_DAYS', 30, 1, 3650),
  deviceHealthRetentionDays: numberEnv('DEVICE_HEALTH_RETENTION_DAYS', 30, 1, 3650),
  deviceRegistrationIdempotencyTtlSeconds: numberEnv('DEVICE_REGISTRATION_IDEMPOTENCY_TTL_SECONDS', 600, 60, 900),
  latestAndroidVersion: versionEnv('APP_LATEST_ANDROID_VERSION', '1.1.0'),
  minimumAndroidVersion: versionEnv('APP_MIN_ANDROID_VERSION', '1.1.0'),
  backendRevision: revisionEnv('BACKEND_REVISION', 'dev'),
}

export function encryptionKey(): Buffer {
  const key = Buffer.from(config.appEncryptionKey, 'base64')
  if (key.length !== 32) throw new Error('APP_ENCRYPTION_KEY deve ser Base64 de exatamente 32 bytes')
  return key
}
