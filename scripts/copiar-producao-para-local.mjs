/**
 * Copia a base de produção (Neon) para o Postgres local, por cima do que lá
 * estiver. O local passa a ser um espelho: os mesmos colaboradores, os mesmos
 * dispositivos, a mesma auditoria -- e as contas reais, com as senhas reais.
 *
 *   node --env-file=.env scripts/copiar-producao-para-local.mjs --dry
 *   node --env-file=.env scripts/copiar-producao-para-local.mjs
 *
 * Precisa de PROD_DATABASE_URL no .env (o .env está no .gitignore).
 *
 * Produção é aberta apenas para LEITURA: só corre pg_dump. Quem é destruído e
 * recriado é o banco LOCAL -- por isso o passo de confirmação.
 */
import { spawnSync } from 'node:child_process'
import { existsSync, mkdirSync, statSync } from 'node:fs'
import { homedir, tmpdir } from 'node:os'
import { join } from 'node:path'

const SECO = process.argv.includes('--dry')
const BIN = join(homedir(), '.pontocafe-pg', 'pgsql', 'bin')
const exe = (n) => join(BIN, process.platform === 'win32' ? `${n}.exe` : n)

const origem = process.env.PROD_DATABASE_URL?.trim()
const destino = process.env.DATABASE_URL?.trim()

if (!origem) {
  console.error('Falta PROD_DATABASE_URL no .env.')
  console.error('Vai ao painel do Neon, copia a connection string e acrescenta ao .env:')
  console.error('  PROD_DATABASE_URL=postgresql://neondb_owner:SENHA@ep-winter-boat-axbx1wr4-pooler.c-4.us-east-2.aws.neon.tech/neondb?sslmode=require')
  process.exit(1)
}
if (!destino) {
  console.error('Falta DATABASE_URL no .env (o destino local).')
  process.exit(1)
}
if (!/@(localhost|127\.0\.0\.1)[:/]/.test(destino)) {
  console.error('DATABASE_URL não aponta para localhost. Recusado: este script apaga o destino.')
  process.exit(1)
}
if (!existsSync(BIN)) {
  console.error(`Binários do Postgres não encontrados em ${BIN}.`)
  process.exit(1)
}

const esconde = (url) => url.replace(/:[^:@/]+@/, ':***@')
const pasta = join(tmpdir(), 'pontocafe-dump')
mkdirSync(pasta, { recursive: true })
const ficheiro = join(pasta, 'producao.dump')

console.log(`origem  ${esconde(origem)}`)
console.log(`destino ${esconde(destino)}`)
console.log()

if (SECO) {
  console.log('Modo --dry: nada foi copiado nem apagado.')
  console.log('Sem --dry, o banco local seria APAGADO e recriado a partir de produção.')
  process.exit(0)
}

function correr(nome, args, env = {}) {
  const r = spawnSync(exe(nome), args, { stdio: 'inherit', env: { ...process.env, ...env } })
  if (r.error) throw r.error
  if (r.status !== 0) {
    console.error(`\n${nome} falhou (código ${r.status}).`)
    process.exit(r.status ?? 1)
  }
}

// 1. Produção: só leitura.
console.log('1/4  pg_dump de produção...')
correr('pg_dump', ['--format=custom', '--no-owner', '--no-privileges', '--file', ficheiro, origem])
console.log(`     ${(statSync(ficheiro).size / 1048576).toFixed(1)} MB`)

// 2. O destino é recriado do zero: um restore por cima de tabelas já cheias
//    deixaria linhas antigas misturadas com as novas.
const admin = destino.replace(/\/[^/?]+(\?|$)/, '/postgres$1')
const nomeBanco = new URL(destino).pathname.slice(1)
console.log(`2/4  a apagar e recriar o banco local "${nomeBanco}"...`)
correr('psql', ['--quiet', '--command', `drop database if exists "${nomeBanco}" with (force)`, admin])
correr('psql', ['--quiet', '--command', `create database "${nomeBanco}"`, admin])

// 3. O papel dos grants da 004 não vem no dump (--no-owner) e o restore
//    reclamaria de cada grant; criá-lo antes evita o ruído.
console.log('3/4  a preparar o papel da aplicação...')
correr('psql', ['--quiet', '--command',
  `do $$ begin if not exists (select 1 from pg_roles where rolname='ponto_cafe_api') then create role ponto_cafe_api nologin; end if; end $$`,
  destino])

console.log('4/4  pg_restore para o local...')
correr('pg_restore', ['--no-owner', '--no-privileges', '--dbname', destino, ficheiro])

console.log('\nPronto. O local é agora uma cópia de produção -- incluindo as contas e senhas reais.')
console.log('Reinicia a API para largar as ligações antigas:')
console.log('  npx tsx --env-file=.env backend/src/local.ts')
