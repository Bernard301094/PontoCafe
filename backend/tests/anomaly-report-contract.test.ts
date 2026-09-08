import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const read = (p: string) => readFileSync(new URL(`../../${p}`, import.meta.url), 'utf8')

const anomalias = read('backend/src/anomaly-report.ts')
const rotas = read('backend/src/routes/report-routes.ts')
const manual = read('backend/src/routes/manual-pause-routes.ts')
const resumo = read('backend/src/routes/audit-routes.ts')
const adminVm = read('app/src/main/java/com/pontocafe/app/AdminViewModel.kt')
const inicio = read('app/src/main/java/com/pontocafe/app/ui/AdminHomeScreenV2.kt')

test('o relatório de anomalias observa, e não acusa', () => {
  // O código prende a pausa a uma pessoa, mas não ao corpo dela: quem sai pode
  // entregar o código a um colega e pedir que registe o retorno. O servidor não
  // tem como recusar -- o par (nome, código) está correto. Resta olhar o padrão.
  assert.match(anomalias, /export async function anomalyReport\(/)
  assert.match(rotas, /relatorios\/anomalias/)

  // Os quatro sinais.
  for (const sinal of [
    'PAUSA_CURTA',
    'RETORNO_EM_RAJADA',
    'DISPOSITIVOS_DIFERENTES',
    'EXCESSO_RECORRENTE',
  ]) {
    assert.ok(anomalias.includes(sinal), `falta o sinal ${sinal}`)
  }

  // O corte da pausa curta é relativo à mediana da própria pessoa. Um piso fixo
  // marcaria todos os dias quem trabalha ao lado do café e volta em 4 minutos.
  assert.match(anomalias, /percentile_cont\(0\.5\)/)
  assert.match(anomalias, /FRACAO_PAUSA_CURTA = 0\.4/)
  assert.match(anomalias, /MINIMO_DE_PAUSAS = 4/)

  // A rajada pesa mais na pontuação porque é o sinal com explicação inocente
  // mais difícil: duas pessoas não digitam dois códigos em trinta segundos.
  assert.match(anomalias, /RAJADA_SEGUNDOS = 30/)
  assert.match(anomalias, /row\.retornosEmRajada \* 5/)

  // E a rota não bloqueia, não notifica e não pune: devolve uma lista.
  assert.doesNotMatch(rotas, /relatorios\/anomalias[\s\S]{0,600}(update|delete|insert)/i)
})

test('o registro manual exige justificativa que se possa ler depois', () => {
  // Eram 3 caracteres, e 3 aceita "esq". Quem lê a auditoria seis meses depois
  // precisa entender o que aconteceu -- é essa pessoa que o mínimo protege.
  assert.match(manual, /const MOTIVO_MINIMO = 20/)
  assert.match(manual, /min\(MOTIVO_MINIMO\)/)
  assert.match(adminVm, /internal const val MOTIVO_MANUAL_MINIMO = 20/)

  // O contador põe o número onde o Admin já olha todos os dias. Auditoria que
  // ninguém abre é arquivo morto.
  assert.match(resumo, /as "registrosManuais7Dias"/)
  assert.match(resumo, /as "supervisoresComRegistroManual"/)
  assert.match(inicio, /registro\(s\) manual\(is\) em 7 dias/)
})

test('dispositivo sem PIN é tratado como risco, não como recado', () => {
  // Sem PIN, sair do modo quiosque e mudar o relógio do Android é trivial -- e
  // sem rede o horário do registro vem exatamente daí.
  assert.match(inicio, /Sem PIN, qualquer pessoa sai do modo quiosque/)
  assert.match(
    inicio,
    /dispositivo\(s\) sem PIN próprio[\s\S]{0,400}PontoCafeTone\.DANGER/,
    'a falta de PIN precisa aparecer como crítica, não como aviso',
  )
})
