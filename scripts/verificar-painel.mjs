/**
 * Compila o JavaScript que o painel entrega ao navegador.
 *
 *   node scripts/verificar-painel.mjs
 *
 * Porque e preciso: server.ts guarda a pagina inteira dentro de um template
 * literal. Para o TypeScript isso e uma string opaca, por isso o tsc e o esbuild
 * dao o ficheiro por bom mesmo quando o script do cliente tem um erro de
 * sintaxe -- e um erro ali nao parte um botao, parte a aplicacao toda: nada
 * carrega, e a consola do servidor nao diz nada.
 *
 * Tres niveis de citacao encaixam neste ficheiro -- o template do servidor, os
 * templates do browser e os atributos HTML -- e ja foi assim que se perderam
 * uma crase e uma barra invertida.
 */
import { readFileSync } from 'node:fs'
import { Script } from 'node:vm'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const raiz = join(dirname(fileURLToPath(import.meta.url)), '..')
const origem = readFileSync(join(raiz, 'server.ts'), 'utf8')

const inicio = origem.indexOf('const HTML_CONTENT = ')
if (inicio === -1) {
  console.error('Nao encontrei HTML_CONTENT em server.ts.')
  process.exit(1)
}

/*
 * Procura a crase de fecho a serio, saltando as que estao escapadas: cortar por
 * um marcador de texto falharia, porque o proprio HTML tem crases escapadas la
 * dentro. Os caracteres vao por codigo (92 = barra invertida, 96 = crase) para
 * nao haver mais um nivel de escapes neste ficheiro.
 */
const BARRA = 92
const CRASE = 96
const abre = origem.indexOf('`', inicio)
let fecha = -1
for (let i = abre + 1; i < origem.length; i += 1) {
  const codigo = origem.charCodeAt(i)
  if (codigo === BARRA) { i += 1; continue }
  if (codigo === CRASE) { fecha = i; break }
}
if (fecha === -1) {
  console.error('O template de HTML_CONTENT nao fecha.')
  process.exit(1)
}

let html
try {
  html = new Script(origem.slice(abre, fecha + 1)).runInNewContext({})
} catch (erro) {
  console.error('O proprio template nao avalia: ' + erro.message)
  process.exit(1)
}

const blocos = [...html.matchAll(/<script(?![^>]*\bsrc=)[^>]*>([\s\S]*?)<\/script>/g)].map((m) => m[1])
if (blocos.length === 0) {
  console.error('Nenhum <script> inline encontrado -- o extractor precisa de revisao.')
  process.exit(1)
}

let falhou = false
blocos.forEach((bloco, i) => {
  try {
    new Script(bloco)
    console.log(`  bloco ${i}: ok (${(bloco.length / 1024).toFixed(0)} KB)`)
  } catch (erro) {
    falhou = true
    console.error(`  bloco ${i}: ${erro.message}`)
    const linha = Number((erro.stack?.match(/evalmachine[^:]*:(\d+)/) || [])[1])
    if (linha) console.error(`    linha ${linha}: ${(bloco.split('\n')[linha - 1] || '').trim().slice(0, 200)}`)
  }
})

if (falhou) process.exit(1)

/*
 * Compilar nao chega. Um erro em tempo de execucao no arranque -- ler um const
 * antes da sua declaracao, chamar algo que ainda nao existe -- mata o script
 * todo antes de definir refreshData, e o painel fica em branco com a base de
 * dados perfeitamente ligada. Ja aconteceu.
 *
 * Corre-se o bloco da aplicacao num DOM de mentira. Nao se testa o que ele faz;
 * testa-se que chega ao fim sem rebentar. Sem lucide de proposito: se o CDN
 * demorar, o arranque nao pode depender dele.
 */
const no = () => ({
  className: '', textContent: '', innerHTML: '', value: '', style: {}, dataset: {},
  classList: { add() {}, remove() {}, contains: () => false, toggle() {} },
  setAttribute() {}, removeAttribute() {}, getAttribute: () => null,
  focus() {}, scrollIntoView() {}, addEventListener() {}, reset() {},
  querySelectorAll: () => [],
})

const contexto = {
  document: {
    getElementById: () => no(),
    querySelectorAll: () => [],
    createElement: () => no(),
    addEventListener() {},
    body: no(),
    hidden: false,
  },
  window: { addEventListener() {}, location: {}, PONTO_API_BASE: 'http://localhost:3000' },
  localStorage: { getItem: () => null, setItem() {}, removeItem() {} },
  navigator: { clipboard: {} },
  setInterval() {}, setTimeout() {}, clearInterval() {}, clearTimeout() {},
  fetch: () => Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve({}) }),
  console,
}
contexto.globalThis = contexto

try {
  new Script(blocos[blocos.length - 1]).runInNewContext(contexto)
  console.log('  arranque: ok (sem erro de execucao)')
} catch (erro) {
  console.error(`  arranque: ${erro.constructor.name}: ${erro.message}`)
  const linha = Number((erro.stack?.match(/evalmachine[^:]*:(\d+)/) || [])[1])
  if (linha) {
    const fonte = blocos[blocos.length - 1].split('\n')[linha - 1] || ''
    console.error(`    linha ${linha}: ${fonte.trim().slice(0, 200)}`)
  }
  process.exit(1)
}

console.log('\nO script do painel compila e arranca.')
