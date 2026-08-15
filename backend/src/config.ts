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

export const config = {
  databaseUrl: required('DATABASE_URL'),
  appTimezone: process.env.APP_TIMEZONE?.trim() || 'America/Fortaleza',
  codePepper: required('CODE_PEPPER'),
  biometricMasterKey: required('BIOMETRIC_MASTER_KEY'),
  firstAdminSetupKey: process.env.FIRST_ADMIN_SETUP_KEY?.trim() || null,
  sessionTtlHours: numberEnv('SESSION_TTL_HOURS', 168, 1, 168),
  faceThreshold: numberEnv('FACE_MATCH_THRESHOLD', 0.72, 0.5, 0.99),
  faceIdentificationMargin: numberEnv('FACE_IDENTIFICATION_MARGIN', 0.06, 0.01, 0.3),
  faceEnrollmentDuplicateThreshold: numberEnv('FACE_ENROLLMENT_DUPLICATE_THRESHOLD', 0.78, 0.7, 0.999),
  authorizationTtlSeconds: numberEnv('AUTHORIZATION_TTL_SECONDS', 180, 30, 900),
  verificationTtlSeconds: numberEnv('FACE_VERIFICATION_TTL_SECONDS', 180, 30, 900),
  offlineMaxEventAgeHours: numberEnv('OFFLINE_MAX_EVENT_AGE_HOURS', 24, 1, 72),
  biometricRetentionDays: numberEnv('BIOMETRIC_RETENTION_DAYS', 90, 1, 3650),
  latestAndroidVersion: versionEnv('APP_LATEST_ANDROID_VERSION', '0.7.0'),
  minimumAndroidVersion: versionEnv('APP_MIN_ANDROID_VERSION', '0.4.0'),
}

export function biometricKey(): Buffer {
  const key = Buffer.from(config.biometricMasterKey, 'base64')
  if (key.length !== 32) throw new Error('BIOMETRIC_MASTER_KEY deve ser Base64 de exatamente 32 bytes')
  return key
}
