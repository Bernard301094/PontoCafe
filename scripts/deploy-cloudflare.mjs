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

const productionUrl = (process.env.PONTOCAFE_PRODUCTION_URL || 'https://pontocafe.bernard-castillo.workers.dev').replace(/\/$/, '')
const avatarBucketName = 'pontocafe-avatars'

const missing = requiredSecrets.filter((name) => !process.env[name]?.trim())
if (missing.length > 0) {
  console.error(`Missing Cloudflare build secrets: ${missing.join(', ')}`)
  process.exit(1)
}

const setupKeyFingerprint = createHash('sha256')
  .update(process.env.FIRST_ADMIN_SETUP_KEY)
  .digest('hex')
  .slice(0, 16)

const gitRevisionResult = spawnSync('git', ['rev-parse', 'HEAD'], {
  encoding: 'utf8',
  shell: false,
})
if (gitRevisionResult.error || gitRevisionResult.status !== 0) {
  console.error('Could not determine Git revision for Cloudflare deployment.')
  process.exit(1)
}
const backendRevision = gitRevisionResult.stdout.trim()
if (!/^[0-9a-f]{40}$/i.test(backendRevision)) {
  console.error('Invalid Git revision returned by git rev-parse HEAD.')
  process.exit(1)
}

function runWrangler(args, options = {}) {
  const result = spawnSync('npx', ['wrangler', ...args], {
    encoding: options.inherit ? undefined : 'utf8',
    stdio: options.inherit ? 'inherit' : undefined,
    shell: false,
  })
  if (result.error) throw result.error
  if (result.status !== 0) {
    if (!options.inherit) {
      if (result.stdout) process.stdout.write(result.stdout)
      if (result.stderr) process.stderr.write(result.stderr)
    }
    throw new Error(`Wrangler failed (${args.join(' ')}) with exit code ${result.status}.`)
  }
  return typeof result.stdout === 'string' ? result.stdout : ''
}

function ensureAvatarBucket() {
  console.log(`Checking private R2 bucket ${avatarBucketName}...`)
  const current = runWrangler(['r2', 'bucket', 'list'])
  if (current.toLowerCase().includes(avatarBucketName.toLowerCase())) {
    console.log(`R2 bucket ${avatarBucketName}: already available.`)
    return
  }

  console.log(`Creating private R2 bucket ${avatarBucketName}...`)
  runWrangler(['r2', 'bucket', 'create', avatarBucketName], { inherit: true })

  const confirmed = runWrangler(['r2', 'bucket', 'list'])
  if (!confirmed.toLowerCase().includes(avatarBucketName.toLowerCase())) {
    throw new Error(`R2 bucket ${avatarBucketName} was not visible after creation.`)
  }
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

async function fetchJson(url, attempts = 10) {
  let lastError
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    try {
      const response = await fetch(url, {
        headers: { 'cache-control': 'no-cache' },
        signal: AbortSignal.timeout(12_000),
      })
      const text = await response.text()
      if (!response.ok) throw new Error(`HTTP ${response.status}: ${text.slice(0, 300)}`)
      return JSON.parse(text)
    } catch (error) {
      lastError = error
      if (attempt < attempts) await sleep(2_000)
    }
  }
  throw lastError
}

console.log(`FIRST_ADMIN_SETUP_KEY fingerprint: ${setupKeyFingerprint}`)
console.log(`Deploying backend revision: ${backendRevision}`)

const directory = await mkdtemp(join(tmpdir(), 'pontocafe-secrets-'))
const secretsFile = join(directory, 'runtime-secrets.json')

try {
  ensureAvatarBucket()

  const secrets = Object.fromEntries(
    requiredSecrets.map((name) => [name, process.env[name]]),
  )

  await writeFile(secretsFile, JSON.stringify(secrets), { mode: 0o600 })

  console.log('Deploying Ponto Cafe Worker with encrypted runtime secrets...')
  runWrangler([
    'deploy',
    '--secrets-file',
    secretsFile,
    '--var',
    `BACKEND_REVISION:${backendRevision}`,
  ], { inherit: true })

  console.log(`Verifying deployed Worker at ${productionUrl}...`)
  const status = await fetchJson(`${productionUrl}/app-status`)
  const health = await fetchJson(`${productionUrl}/health`)

  if (status.backendRevision !== backendRevision) {
    throw new Error(
      `Deploy not confirmed: /app-status returned backendRevision=${String(status.backendRevision)}; expected=${backendRevision}.`,
    )
  }
  if (health.status !== 'ok' || health.banco !== 'ok') {
    throw new Error(`Health check failed: ${JSON.stringify(health)}`)
  }

  console.log('Cloudflare deployment confirmed successfully.')
  console.log(JSON.stringify({
    backendRevision,
    apiVersion: status.apiVersion,
    workerVersionId: status.workerVersionId ?? null,
    workerVersionTag: status.workerVersionTag ?? null,
    avatarBucket: avatarBucketName,
    banco: health.banco,
  }, null, 2))
} finally {
  await rm(directory, { recursive: true, force: true })
}
