/**
 * Atribui matrícula a quem ainda não tem.
 *
 *   node --env-file=.env scripts/gerar-matriculas.mjs --dry   # só mostra o plano
 *   node --env-file=.env scripts/gerar-matriculas.mjs         # aplica
 *
 * Para que serve: no totem, a lista tem a equipa inteira e a busca já aceita a
 * matrícula (ponto-routes: `col.matricula ilike '%'||$2||'%'`). Escrever "47"
 * deixa uma pessoa no ecrã; escrever "Francisco" deixa quatro. É o passo mais
 * lento de bater ponto, e resolve-se com um número curto.
 *
 * A atribuição é por ordem alfabética, e só para quem está sem matrícula: quem
 * já tem uma -- vinda do RH, por exemplo -- não é tocado. Correr duas vezes não
 * muda nada.
 *
 * SE A EMPRESA JÁ TIVER NÚMEROS DE FUNCIONÁRIO, use esses em vez destes. Dois
 * sistemas a numerar as mesmas pessoas de maneiras diferentes é uma confusão
 * que depois se paga.
 */
import pg from 'pg'

const APLICAR = !process.argv.includes('--dry')
const argumento = (nome, omissao) => {
  const encontrado = process.argv.find((a) => a.startsWith(`--${nome}=`))
  return encontrado ? Number(encontrado.split('=')[1]) : omissao
}

const INICIO = argumento('inicio', 1)
const DIGITOS = argumento('digitos', 3)

if (!Number.isInteger(INICIO) || INICIO < 0) {
  console.error('--inicio tem de ser um inteiro não negativo.')
  process.exit(1)
}
if (!Number.isInteger(DIGITOS) || DIGITOS < 1 || DIGITOS > 10) {
  console.error('--digitos tem de estar entre 1 e 10.')
  process.exit(1)
}

const connectionString = process.env.DATABASE_URL
if (!connectionString) {
  console.error('Defina DATABASE_URL. Corra com: node --env-file=.env scripts/gerar-matriculas.mjs')
  process.exit(1)
}

const local = /@(localhost|127\.0\.0\.1)[:/]/.test(connectionString)
const client = new pg.Client({ connectionString, ssl: local ? false : { rejectUnauthorized: false } })

await client.connect()
console.log(`Ligado a ${connectionString.replace(/:[^:@/]+@/, ':***@')}\n`)

const semMatricula = await client.query(
  `select id, nome from colaboradores
    where ativo = true and (matricula is null or btrim(matricula) = '')
    order by nome`,
)

if (semMatricula.rows.length === 0) {
  console.log('Toda a gente já tem matrícula. Nada a fazer.')
  await client.end()
  process.exit(0)
}

// Nunca reutilizar um número já em uso: a coluna tem índice único, e a colisão
// abortaria a meio deixando metade das pessoas com matrícula e metade sem.
const ocupadas = new Set(
  (await client.query(`select matricula from colaboradores where matricula is not null`)).rows.map((r) =>
    String(r.matricula).trim(),
  ),
)

const formatar = (n) => String(n).padStart(DIGITOS, '0')

const plano = []
let proximo = INICIO
for (const pessoa of semMatricula.rows) {
  while (ocupadas.has(formatar(proximo))) proximo += 1
  const matricula = formatar(proximo)
  if (matricula.length > DIGITOS) {
    console.error(`\nEsgotaram-se os números com ${DIGITOS} dígitos. Repita com --digitos=${DIGITOS + 1}.`)
    await client.end()
    process.exit(1)
  }
  ocupadas.add(matricula)
  plano.push({ ...pessoa, matricula })
  proximo += 1
}

plano.forEach((p) => console.log(`  ${p.matricula}  ${p.nome}`))
console.log(`\n${plano.length} colaborador(es) sem matrícula.`)

if (!APLICAR) {
  console.log('Modo --dry: nada foi gravado.')
  await client.end()
  process.exit(0)
}

// Tudo ou nada: uma equipa metade numerada é pior do que uma equipa por numerar,
// porque ninguém sabe em que estado ficou.
await client.query('BEGIN')
try {
  for (const p of plano) {
    await client.query('update colaboradores set matricula=$2, atualizado_em=now() where id=$1', [p.id, p.matricula])
    // O id de auditoria é gerado pelo banco: passá-lo dá
    // "cannot insert a non-DEFAULT value into column id".
    await client.query(
      `insert into auditoria (ator_auth_id,ator_tipo,acao,entidade,entidade_id,detalhes)
       values (null,'SISTEMA','ATRIBUIR_MATRICULA','COLABORADOR',$1,$2::jsonb)`,
      [p.id, JSON.stringify({ matricula: p.matricula, origem: 'scripts/gerar-matriculas.mjs' })],
    )
  }
  await client.query('COMMIT')
  console.log(`\n${plano.length} matrícula(s) atribuída(s).`)
} catch (erro) {
  await client.query('ROLLBACK')
  console.error(`\nFalhou, nada foi gravado: ${erro.message}`)
  await client.end()
  process.exit(1)
}

await client.end()
