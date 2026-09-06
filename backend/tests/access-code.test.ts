import assert from 'node:assert/strict'
import test from 'node:test'
import {
  ACCESS_CODE_ALPHABET,
  ACCESS_CODE_KEYSPACE,
  ACCESS_CODE_LENGTH,
  formatAccessCode,
  generateAccessCode,
  isAccessCode,
  normalizeAccessCode,
} from '../src/domain/access-code.js'
import {
  countedSeconds,
  DEFAULT_COFFEE_GRACE_SECONDS,
  exceededLimit,
  STANDARD_COFFEE_LIMIT_SECONDS,
  totalAwaySeconds,
} from '../src/domain/coffee-rule.js'

test('o alfabeto exclui exatamente os símbolos que se confundem no papel', () => {
  assert.equal(ACCESS_CODE_ALPHABET.length, 32)
  assert.equal(new Set(ACCESS_CODE_ALPHABET).size, 32)
  for (const excluded of ['I', 'L', 'O', 'U']) {
    assert.ok(!ACCESS_CODE_ALPHABET.includes(excluded), `${excluded} não deveria estar no alfabeto`)
  }
  assert.equal(ACCESS_CODE_KEYSPACE, 32 ** 6)
})

test('todo código gerado tem 6 símbolos do alfabeto', () => {
  for (let i = 0; i < 500; i++) {
    const code = generateAccessCode()
    assert.equal(code.length, ACCESS_CODE_LENGTH)
    for (const char of code) assert.ok(ACCESS_CODE_ALPHABET.includes(char))
  }
})

test('códigos consecutivos não colapsam num mesmo valor', () => {
  const generated = new Set<string>()
  for (let i = 0; i < 400; i++) generated.add(generateAccessCode())
  // Com 32^6 combinações, 400 sorteios repetidos seriam sinal de gerador quebrado.
  assert.ok(generated.size >= 395, `esperava quase 400 valores distintos, obtive ${generated.size}`)
})

test('cada símbolo do alfabeto aparece ao longo de uma amostra grande', () => {
  // Um bug clássico de máscara (usar & 15 em vez de & 31) reduziria o alfabeto
  // pela metade sem quebrar nenhum dos testes acima.
  const seen = new Set<string>()
  for (let i = 0; i < 5_000; i++) {
    for (const char of generateAccessCode()) seen.add(char)
  }
  assert.equal(seen.size, 32)
})

test('a normalização aceita o que a pessoa realmente digita', () => {
  assert.equal(normalizeAccessCode('a7k2m9'), 'A7K2M9')
  assert.equal(normalizeAccessCode(' A7K-2M9 '), 'A7K2M9')
  assert.equal(normalizeAccessCode('A7K.2M9'), 'A7K2M9')
  // I, L e O são as confusões que o alfabeto foi desenhado para tolerar.
  assert.equal(normalizeAccessCode('I7K2M9'), '17K2M9')
  assert.equal(normalizeAccessCode('l7K2M9'), '17K2M9')
  assert.equal(normalizeAccessCode('O7K2M9'), '07K2M9')
})

test('a normalização recusa o que nunca poderia ser um código', () => {
  assert.equal(normalizeAccessCode(''), null)
  assert.equal(normalizeAccessCode('A7K2M'), null)
  assert.equal(normalizeAccessCode('A7K2M99'), null)
  // U fica de fora do alfabeto e não tem para onde ser mapeado.
  assert.equal(normalizeAccessCode('U7K2M9'), null)
  assert.equal(normalizeAccessCode('A7K2M!'), null)
  assert.equal(isAccessCode('A7K2M9'), true)
  assert.equal(isAccessCode('A7K2M'), false)
})

test('o código é ditado em dois blocos de três', () => {
  assert.equal(formatAccessCode('A7K2M9'), 'A7K-2M9')
})

test('a carência atrasa o início da contagem sem encurtar o café', () => {
  const limite = STANDARD_COFFEE_LIMIT_SECONDS
  const carencia = DEFAULT_COFFEE_GRACE_SECONDS

  assert.equal(limite, 900)
  assert.equal(carencia, 60)
  assert.equal(totalAwaySeconds(limite, carencia), 960)

  // Durante o minuto de tolerância nada é descontado do café.
  assert.equal(countedSeconds(0, carencia), 0)
  assert.equal(countedSeconds(59, carencia), 0)
  assert.equal(countedSeconds(60, carencia), 0)
  assert.equal(countedSeconds(61, carencia), 1)

  // Quem volta exatamente no prazo não excede; um segundo depois, sim.
  assert.equal(exceededLimit(960, limite, carencia), false)
  assert.equal(exceededLimit(961, limite, carencia), true)
  // Sem a carência o mesmo retorno de 16 minutos seria um atraso de 1 minuto.
  assert.equal(exceededLimit(960, limite, 0), true)
})
