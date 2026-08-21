import { createHash } from 'node:crypto'
import { spawnSync } from 'node:child_process'
import { mkdtemp, readFile, writeFile, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

const requiredSecrets = [
  'BETTER_AUTH_SECRET',
  'CODE_PEPPER',
  'BIOMETRIC_MASTER_KEY',
  'FIRST_ADMIN_SETUP_KEY',
]

const repoRoot = fileURLToPath(new URL('../', import.meta.url))
const backendDir = fileURLToPath(new URL('../backend/', import.meta.url))
const productionUrl = (process.env.PONTOCAFE_PRODUCTION_URL || 'https://pontocafe.bernard-castillo.workers.dev').replace(/\/$/, '')
const avatarBucketName = 'pontocafe-avatars'
const npmCommand = process.platform === 'win32' ? 'npm.cmd' : 'npm'
const npxCommand = process.platform === 'win32' ? 'npx.cmd' : 'npx'

const backendPackage = JSON.parse(
  await readFile(new URL('../backend/package.json', import.meta.url), 'utf8'),
)
const gradleSource = await readFile(new URL('../app/build.gradle.kts', import.meta.url), 'utf8')
const backendConfigSource = await readFile(new URL('../backend/src/config.ts', import.meta.url), 'utf8')

const expectedApiVersion = String(backendPackage.version || '').trim()
const androidVersionMatch = gradleSource.match(/versionName\s*=\s*"(\d+\.\d+\.\d+)"/)
const minimumAndroidMatch = backendConfigSource.match(/APP_MIN_ANDROID_VERSION',\s*'(\d+\.\d+\.\d+)'/)
const expectedAndroidVersion = androidVersionMatch?.[1] || ''
const expectedMinimumAndroidVersion = minimumAndroidMatch?.[1] || ''

for (const [label, value] of [
  ['backend/package.json', expectedApiVersion],
  ['app/build.gradle.kts versionName', expectedAndroidVersion],
  ['backend minimum Android version', expectedMinimumAndroidVersion],
]) {
  if (!/^\d+\.\d+\.\d+$/.test(value)) {
    throw new Error(`Could not resolve a valid production version from ${label}: ${value || '(empty)'}.`)
  }
}

const missing = requiredSecrets.filter((name) => !process.env[name]?.trim())
if (missing.length > 0) {
  console.error(`Missing Cloudflare build secrets: ${missing.join(', ')}`)
  process.exit(1)
}

const setupKeyFingerprint = createHash('sha256')
  .update(process.env.FIRST_ADMIN_SETUP_KEY)
  .digest('hex')
  .slice(0, 16)

function run(command, args, options = {}) {
  const result = spawnSync(command, args, {
    cwd: options.cwd || repoRoot,
    encoding: options.inherit ? undefined : 'utf8',
    stdio: options.inherit ? 'inherit' : undefined,
    shell: false,
    env: process.env,
  })
  if (result.error) throw result.error
  if (result.status !== 0) {
    if (!options.inherit) {
      if (result.stdout) process.stdout.write(result.stdout)
      if (result.stderr) process.stderr.write(result.stderr)
    }
    throw new Error(`${command} ${args.join(' ')} failed with exit code ${result.status}.`)
  }
  return typeof result.stdout === 'string' ? result.stdout : ''
}

const gitRevisionResult = run('git', ['rev-parse', 'HEAD'], { cwd: repoRoot })
const backendRevision = gitRevisionResult.trim()
if (!/^[0-9a-f]{40}$/i.test(backendRevision)) {
  console.error('Invalid Git revision returned by git rev-parse HEAD.')
  process.exit(1)
}

function runWrangler(args, options = {}) {
  return run(npxCommand, ['wrangler', ...args], { ...options, cwd: backendDir })
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
console.log(`Preparing backend ${expectedApiVersion} revision: ${backendRevision}`)

console.log('\n[1/6] Running backend tests and typecheck...')
run(npmCommand, ['--workspace', 'backend', 'run', 'validate'], { cwd: repoRoot, inherit: true })

console.log('\n[2/6] Running PontoCafe 1.0 release contract...')
run(npmCommand, ['run', 'release:check'], { cwd: repoRoot, inherit: true })

console.log('\n[3/6] Validating Cloudflare Worker bundle...')
runWrangler(['deploy', '--dry-run'], { inherit: true })

const directory = await mkdtemp(join(tmpdir(), 'pontocafe-secrets-'))
const secretsFile = join(directory, 'runtime-secrets.json')

try {
  console.log('\n[4/6] Ensuring private avatar storage...')
  ensureAvatarBucket()

  const secrets = Object.fromEntries(
    requiredSecrets.map((name) => [name, process.env[name]]),
  )

  await writeFile(secretsFile, JSON.stringify(secrets), { mode: 0o600 })

  console.log('\n[5/6] Deploying Ponto Cafe Worker with encrypted runtime secrets...')
  runWrangler([
    'deploy',
    '--secrets-file',
    secretsFile,
    '--var',
    `BACKEND_REVISION:${backendRevision}`,
  ], { inherit: true })

  console.log(`\n[6/6] Verifying deployed Worker at ${productionUrl}...`)
  const status = await fetchJson(`${productionUrl}/app-status`)
  const health = await fetchJson(`${productionUrl}/health`)

  if (status.backendRevision !== backendRevision) {
    throw new Error(
      `Deploy not confirmed: /app-status returned backendRevision=${String(status.backendRevision)}; expected=${backendRevision}.`,
    )
  }
  if (status.apiVersion !== expectedApiVersion) {
    throw new Error(
      `API version mismatch: ${String(status.apiVersion)}; expected=${expectedApiVersion}.`,
    )
  }
  if (status.latestAndroidVersion !== expectedAndroidVersion) {
    throw new Error(
      `Android latest-version policy mismatch: ${String(status.latestAndroidVersion)}; expected=${expectedAndroidVersion}.`,
    )
  }
  if (status.minimumAndroidVersion !== expectedMinimumAndroidVersion) {
    throw new Error(
      `Android minimum-version policy mismatch: ${String(status.minimumAndroidVersion)}; expected=${expectedMinimumAndroidVersion}.`,
    )
  }
  if (health.status !== 'ok' || health.banco !== 'ok') {
    throw new Error(`Health check failed: ${JSON.stringify(health)}`)
  }

  console.log('Cloudflare deployment confirmed successfully.')
  console.log(JSON.stringify({
    backendRevision,
    apiVersion: status.apiVersion,
    latestAndroidVersion: status.latestAndroidVersion,
    minimumAndroidVersion: status.minimumAndroidVersion,
    workerVersionId: status.workerVersionId ?? null,
    workerVersionTag: status.workerVersionTag ?? null,
    avatarBucket: avatarBucketName,
    banco: health.banco,
  }, null, 2))
} finally {
  await rm(directory, { recursive: true, force: true })
}
