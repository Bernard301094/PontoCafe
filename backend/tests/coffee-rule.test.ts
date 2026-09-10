import assert from 'node:assert/strict'
import test from 'node:test'
import {
  formatDuration,
  resolveCoffeeLimitSeconds,
  STANDARD_COFFEE_LIMIT_SECONDS,
} from '../src/domain/coffee-rule.js'

test('o limite padrão do Ponto Café é 15 minutos', () => {
  assert.equal(STANDARD_COFFEE_LIMIT_SECONDS, 900)
  assert.equal(formatDuration(STANDARD_COFFEE_LIMIT_SECONDS), '15:00')
})

test('aceita duração precisa em segundos', () => {
  assert.equal(resolveCoffeeLimitSeconds({ limiteSegundos: 900 }), 900)
  assert.equal(resolveCoffeeLimitSeconds({ limiteSegundos: 915 }), 915)
})

test('mantém compatibilidade com minutos inteiros', () => {
  assert.equal(resolveCoffeeLimitSeconds({ limiteMinutos: 15 }), 900)
})

test('rejeita limites operacionais inválidos', () => {
  assert.throws(() => resolveCoffeeLimitSeconds({ limiteSegundos: 30 }))
  assert.throws(() => resolveCoffeeLimitSeconds({ limiteSegundos: 8000 }))
})
