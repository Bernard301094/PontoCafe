import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const scriptDir = path.dirname(fileURLToPath(import.meta.url))
const backendDir = path.resolve(scriptDir, '..')
const repoRoot = path.resolve(backendDir, '..')
const productionUrl = (process.env.PONTOCAFE_PRODUCTION_URL || 'https://pontocafe.bernard-castillo.workers.dev').replace(/\/$/, '')

function run(command, args, cwd = backendDir) {
  execFileSync(command, args, {
    cwd,
    stdio: 'inherit',
    env: process.env,
  })
}

function output(command, args, cwd = backendDir) {
  return execFileSync(command, args, {
    cwd,
    encoding: 'utf8',
    env: process.env,
  }).trim()
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

const revision = output('git', ['rev-parse', '--short=12', 'HEAD'], repoRoot)

console.log(`\n[1/4] Validando backend ${revision}...`)
run(process.platform === 'win32' ? 'npm.cmd' : 'npm', ['run', 'validate'])

console.log(`\n[2/4] Validando bundle do Worker...`)
run(process.platform === 'win32' ? 'npx.cmd' : 'npx', ['wrangler', 'deploy', '--dry-run'])

console.log(`\n[3/4] Publicando Worker com tag ${revision}...`)
run(process.platform === 'win32' ? 'npx.cmd' : 'npx', [
  'wrangler',
  'deploy',
  '--tag',
  revision,
  '--message',
  `PontoCafe ${revision}`,
])

console.log(`\n[4/4] Verificando Worker publicado em ${productionUrl}...`)
const status = await fetchJson(`${productionUrl}/app-status`)
const health = await fetchJson(`${productionUrl}/health`)

if (status.workerVersionTag !== revision) {
  throw new Error(
    `Deploy não confirmado: /app-status retornou workerVersionTag=${String(status.workerVersionTag)}; esperado=${revision}.`,
  )
}

if (status.apiVersion !== '0.7.0') {
  throw new Error(`Versão inesperada da API: ${String(status.apiVersion)}.`)
}

if (health.status !== 'ok' || health.banco !== 'ok') {
  throw new Error(`Health check inválido: ${JSON.stringify(health)}`)
}

console.log('\nDeploy confirmado com sucesso.')
console.log(JSON.stringify({
  revision,
  workerVersionId: status.workerVersionId,
  workerVersionTag: status.workerVersionTag,
  workerVersionTimestamp: status.workerVersionTimestamp,
  apiVersion: status.apiVersion,
  banco: health.banco,
}, null, 2))
