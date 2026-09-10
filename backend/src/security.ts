import { createHash, createHmac, randomBytes, randomUUID, timingSafeEqual } from 'node:crypto'
import { config } from './config.js'
import { generateDeviceActivationCode } from './device-activation-code.js'

export const newId = () => randomUUID()
export const newToken = () => randomBytes(32).toString('base64url')
export const newDeviceToken = generateDeviceActivationCode
export const hashToken = (value: string) => createHash('sha256').update(value).digest('hex')
export const hashDeviceUnlockPin = (deviceId: string, pin: string) =>
  createHmac('sha256', config.codePepper).update(`device-unlock:${deviceId}:${pin}`).digest('hex')

export function secureHexEquals(a: string, b: string): boolean {
  if (!/^[0-9a-f]+$/i.test(a) || !/^[0-9a-f]+$/i.test(b) || a.length !== b.length) return false
  const left = Buffer.from(a, 'hex')
  const right = Buffer.from(b, 'hex')
  return left.length === right.length && timingSafeEqual(left, right)
}

/**
 * Comparação de códigos de acesso em tempo constante.
 *
 * O código já vem normalizado dos dois lados (maiúsculas, alfabeto fixo, 6
 * caracteres), então um comprimento diferente só acontece com dado corrompido —
 * e nesse caso responder de imediato não revela nada sobre o segredo.
 */
export function secureCodeEquals(a: string, b: string): boolean {
  if (a.length !== b.length || a.length === 0) return false
  const left = Buffer.from(a, 'utf8')
  const right = Buffer.from(b, 'utf8')
  return left.length === right.length && timingSafeEqual(left, right)
}
