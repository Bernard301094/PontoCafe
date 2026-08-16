import { createCipheriv, createDecipheriv, createHmac, randomBytes } from 'node:crypto'

export type DeviceRegistrationContext = {
  idempotencyKey: string
  actorId: string
  fingerprint: string
  deviceId: string
}

const IDEMPOTENCY_KEY_PATTERN = /^[A-Za-z0-9._:-]{16,128}$/
const DEVICE_TOKEN_PATTERN = /^[A-Za-z0-9]{10}$/
const KEY_DERIVATION_CONTEXT = 'pontocafe:device-registration-idempotency:v1'

export function isValidDeviceRegistrationIdempotencyKey(value: string | null | undefined): value is string {
  return typeof value === 'string' && IDEMPOTENCY_KEY_PATTERN.test(value.trim())
}

export function normalizeDeviceRegistrationName(value: string): string {
  return value.trim().normalize('NFKC')
}

export function deviceRegistrationFingerprint(
  actorId: string,
  deviceName: string,
  pin: string | null,
  codePepper: string,
): string {
  const normalizedName = normalizeDeviceRegistrationName(deviceName)
  const payload = JSON.stringify({
    v: 1,
    actorId,
    deviceName: normalizedName,
    pin: pin ?? '',
  })
  return createHmac('sha256', codePepper)
    .update(`device-registration-request:${payload}`)
    .digest('hex')
}

function deviceRegistrationEncryptionKey(masterKey: Buffer): Buffer {
  if (masterKey.length !== 32) throw new Error('A chave mestre de idempotência deve ter 32 bytes.')
  return createHmac('sha256', masterKey)
    .update(KEY_DERIVATION_CONTEXT)
    .digest()
}

function additionalAuthenticatedData(context: DeviceRegistrationContext): Buffer {
  return Buffer.from(JSON.stringify({
    v: 1,
    key: context.idempotencyKey,
    actor: context.actorId,
    fingerprint: context.fingerprint,
    device: context.deviceId,
  }), 'utf8')
}

export function encryptDeviceRegistrationToken(
  token: string,
  masterKey: Buffer,
  context: DeviceRegistrationContext,
) {
  if (!DEVICE_TOKEN_PATTERN.test(token)) throw new Error('Token de ativação inválido para replay idempotente.')
  const iv = randomBytes(12)
  const cipher = createCipheriv('aes-256-gcm', deviceRegistrationEncryptionKey(masterKey), iv)
  cipher.setAAD(additionalAuthenticatedData(context))
  const ciphertext = Buffer.concat([cipher.update(token, 'utf8'), cipher.final()])
  return { ciphertext, iv, authTag: cipher.getAuthTag() }
}

export function decryptDeviceRegistrationToken(
  ciphertext: Buffer,
  iv: Buffer,
  authTag: Buffer,
  masterKey: Buffer,
  context: DeviceRegistrationContext,
): string {
  const decipher = createDecipheriv('aes-256-gcm', deviceRegistrationEncryptionKey(masterKey), iv)
  decipher.setAAD(additionalAuthenticatedData(context))
  decipher.setAuthTag(authTag)
  const token = Buffer.concat([decipher.update(ciphertext), decipher.final()]).toString('utf8')
  if (!DEVICE_TOKEN_PATTERN.test(token)) throw new Error('Replay idempotente contém token inválido.')
  return token
}
