// Prova a consulta de anomalias contra o Postgres real. SÓ LEITURA.
//
// Rode com:
//   $env:DATABASE_URL = "sua_string_de_conexao"
//   node --import tsx testar-anomalias.mjs
//
// Depois apague este arquivo: del testar-anomalias.mjs

// O módulo importa config.ts, que exige estas variáveis para carregar. São
// inertes aqui: nada neste script cifra, assina ou autentica nada.
process.env.CODE_PEPPER ??= 'inerte-para-teste'
process.env.APP_ENCRYPTION_KEY ??= Buffer.alloc(32).toString('base64')
process.env.BETTER_AUTH_SECRET ??= 'inerte-para-teste'

if (!process.env.DATABASE_URL) {
  console.error('Defina DATABASE_URL antes de rodar. Ex.:')
  console.error('  $env:DATABASE_URL = "postgresql://usuario:senha@host/neondb?sslmode=require"')
  process.exit(1)
}

const { anomalyReport } = await import('./backend/src/anomaly-report.ts')

const fim = new Date().toISOString().slice(0, 10)
const inicio = new Date(Date.now() - 30 * 86_400_000).toISOString().slice(0, 10)

console.log(`Período analisado: ${inicio} a ${fim}\n`)

const linhas = await anomalyReport(inicio, fim)

console.log('=========================================================')
console.log(`A CONSULTA RODOU. ${linhas.length} pessoa(s) com algum sinal.`)
console.log('=========================================================\n')

if (linhas.length === 0) {
  console.log('Nenhum sinal no período. Isso é um bom resultado — e também')
  console.log('significa que ainda não há dados suficientes para julgar.')
} else {
  for (const l of linhas.slice(0, 15)) {
    console.log(`${String(l.pontuacao).padStart(4)} pts   ${l.nome}${l.setor ? `  (${l.setor})` : ''}`)
    console.log(
      `           ${l.totalPausas} pausas · mediana ${Math.round(l.medianaSegundos / 60)} min` +
      ` · curtas ${l.pausasCurtas} · rajada ${l.retornosEmRajada}` +
      ` · quiosques dif. ${l.dispositivosDiferentes} · excessos ${l.excessos}`,
    )
    console.log(`           sinais: ${l.sinais.join(', ')}\n`)
  }
  if (linhas.length > 15) console.log(`… e mais ${linhas.length - 15}.`)
}

console.log('\nLembrete: isto não prova nada. É uma lista para alguém olhar.')
console.log('Um caso isolado é ruído; o mesmo nome repetindo-se é conversa a ter.')

process.exit(0)
