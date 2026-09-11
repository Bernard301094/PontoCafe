/**
 * Liga e desliga o PostgreSQL portátil usado no desenvolvimento local.
 *
 *   node scripts/postgres-local.mjs start | stop | status
 *
 * Os binários são os oficiais (EnterpriseDB, sem instalador) e vivem fora do
 * repositório, em ~/.pontocafe-pg — nada disto entra no git nem no PATH do
 * sistema. Para desfazer tudo: parar o servidor e apagar essa pasta.
 */
import { spawnSync } from 'node:child_process'
import { existsSync } from 'node:fs'
import { homedir } from 'node:os'
import { join } from 'node:path'

const RAIZ = join(homedir(), '.pontocafe-pg')
const BIN = join(RAIZ, 'pgsql', 'bin')
const DADOS = join(RAIZ, 'data')
const LOG = join(RAIZ, 'postgres.log')
const PORTA = '5432'

function exe(nome) {
  return join(BIN, process.platform === 'win32' ? `${nome}.exe` : nome)
}

function correr(nome, args, opcoes = {}) {
  const r = spawnSync(exe(nome), args, { stdio: 'inherit', ...opcoes })
  if (r.error) throw r.error
  return r.status ?? 1
}

/**
 * `pg_ctl start` deixa o servidor a herdar os handles que lhe dermos, e o
 * servidor fica vivo a segurá-los — com stdio herdado, este processo nunca
 * termina. Descartamos os handles (o servidor já escreve tudo em -l LOG) e
 * relatamos o resultado com um `status` à parte, que devolve de imediato.
 */
function arrancar() {
  const r = spawnSync(exe('pg_ctl'), ['-D', DADOS, '-l', LOG, '-o', `-p ${PORTA}`, 'start'], {
    stdio: 'ignore',
  })
  if (r.error) throw r.error
  return spawnSync(exe('pg_ctl'), ['-D', DADOS, 'status'], { stdio: 'inherit' }).status ?? 1
}

if (!existsSync(BIN)) {
  console.error(`Binários não encontrados em ${BIN}.`)
  console.error('Descarrega postgresql-17.6-1-windows-x64-binaries.zip de get.enterprisedb.com')
  console.error(`e extrai de modo a ficares com ${join(RAIZ, 'pgsql', 'bin')}.`)
  process.exit(1)
}

const comando = process.argv[2] ?? 'status'

if (comando === 'start') {
  // pg_ctl devolve 0 quando já está a correr, por isso start é repetível.
  const codigo = arrancar()
  if (codigo !== 0) {
    console.error(`\nFalhou. O log costuma dizer porquê: ${LOG}`)
    process.exit(codigo)
  }
} else if (comando === 'stop') {
  process.exit(correr('pg_ctl', ['-D', DADOS, '-m', 'fast', 'stop']))
} else if (comando === 'status') {
  process.exit(correr('pg_ctl', ['-D', DADOS, 'status']))
} else {
  console.error(`Comando desconhecido: ${comando}. Usa start, stop ou status.`)
  process.exit(1)
}
