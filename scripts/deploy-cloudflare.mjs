import { createHash } from 'node:crypto'
import { mkdtemp, writeFile, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { spawnSync } from 'node:child_process'

const requiredSecrets = [
  'BETTER_AUTH_SECRET',
  'CODE_PEPPER',
  'BIOMETRIC_MASTER_KEY',
  'FIRST_ADMIN_SETUP_KEY',
]

const missing = requiredSecrets.filter((name) => !process.env[name]?.trim())
if (missing.length > 0) {
  console.error(`Missing Cloudflare build secrets: ${missing.join(', ')}`)
  process.exit(1)
}

const setupKeyFingerprint = createHash('sha256')
  .update(process.env.FIRST_ADMIN_SETUP_KEY)
  .digest('hex')
  .slice(0, 16)

console.log(`FIRST_ADMIN_SETUP_KEY fingerprint: ${setupKeyFingerprint}`)

const directory = await mkdtemp(join(tmpdir(), 'pontocafe-secrets-'))
const secretsFile = join(directory, 'runtime-secrets.json')

try {
  const secrets = Object.fromEntries(
    requiredSecrets.map((name) => [name, process.env[name]]),
  )

  await writeFile(secretsFile, JSON.stringify(secrets), { mode: 0o600 })

  console.log('Deploying Ponto Cafe Worker with 4 encrypted runtime secrets...')
  const result = spawnSync(
    'npx',
    ['wrangler', 'deploy', '--secrets-file', secretsFile],
    { stdio: 'inherit', shell: false },
  )

  if (result.error) throw result.error
  process.exitCode = result.status ?? 1
} finally {
  await rm(directory, { recursive: true, force: true })
}
