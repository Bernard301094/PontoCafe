import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'
import { buildQrPayload, parseQrPayload } from '../src/domain/qr-payload.js'

const here = dirname(fileURLToPath(import.meta.url))
const repoRoot = resolve(here, '..', '..')
const read = (relativePath: string) => readFileSync(resolve(repoRoot, relativePath), 'utf8')

const kotlinFonte = read('app/src/main/java/com/pontocafe/app/domain/QrPayload.kt')
const kotlinTeste = read('app/src/test/java/com/pontocafe/app/domain/QrPayloadTest.kt')

const COLABORADOR = '3f8a1c02-9b4d-4e77-8a10-6c5e2d9f1b33'

/**
 * Os mesmos vetores que `QrPayloadTest.kt` corre do lado Android.
 *
 * O formato do QR existe duas vezes, em duas linguagens. Uma divergência entre
 * as duas não produz erro nenhum em lado nenhum: produz uma câmara que lê e
 * nunca reconhece, sem nada no ecrã a dizer porquê. Esta tabela é a corda que
 * prende as duas pontas, e o teste abaixo verifica que a lista continua a
 * existir dos dois lados.
 */
const VETORES: Array<[string, string | null]> = [
  [`PONTOCAFE1|${COLABORADOR}|A7K2M9`, `${COLABORADOR}|A7K2M9`],
  // I e L valem 1, O vale 0 — a mesma tolerância do teclado.
  [`PONTOCAFE1|${COLABORADOR}|AIKOM9`, `${COLABORADOR}|A1K0M9`],
  [`PONTOCAFE1|${COLABORADOR.toUpperCase()}|A7K2M9`, `${COLABORADOR}|A7K2M9`],
  // A câmara lê tudo o que estiver à frente. Nada disto é um erro.
  ['https://exemplo.com', null],
  ['WIFI:S:cafe;T:WPA;P:segredo;;', null],
  ['', null],
  [`PONTOCAFE1|${COLABORADOR}`, null],
  [`PONTOCAFE1|${COLABORADOR}|A7K2M9|extra`, null],
  // Versão desconhecida: recusar é o seguro. Interpretar um formato futuro com
  // as regras de hoje podia abrir a pausa da pessoa errada.
  [`PONTOCAFE2|${COLABORADOR}|A7K2M9`, null],
  [`PONTOCAFE|${COLABORADOR}|A7K2M9`, null],
  ['PONTOCAFE1|nao-e-uuid|A7K2M9', null],
  [`PONTOCAFE1||A7K2M9`, null],
  [`PONTOCAFE1|${COLABORADOR}|A7K2M`, null],
  // Sete caracteres NÃO podem virar seis. Truncar aqui faria um QR adulterado
  // passar por um código válido.
  [`PONTOCAFE1|${COLABORADOR}|A7K2M99`, null],
  // U está fora do alfabeto de propósito e não tem mapeamento.
  [`PONTOCAFE1|${COLABORADOR}|A7K2MU`, null],
]

test('o parser do backend responde o mesmo que o do Android em cada vetor', () => {
  for (const [entrada, esperado] of VETORES) {
    const lido = parseQrPayload(entrada)
    const obtido = lido ? `${lido.colaboradorId}|${lido.codigo}` : null
    assert.equal(obtido, esperado, `divergência em ${JSON.stringify(entrada)}`)
  }
})

test('o que a rota emite é exatamente o que o aparelho consegue ler', () => {
  const payload = buildQrPayload(COLABORADOR, 'A7K2M9')
  assert.equal(payload, `PONTOCAFE1|${COLABORADOR}|A7K2M9`)
  assert.deepEqual(parseQrPayload(payload), { colaboradorId: COLABORADOR, codigo: 'A7K2M9' })
})

test('o lado Android corre a mesma tabela de vetores', () => {
  // Não compara comportamento — para isso existe o teste JUnit. Compara que a
  // tabela não encolheu de um dos lados: um vetor apagado no Kotlin seria uma
  // divergência a nascer sem nada a acusá-la.
  for (const [entrada] of VETORES) {
    if (!entrada) continue
    const codigo = entrada.split('|')[2]
    if (!codigo) continue
    assert.ok(
      kotlinTeste.includes(codigo),
      `o caso ${JSON.stringify(codigo)} não aparece em QrPayloadTest.kt`,
    )
  }
  assert.match(kotlinTeste, /https:\/\/exemplo\.com/)
  assert.match(kotlinTeste, /WIFI:S:cafe/)
  assert.match(kotlinTeste, /PONTOCAFE2/)
})

test('o Android não trunca o código como faria um teclado', () => {
  // `AccessCode.sanitizeInput` faz take(6) porque numa tecla a sétima não
  // entra. Num payload que chega inteiro, truncar é uma falha de segurança —
  // por isso a normalização do QR é a do backend, não a do teclado.
  assert.match(kotlinFonte, /private fun normalizarCodigo/)
  assert.match(kotlinFonte, /limpo\.length != AccessCode\.LENGTH/)
  // A chamada, não a menção: o comentário acima da função explica justamente
  // por que não se usa `sanitizeInput` aqui, e proibir a palavra proibiria a
  // explicação junto com o erro.
  assert.doesNotMatch(kotlinFonte, /=\s*AccessCode\.sanitizeInput\(/)
})
