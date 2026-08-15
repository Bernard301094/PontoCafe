import { randomInt } from 'node:crypto'

const DEVICE_ACTIVATION_ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'

export function generateDeviceActivationCode(length = 10): string {
  if (!Number.isInteger(length) || length < 1 || length > 64) {
    throw new Error('Tamanho de token de dispositivo inválido.')
  }

  return Array.from(
    { length },
    () => DEVICE_ACTIVATION_ALPHABET[randomInt(DEVICE_ACTIVATION_ALPHABET.length)],
  ).join('')
}
