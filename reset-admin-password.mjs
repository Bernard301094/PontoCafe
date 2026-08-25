#!/usr/bin/env node
// One-off admin-password-reset utility for PontoCafe.
// Reproduces backend/src/password-crypto.ts's exact scrypt format so the
// server accepts the new password on the next sign-in. Not part of the app;
// not meant to be committed.
//
// Usage:
//   DATABASE_URL="postgresql://user:pass@host:5432/db?sslmode=require" \
//     node reset-admin-password.mjs admin@example.com [senha-nova-opcional]
//
// If no password is given, a strong random one is generated and printed.
// Safe by construction: looks up the user by email first and prints what it
// found before touching anything; only ever updates the single matching
// credential row via a parameterized query.

import { randomBytes, scryptSync } from 'node:crypto'
import pg from 'pg'

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

const email = process.argv[2]?.trim().toLowerCase()
const newPassword = process.argv[3] || randomPassword()

if (!email) {
  console.error('Uso: DATABASE_URL=... node reset-admin-password.mjs email@exemplo.com [nova-senha-opcional]')
  process.exit(1)
}
if (newPassword.length < 10) {
  console.error('A senha deve ter pelo menos 10 caracteres (mínimo exigido pelo backend).')
  process.exit(1)
}
if (!process.env.DATABASE_URL) {
  console.error('Defina DATABASE_URL antes de rodar este script.')
  process.exit(1)
}

const client = new pg.Client({ connectionString: process.env.DATABASE_URL })
await client.connect()

try {
  const userResult = await client.query(
    'select id, name, role, banned from "user" where email = $1',
    [email],
  )
  if (userResult.rows.length === 0) {
    console.error(`Nenhum usuário encontrado com o e-mail ${email}. Nada foi alterado.`)
    process.exit(1)
  }
  const user = userResult.rows[0]
  console.log(`Usuário encontrado: ${user.name} (id=${user.id}, role=${user.role}, banned=${user.banned})`)

  const accountResult = await client.query(
    `select id from account where "userId" = $1 and "providerId" = 'credential'`,
    [user.id],
  )
  if (accountResult.rows.length === 0) {
    console.error('Nenhuma credencial de senha (providerId=credential) encontrada para este usuário. Nada foi alterado.')
    process.exit(1)
  }
  if (accountResult.rows.length > 1) {
    console.error('Mais de uma credencial encontrada para este usuário — abortando por segurança. Nada foi alterado.')
    process.exit(1)
  }

  const hash = hashPassword(newPassword)
  await client.query(
    'update account set password = $1, "updatedAt" = now() where id = $2',
    [hash, accountResult.rows[0].id],
  )

  console.log('Senha atualizada com sucesso.')
  console.log(`Nova senha: ${newPassword}`)
} finally {
  await client.end()
}
