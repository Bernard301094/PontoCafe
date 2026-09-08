import { execFileSync } from 'node:child_process'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const scriptDir = path.dirname(fileURLToPath(import.meta.url))
const backendDir = path.resolve(scriptDir, '..')
const repoRoot = path.resolve(backendDir, '..')
const productionUrl = (process.env.PONTOCAFE_PRODUCTION_URL || 'https://pontocafe.bernard-castillo.workers.dev').replace(/\/$/, '')
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
// A tag do Worker é curta porque é o que cabe na lista de versões do painel;
// BACKEND_REVISION é o SHA inteiro, igual ao que scripts/deploy-cloudflare.mjs
// publica -- os dois caminhos de deploy têm de descrever o mesmo commit da
// mesma forma, senão /app-status responde uma coisa num deploy e outra no
// seguinte.
const backendRevision = output('git', ['rev-parse', 'HEAD'], repoRoot)
if (!/^[0-9a-f]{40}$/i.test(backendRevision)) {
  throw new Error(`Revisão Git inválida: ${backendRevision}`)
}

console.log(`\n[1/4] Validando backend ${expectedApiVersion} · ${revision}...`)
run(npmCommand, ['run', 'validate'])

console.log(`\n[2/4] Validando bundle do Worker...`)
runWrangler(['deploy', '--dry-run'])

console.log(`\n[3/4] Publicando Worker ${expectedApiVersion} com tag ${revision}...`)
runWrangler([
  'deploy',
  '--tag',
  revision,
  '--message',
  `PontoCafe ${expectedApiVersion} · ${revision}`,
  // Sem isto o Worker herda o BACKEND_REVISION que já estava gravado -- e o
  // wrangler.jsonc usa keep_vars, então o valor antigo sobrevive a cada
  // publicação. Foi exactamente o que aconteceu: o Worker rodava o código do
  // merge e /app-status apontava para um commit de duas semanas antes.
  '--var',
  `BACKEND_REVISION:${backendRevision}`,
])

console.log(`\n[4/4] Verificando Worker publicado em ${productionUrl}...`)
const status = await fetchJson(`${productionUrl}/app-status`)
const health = await fetchJson(`${productionUrl}/health`)

if (status.workerVersionTag !== revision) {
  throw new Error(
    `Deploy não confirmado: /app-status retornou workerVersionTag=${String(status.workerVersionTag)}; esperado=${revision}.`,
  )
}

// A tag confirma que a publicação chegou; a revisão confirma que o campo de
// diagnóstico descreve o código que está a correr. Conferir só a tag deixou
// passar meses de /app-status a apontar para o commit errado.
if (status.backendRevision !== backendRevision) {
  throw new Error(
    `Revisão não confirmada: /app-status retornou backendRevision=${String(status.backendRevision)}; esperado=${backendRevision}.`,
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
  backendRevision: status.backendRevision,
  expectedApiVersion,
  workerVersionId: status.workerVersionId,
  workerVersionTag: status.workerVersionTag,
  workerVersionTimestamp: status.workerVersionTimestamp,
  apiVersion: status.apiVersion,
  banco: health.banco,
}, null, 2))
