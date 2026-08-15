import { createCipheriv, createDecipheriv, createHash, createHmac, randomBytes, randomInt, randomUUID, timingSafeEqual } from 'node:crypto'
import { config, biometricKey } from './config.js'

const DEVICE_TOKEN_ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'

export const newId = () => randomUUID()
export const newToken = () => randomBytes(32).toString('base64url')
export const newDeviceToken = (length = 10) => {
  if (!Number.isInteger(length) || length < 1 || length > 64) {
    throw new Error('Tamanho de token de dispositivo inválido.')
  }
  return Array.from(
    { length },
    () => DEVICE_TOKEN_ALPHABET[randomInt(DEVICE_TOKEN_ALPHABET.length)],
  ).join('')
}
export const hashToken = (value: string) => createHash('sha256').update(value).digest('hex')
export const hashAuthorizationCode = (code: string) => createHmac('sha256', config.codePepper).update(code).digest('hex')
export const hashDeviceUnlockPin = (deviceId: string, pin: string) =>
  createHmac('sha256', config.codePepper).update(`device-unlock:${deviceId}:${pin}`).digest('hex')
export const generateAuthorizationCode = () => randomInt(100000, 1000000).toString()

export function secureHexEquals(a: string, b: string): boolean {
  if (!/^[0-9a-f]+$/i.test(a) || !/^[0-9a-f]+$/i.test(b) || a.length !== b.length) return false
  const left = Buffer.from(a, 'hex')
  const right = Buffer.from(b, 'hex')
  return left.length === right.length && timingSafeEqual(left, right)
}

export function encryptEmbedding(embedding: number[]) {
  const iv = randomBytes(12)
  const cipher = createCipheriv('aes-256-gcm', biometricKey(), iv)
  const plaintext = Buffer.from(JSON.stringify(embedding), 'utf8')
  const ciphertext = Buffer.concat([cipher.update(plaintext), cipher.final()])
  return { ciphertext, iv, authTag: cipher.getAuthTag() }
}

export function decryptEmbedding(ciphertext: Buffer, iv: Buffer, authTag: Buffer): number[] {
  const decipher = createDecipheriv('aes-256-gcm', biometricKey(), iv)
  decipher.setAuthTag(authTag)
  const plaintext = Buffer.concat([decipher.update(ciphertext), decipher.final()]).toString('utf8')
  const parsed = JSON.parse(plaintext)
  if (!Array.isArray(parsed) || parsed.some((value) => typeof value !== 'number' || !Number.isFinite(value))) throw new Error('Template facial inválido')
  return parsed
}

export function cosineSimilarity(a: number[], b: number[]): number {
  if (a.length !== b.length || a.length === 0) return -1
  let dot = 0, normA = 0, normB = 0
  for (let i = 0; i < a.length; i++) {
    dot += a[i] * b[i]
    normA += a[i] * a[i]
    normB += b[i] * b[i]
  }
  if (normA === 0 || normB === 0) return -1
  return dot / (Math.sqrt(normA) * Math.sqrt(normB))
}
