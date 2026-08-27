import { execFileSync } from 'node:child_process'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const scriptDir = path.dirname(fileURLToPath(import.meta.url))
const backendDir = path.resolve(scriptDir, '..')
const repoRoot = path.resolve(backendDir, '..')
const productionUrl = (process.env.PONTOCAFE_PRODUCTION_URL || 'https://pontocafe.bernard-castillo.workers.dev').replace(/\/$/, '')
const avatarBucketName = 'pontocafe-avatars'
const require = createRequire(import.meta.url)
const backendPackage = require('../package.json')
const expectedApiVersion = String(backendPackage.version || '').trim()
if (!/^\d+\.\d+\.\d+$/.test(expectedApiVersion)) {
  throw new Error(`Versão inválida em backend/package.json: ${expectedApiVersion || '(vazia)'}.`)
}
const wranglerPackageJson = require.resolve('wrangler/package.json')
const wranglerCli = path.join(path.dirname(wranglerPackageJson), 'bin', 'wrangler.js')

function quoteWindowsArg(value) {
  const text = String(value)
  if (!/[\s"&|<>^()%!]/.test(text)) return text
  return `"${text.replaceAll('"', '""')}"`
}

function run(command, args, cwd = backendDir) {
  if (process.platform === 'win32' && /(?:^|[\\/])npm\.cmd$/i.test(command)) {
    const commandLine = [command, ...args].map(quoteWindowsArg).join(' ')
    execFileSync(process.env.ComSpec || 'C:\\Windows\\System32\\cmd.exe', ['/d', '/s', '/c', commandLine], {
      cwd,
      stdio: 'inherit',
      env: process.env,
    })
    return
  }

  execFileSync(command, args, {
    cwd,
    stdio: 'inherit',
    env: process.env,
  })
}

function runWrangler(args) {
  run(process.execPath, [wranglerCli, ...args])
}

function output(command, args, cwd = backendDir) {
  return execFileSync(command, args, {
    cwd,
    encoding: 'utf8',
    env: process.env,
  }).trim()
}

function wranglerOutput(args) {
  return output(process.execPath, [wranglerCli, ...args])
}

function ensureAvatarBucket() {
  const currentBuckets = wranglerOutput(['r2', 'bucket', 'list'])
  const normalized = currentBuckets.toLowerCase()
  if (normalized.includes(avatarBucketName)) {
    console.log(`R2 ${avatarBucketName}: já existe.`)
    return
  }

  console.log(`Criando R2 privado ${avatarBucketName}...`)
  runWrangler(['r2', 'bucket', 'create', avatarBucketName])

  const afterCreate = wranglerOutput(['r2', 'bucket', 'list']).toLowerCase()
  if (!afterCreate.includes(avatarBucketName)) {
    throw new Error(`O bucket R2 ${avatarBucketName} não apareceu após a criação.`)
  }
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

async function fetchJson(url, attempts = 8) {
  let lastError

  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    try {
      const response = await fetch(url, {
        headers: { 'cache-control': 'no-cache' },
        signal: AbortSignal.timeout(12_000),
      })

      const text = await response.text()
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${text.slice(0, 300)}`)
      }

      return JSON.parse(text)
    } catch (error) {
      lastError = error
      if (attempt < attempts) await sleep(2_000)
    }
  }

  throw lastError
}

const npmCommand = process.platform === 'win32' ? 'npm.cmd' : 'npm'
const revision = output('git', ['rev-parse', '--short=12', 'HEAD'], repoRoot)

console.log(`\n[1/5] Garantindo armazenamento privado de avatar...`)
ensureAvatarBucket()

console.log(`\n[2/5] Validando backend ${expectedApiVersion} · ${revision}...`)
run(npmCommand, ['run', 'validate'])

console.log(`\n[3/5] Validando bundle do Worker...`)
runWrangler(['deploy', '--dry-run'])

console.log(`\n[4/5] Publicando Worker ${expectedApiVersion} com tag ${revision}...`)
runWrangler([
  'deploy',
  '--tag',
  revision,
  '--message',
  `PontoCafe ${expectedApiVersion} · ${revision}`,
])

console.log(`\n[5/5] Verificando Worker publicado em ${productionUrl}...`)
const status = await fetchJson(`${productionUrl}/app-status`)
const health = await fetchJson(`${productionUrl}/health`)

if (status.workerVersionTag !== revision) {
  throw new Error(
    `Deploy não confirmado: /app-status retornou workerVersionTag=${String(status.workerVersionTag)}; esperado=${revision}.`,
  )
}

if (status.apiVersion !== expectedApiVersion) {
  throw new Error(
    `Versão inesperada da API: ${String(status.apiVersion)}; esperado=${expectedApiVersion}.`,
  )
}

if (health.status !== 'ok' || health.banco !== 'ok') {
  throw new Error(`Health check inválido: ${JSON.stringify(health)}`)
}

console.log('\nDeploy confirmado com sucesso.')
console.log(JSON.stringify({
  revision,
  expectedApiVersion,
  avatarBucket: avatarBucketName,
  workerVersionId: status.workerVersionId,
  workerVersionTag: status.workerVersionTag,
  workerVersionTimestamp: status.workerVersionTimestamp,
  apiVersion: status.apiVersion,
  banco: health.banco,
}, null, 2))
