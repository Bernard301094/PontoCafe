import { randomBytes, scryptSync, timingSafeEqual } from 'node:crypto'

/**
 * Better Auth's default password format is:
 *
 *   <16-byte salt as hex>:<64-byte scrypt key as hex>
 *
 * Keep these parameters byte-for-byte compatible with Better Auth so every
 * credential already stored by PontoCafe remains valid. We configure this
 * implementation explicitly because Wrangler/workerd can resolve
 * @better-auth/utils/password to its pure-JS fallback even with nodejs_compat,
 * making password hashing/verification vulnerable to Worker CPU limits.
 */
const SCRYPT_N = 16_384
const SCRYPT_R = 16
const SCRYPT_P = 1
const SCRYPT_KEY_LENGTH = 64
const SALT_LENGTH = 16
const SCRYPT_MAX_MEMORY = 128 * SCRYPT_N * SCRYPT_R * 2

const HEX_PATTERN = /^[0-9a-f]+$/i

export type PasswordVerificationInput = {
  hash: string
  password: string
}

function parsePasswordHash(hash: string): { salt: Buffer; key: Buffer } | null {
  const parts = hash.split(':')
  if (parts.length !== 2) return null

  const [saltHex, keyHex] = parts
  if (
    saltHex.length !== SALT_LENGTH * 2 ||
    keyHex.length !== SCRYPT_KEY_LENGTH * 2 ||
    !HEX_PATTERN.test(saltHex) ||
    !HEX_PATTERN.test(keyHex)
  ) {
    return null
  }

  return {
    salt: Buffer.from(saltHex, 'hex'),
    key: Buffer.from(keyHex, 'hex'),
  }
}

function deriveKey(password: string, salt: Buffer): Buffer {
  return scryptSync(
    password.normalize('NFKC'),
    salt,
    SCRYPT_KEY_LENGTH,
    {
      N: SCRYPT_N,
      r: SCRYPT_R,
      p: SCRYPT_P,
      maxmem: SCRYPT_MAX_MEMORY,
    },
  )
}

export function isRecognizedPasswordHash(hash: string | null | undefined): boolean {
  return typeof hash === 'string' && parsePasswordHash(hash) !== null
}

export async function hashPassword(password: string): Promise<string> {
  const salt = randomBytes(SALT_LENGTH)
  const key = deriveKey(password, salt)
  return `${salt.toString('hex')}:${key.toString('hex')}`
}

export async function verifyPassword({ hash, password }: PasswordVerificationInput): Promise<boolean> {
  const parsed = parsePasswordHash(hash)
  if (!parsed) return false

  const candidate = deriveKey(password, parsed.salt)
  return candidate.length === parsed.key.length && timingSafeEqual(candidate, parsed.key)
}
