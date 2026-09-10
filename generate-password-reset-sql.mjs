#!/usr/bin/env node
// Generates ready-to-paste SQL to reset any PontoCafe user's password,
// without ever needing a database connection here. Uses the exact scrypt
// format from backend/src/password-crypto.ts, so the server accepts the
// result on the next sign-in. Not part of the app; not meant to be committed.
//
// Usage:
//   node generate-password-reset-sql.mjs email@exemplo.com [senha-nova-opcional]
//
// If no password is given, a strong random one is generated and printed.
// Paste the two SQL blocks it prints into your own DB console, in order:
// the SELECT first (should return exactly 1 row), then the UPDATE.

import { randomBytes, scryptSync } from 'node:crypto'

const SCRYPT_N = 16_384
const SCRYPT_R = 16
const SCRYPT_P = 1
const SCRYPT_KEY_LENGTH = 64
const SALT_LENGTH = 16
const SCRYPT_MAX_MEMORY = 128 * SCRYPT_N * SCRYPT_R * 2

function hashPassword(password) {
  const salt = randomBytes(SALT_LENGTH)
  const key = scryptSync(password.normalize('NFKC'), salt, SCRYPT_KEY_LENGTH, {
    N: SCRYPT_N,
    r: SCRYPT_R,
    p: SCRYPT_P,
    maxmem: SCRYPT_MAX_MEMORY,
  })
  return `${salt.toString('hex')}:${key.toString('hex')}`
}

function randomPassword() {
  return randomBytes(18).toString('base64').replace(/[/+=]/g, '').slice(0, 24)
}

function sqlEscape(value) {
  return value.replace(/'/g, "''")
}

const email = process.argv[2]?.trim().toLowerCase()
const newPassword = process.argv[3] || randomPassword()

if (!email) {
  console.error('Uso: node generate-password-reset-sql.mjs email@exemplo.com [nova-senha-opcional]')
  process.exit(1)
}
if (newPassword.length < 10) {
  console.error('A senha deve ter pelo menos 10 caracteres (mínimo exigido pelo backend).')
  process.exit(1)
}

const hash = hashPassword(newPassword)
const emailSql = sqlEscape(email)
const hashSql = sqlEscape(hash)

console.log(`Nova senha: ${newPassword}`)
console.log('')
console.log('-- Passo 1: confirme que existe exatamente 1 linha antes de continuar')
console.log(`select a.id, a."userId", a."providerId", u.email, u.role
from account a
join "user" u on u.id = a."userId"
where u.email = '${emailSql}' and a."providerId" = 'credential';`)
console.log('')
console.log('-- Passo 2: só rode isto se o passo 1 retornou exatamente 1 linha')
console.log(`update account
set password = '${hashSql}',
    "updatedAt" = now()
where "providerId" = 'credential'
  and "userId" = (select id from "user" where email = '${emailSql}');`)
