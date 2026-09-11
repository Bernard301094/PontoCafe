/**
 * Aplica o schema do Ponto Café num Postgres vazio, na ordem que o
 * database/README.md descreve para uma instalação nova.
 *
 *   node --env-file=.env scripts/setup-local-db.mjs
 *
 * A 001 é o protótipo antigo e não entra. A 005 e a 006 também não: a 012
 * desfaz exactamente o que elas criam, por isso numa base nova são ruído.
 *
 * O ficheiro aplicado fica registado em `_migracoes_locais`, então correr o
 * script outra vez não repete nada — só aplica o que falta.
 */
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'
import pg from 'pg'

const raiz = join(dirname(fileURLToPath(import.meta.url)), '..')

const MIGRACOES = [
  '002_better_auth_business_schema.sql',
  '003_device_unlock_pin.sql',
  '004_device_registration_idempotency.sql',
  '007_ponto_operation_idempotency.sql',
  '008_release_readiness_indexes.sql',
  '009_supervisor_onboarding.sql',
  '010_manual_pause_close.sql',
  '011_manual_pause_open.sql',
  '012_access_codes.sql',
  '013_colaborador_nome_unico.sql',
  '014_codigo_por_periodo_e_qr.sql',
]

const connectionString = process.env.DATABASE_URL
if (!connectionString) {
  console.error('Falta DATABASE_URL. Corre com: node --env-file=.env scripts/setup-local-db.mjs')
  process.exit(1)
}

// Neon/Supabase exigem TLS; um Postgres local normalmente não o oferece.
const local = /@(localhost|127\.0\.0\.1)[:/]/.test(connectionString)
const client = new pg.Client({
  connectionString,
  ssl: local ? false : { rejectUnauthorized: false },
})

await client.connect()
console.log(`Ligado a ${connectionString.replace(/:[^:@/]+@/, ':***@')}`)

/*
 * A 004 faz `grant ... to ponto_cafe_api`. Em produção esse papel já existe,
 * criado fora das migrações; num cluster novo não, e a migração falha. Criamo-lo
 * sem LOGIN: aqui serve só de alvo dos grants, porque em local ligamos como
 * superutilizador.
 */
await client.query(`
  DO $$
  BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ponto_cafe_api') THEN
      CREATE ROLE ponto_cafe_api NOLOGIN;
    END IF;
  END
  $$
`)

await client.query(`
  CREATE TABLE IF NOT EXISTS _migracoes_locais (
    arquivo     text PRIMARY KEY,
    aplicada_em timestamptz NOT NULL DEFAULT now()
  )
`)

const { rows } = await client.query('SELECT arquivo FROM _migracoes_locais')
const jaAplicadas = new Set(rows.map((r) => r.arquivo))

let aplicadas = 0
for (const arquivo of MIGRACOES) {
  if (jaAplicadas.has(arquivo)) {
    console.log(`  ·  ${arquivo} (já aplicada)`)
    continue
  }

  const sql = await readFile(join(raiz, 'database', arquivo), 'utf8')
  try {
    await client.query('BEGIN')
    await client.query(sql)
    await client.query('INSERT INTO _migracoes_locais (arquivo) VALUES ($1)', [arquivo])
    await client.query('COMMIT')
    console.log(`  ✓  ${arquivo}`)
    aplicadas += 1
  } catch (erro) {
    await client.query('ROLLBACK')
    console.error(`  ✗  ${arquivo}\n     ${erro.message}`)
    await client.end()
    process.exit(1)
  }
}

await client.end()
console.log(
  aplicadas === 0
    ? 'Nada a fazer: o schema já estava em dia.'
    : `${aplicadas} migração(ões) aplicada(s). Falta só o primeiro admin: npm run auth:bootstrap`,
)
