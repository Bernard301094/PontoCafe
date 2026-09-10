// Adiciona ao banco os colaboradores de colaboradores.txt que ainda não existem.
//
//   $env:DATABASE_URL = "sua_string_de_conexao"
//   node importar-colaboradores.mjs --dry     # só mostra o plano
//   node importar-colaboradores.mjs           # aplica
//
// É idempotente: rodar duas vezes não duplica ninguém.
import { readFileSync } from 'node:fs'
import { randomUUID } from 'node:crypto'
import pg from 'pg'

const APLICAR = !process.argv.includes('--dry')

if (!process.env.DATABASE_URL) {
  console.error('Defina DATABASE_URL antes de rodar.')
  process.exit(1)
}

/**
 * Chave de comparação: sem acento, sem caixa, sem espaço duplo.
 *
 * O banco guarda "Abner Jonnas da Silva" e a lista veio "ABNER JONNAS DA
 * SILVA". Comparar cru criaria uma segunda linha para a mesma pessoa, e o
 * quiosque passaria a mostrar o nome dela duas vezes.
 */
const chave = (nome) =>
  nome.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/\s+/g, ' ').trim()

/** Partículas que ficam minúsculas mesmo em Title Case, em português. */
const PARTICULAS = new Set(['da', 'de', 'do', 'das', 'dos', 'e'])

const titulo = (nome) =>
  nome.toLocaleLowerCase('pt-BR').split(/\s+/).filter(Boolean)
    .map((palavra, i) =>
      i > 0 && PARTICULAS.has(palavra)
        ? palavra
        : palavra.charAt(0).toLocaleUpperCase('pt-BR') + palavra.slice(1),
    )
    .join(' ')

const linhas = readFileSync('colaboradores.txt', 'utf8')
  .split(/\r?\n/)
  .map((l) => l.trim())
  .filter(Boolean)

const desejados = linhas.map((linha) => {
  // "FULANO - supervisor" -> o sufixo é anotação de quem passou a lista, não
  // parte do nome. Conta de Supervisor é outra coisa: exige e-mail e senha.
  const marcadoSupervisor = /\s-\s*supervisora?$/i.test(linha)
  const nome = titulo(linha.replace(/\s-\s*supervisora?$/i, '').replace(/\s+/g, ' ').trim())
  return { nome, chave: chave(nome), marcadoSupervisor }
})

// A própria lista pode trazer o mesmo nome duas vezes.
const porChave = new Map()
for (const p of desejados) if (!porChave.has(p.chave)) porChave.set(p.chave, p)
const duplicadosNaLista = desejados.length - porChave.size

const client = new pg.Client({ connectionString: process.env.DATABASE_URL })
await client.connect()

try {
  const existentes = await client.query('select id, nome, ativo from colaboradores')
  const chavesExistentes = new Map(existentes.rows.map((r) => [chave(r.nome), r]))

  const novos = [...porChave.values()].filter((p) => !chavesExistentes.has(p.chave))
  const jaExistem = [...porChave.values()].filter((p) => chavesExistentes.has(p.chave))
  const inativos = jaExistem.filter((p) => chavesExistentes.get(p.chave).ativo === false)
  const noBancoForaDaLista = existentes.rows.filter((r) => !porChave.has(chave(r.nome)))

  // Duplicados que JÁ existem impedem a migração 013 de ser aplicada, e são
  // exactamente o problema que ela existe para travar. Reportar antes evita
  // que a migração falhe com um erro do Postgres sem explicação.
  const porNome = new Map()
  for (const r of existentes.rows) {
    if (r.ativo === false) continue
    const k = chave(r.nome)
    porNome.set(k, [...(porNome.get(k) ?? []), r])
  }
  const repetidos = [...porNome.values()].filter((g) => g.length > 1)

  console.log(`No banco hoje ....... ${existentes.rows.length}`)
  console.log(`Na lista ............ ${porChave.size}${duplicadosNaLista ? ` (${duplicadosNaLista} repetido(s) na própria lista)` : ''}`)
  console.log(`Já existem .......... ${jaExistem.length}`)
  console.log(`A adicionar ......... ${novos.length}`)
  if (inativos.length) console.log(`Existem mas INATIVOS  ${inativos.length}  -> ${inativos.map((p) => p.nome).join(', ')}`)
  console.log()

  if (novos.length) {
    console.log('--- serão adicionados ---')
    for (const p of novos) console.log(`  + ${p.nome}${p.marcadoSupervisor ? '   [marcado como supervisor na lista]' : ''}`)
    console.log()
  }

  if (noBancoForaDaLista.length) {
    console.log(`--- ${noBancoForaDaLista.length} no banco que NÃO estão na lista (não vou tocar) ---`)
    for (const r of noBancoForaDaLista) console.log(`  ? ${r.nome}${r.ativo ? '' : ' (inativo)'}`)
    console.log()
  }

  if (repetidos.length) {
    console.log(`--- ATENÇÃO: ${repetidos.length} nome(s) JÁ DUPLICADO(S) no banco ---`)
    for (const grupo of repetidos) {
      console.log(`  ! ${grupo.map((r) => r.nome).join(' | ')}`)
      for (const r of grupo) console.log(`      id ${r.id}`)
    }
    console.log('  Resolva antes de aplicar a migração 013: arquive o repetido')
    console.log('  (ativo=false) ou corrija o nome. NÃO apague -- as pausas apontam para o id.')
    console.log()
  }

  if (!APLICAR) {
    console.log('Modo --dry: nada foi gravado.')
  } else if (novos.length === 0) {
    console.log('Nada a fazer: todos já existem.')
  } else {
    await client.query('begin')
    for (const p of novos) {
      await client.query(
        'insert into colaboradores (id, matricula, nome, setor, turno) values ($1, null, $2, null, null)',
        [randomUUID(), p.nome],
      )
    }
    await client.query('commit')
    console.log(`${novos.length} colaborador(es) adicionado(s).`)
  }
} catch (erro) {
  await client.query('rollback').catch(() => {})
  console.error('\nFALHOU, nada foi gravado:', erro.message)
  process.exitCode = 1
} finally {
  await client.end()
}
