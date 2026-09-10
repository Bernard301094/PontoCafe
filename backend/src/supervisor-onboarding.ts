import { randomBytes } from 'node:crypto'

const UPPER = 'ABCDEFGHJKLMNPQRSTUVWXYZ'
const LOWER = 'abcdefghijkmnopqrstuvwxyz'
const DIGITS = '23456789'
const SYMBOLS = '!@#$%*-_'
const ALL = `${UPPER}${LOWER}${DIGITS}${SYMBOLS}`

function secureIndex(maxExclusive: number): number {
  if (!Number.isInteger(maxExclusive) || maxExclusive <= 0 || maxExclusive > 256) {
    throw new Error('Intervalo inválido para geração segura de senha temporária.')
  }

  const limit = Math.floor(256 / maxExclusive) * maxExclusive
  while (true) {
    const byte = randomBytes(1)[0]
    if (byte < limit) return byte % maxExclusive
  }
}

function pick(alphabet: string): string {
  return alphabet[secureIndex(alphabet.length)]
}

function shuffle(chars: string[]): string {
  for (let i = chars.length - 1; i > 0; i -= 1) {
    const j = secureIndex(i + 1)
    ;[chars[i], chars[j]] = [chars[j], chars[i]]
  }
  return chars.join('')
}

/**
 * Gera uma senha temporária de alta entropia que já satisfaz a política mínima
 * do Ponto Café. O valor só deve ser devolvido uma vez ao Administrador e nunca
 * pode ser persistido em texto puro ou enviado para logs/auditoria.
 */
export function generateTemporaryPassword(length = 16): string {
  if (!Number.isInteger(length) || length < 12 || length > 64) {
    throw new Error('Comprimento inválido para senha temporária.')
  }

  const chars = [pick(UPPER), pick(LOWER), pick(DIGITS), pick(SYMBOLS)]
  while (chars.length < length) chars.push(pick(ALL))
  return shuffle(chars)
}

export function isStrongPassword(password: string): boolean {
  return password.length >= 10 &&
    password.length <= 128 &&
    /[A-Za-z]/.test(password) &&
    /\d/.test(password)
}

export function normalizeSupervisorShift(value: string): 'A' | 'B' | 'C' | 'D' | null {
  const normalized = value.trim().toUpperCase()
  return normalized === 'A' || normalized === 'B' || normalized === 'C' || normalized === 'D'
    ? normalized
    : null
}
