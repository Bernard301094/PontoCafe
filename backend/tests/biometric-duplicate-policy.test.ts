import assert from 'node:assert/strict'
import test from 'node:test'
import { evaluateDuplicateBiometric } from '../src/biometric-duplicate-policy.js'

const threshold = 0.78

test('bloqueia quando o embedding consolidado já corresponde à pessoa existente', () => {
  const result = evaluateDuplicateBiometric(0.81, [0.72, 0.74, 0.73, 0.75, 0.71], threshold)
  assert.equal(result.duplicate, true)
})

test('bloqueia quando duas amostras individuais atingem o limiar', () => {
  const result = evaluateDuplicateBiometric(0.74, [0.79, 0.80, 0.70, 0.72, 0.73], threshold)
  assert.equal(result.duplicate, true)
  assert.equal(result.matchingSamples, 2)
})

test('bloqueia uma amostra individual excepcionalmente forte', () => {
  const result = evaluateDuplicateBiometric(0.73, [0.87, 0.72, 0.70, 0.74, 0.71], threshold)
  assert.equal(result.duplicate, true)
})

test('não marca como duplicado quando todas as evidências ficam abaixo da política', () => {
  const result = evaluateDuplicateBiometric(0.71, [0.72, 0.74, 0.73, 0.75, 0.70], threshold)
  assert.equal(result.duplicate, false)
})
