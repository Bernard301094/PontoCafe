function required(name: string): string {
  const value = process.env[name]?.trim()
  if (!value) throw new Error(`Variável obrigatória ausente: ${name}`)
  return value
}

function numberEnv(name: string, fallback: number): number {
  const raw = process.env[name]
  if (!raw) return fallback
  const value = Number(raw)
  if (!Number.isFinite(value)) throw new Error(`Variável inválida: ${name}`)
  return value
}

export const config = {
  databaseUrl: required('DATABASE_URL'),
  appTimezone: process.env.APP_TIMEZONE?.trim() || 'America/Fortaleza',
  codePepper: required('CODE_PEPPER'),
  biometricMasterKey: required('BIOMETRIC_MASTER_KEY'),
  firstAdminSetupKey: process.env.FIRST_ADMIN_SETUP_KEY?.trim() || null,
  sessionTtlHours: numberEnv('SESSION_TTL_HOURS', 12),
  faceThreshold: numberEnv('FACE_MATCH_THRESHOLD', 0.72),
  faceIdentificationMargin: numberEnv('FACE_IDENTIFICATION_MARGIN', 0.06),
  faceEnrollmentDuplicateThreshold: numberEnv('FACE_ENROLLMENT_DUPLICATE_THRESHOLD', 0.88),
  authorizationTtlSeconds: numberEnv('AUTHORIZATION_TTL_SECONDS', 180),
  verificationTtlSeconds: numberEnv('FACE_VERIFICATION_TTL_SECONDS', 180),
}

export function biometricKey(): Buffer {
  const key = Buffer.from(config.biometricMasterKey, 'base64')
  if (key.length !== 32) throw new Error('BIOMETRIC_MASTER_KEY deve ser Base64 de exatamente 32 bytes')
  return key
}
