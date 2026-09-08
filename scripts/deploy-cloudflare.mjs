import { createHash } from 'node:crypto'
import { spawnSync } from 'node:child_process'
import { mkdtemp, readFile, writeFile, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

const requiredSecrets = [
  'BETTER_AUTH_SECRET',
  'CODE_PEPPER',
  'APP_ENCRYPTION_KEY',
  'FIRST_ADMIN_SETUP_KEY',
]

const repoRoot = fileURLToPath(new URL('../', import.meta.url))
const backendDir = fileURLToPath(new URL('../backend/', import.meta.url))
const productionUrl = (process.env.PONTOCAFE_PRODUCTION_URL || 'https://pontocafe.bernard-castillo.workers.dev').replace(/\/$/, '')
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

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

/**
 * Fetches until the answer is the expected one -- not until the request works.
 *
 * The retry used to fire only when the call failed, and a 200 served by the
 * PREVIOUS version counts as a success. A publish takes a few more seconds to
 * reach every edge, so the script read the old version, concluded nothing had
 * changed and aborted -- reporting failure on a deploy that was live. A checker
 * that cries wolf on good deploys teaches whoever operates it to ignore it.
 */
async function fetchJson(url, { attempts = 20, delayMs = 3_000, until } = {}) {
  let lastError
  let lastPayload
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    try {
      const response = await fetch(url, {
        headers: { 'cache-control': 'no-cache' },
        signal: AbortSignal.timeout(12_000),
      })
      const text = await response.text()
      if (!response.ok) throw new Error(`HTTP ${response.status}: ${text.slice(0, 300)}`)
      const payload = JSON.parse(text)
      lastPayload = payload
      if (!until || until(payload)) return payload
      lastError = new Error('The response still comes from an earlier version.')
    } catch (error) {
      lastError = error
    }
    if (attempt < attempts) await sleep(delayMs)
  }
  if (lastPayload) return lastPayload
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
  console.log('\n[4/6] Preparing encrypted runtime secrets...')

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
  // Wait for the edge to serve the version that was just published, instead of
  // reading the previous one and concluding the deploy failed.
  const status = await fetchJson(`${productionUrl}/app-status`, {
    until: (payload) => payload.backendRevision === backendRevision,
  })
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
    banco: health.banco,
  }, null, 2))
} finally {
  await rm(directory, { recursive: true, force: true })
}
