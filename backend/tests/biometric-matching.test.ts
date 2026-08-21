import assert from 'node:assert/strict'
import { performance } from 'node:perf_hooks'
import test from 'node:test'
import {
  FACE_EMBEDDING_DIMENSION,
  evaluateBiometricIdentification,
  evaluateEnrollmentConsistency,
  validateBiometricVector,
} from '../src/biometric-matching.js'

function unit(index: number): number[] {
  const vector = new Array<number>(FACE_EMBEDDING_DIMENSION).fill(0)
  vector[index] = 1
  return vector
}

function withQueryScore(score: number, axis = 1): number[] {
  const vector = new Array<number>(FACE_EMBEDDING_DIMENSION).fill(0)
  vector[0] = score
  vector[axis] = Math.sqrt(1 - score * score)
  return vector
}

function normalizedAverage(samples: number[][]): number[] {
  const average = new Array<number>(FACE_EMBEDDING_DIMENSION).fill(0)
  for (const sample of samples) {
    for (let index = 0; index < average.length; index++) average[index] += sample[index]
  }
  const norm = Math.sqrt(average.reduce((sum, value) => sum + value * value, 0))
  return average.map((value) => value / norm)
}

test('valida dimensão, finitude, norma zero e normalização L2', () => {
  assert.equal(validateBiometricVector(unit(0)).valid, true)
  assert.equal(validateBiometricVector([1, 0]).reason, 'WRONG_DIMENSION')
  assert.equal(validateBiometricVector(unit(0).map((value, index) => index === 3 ? Number.NaN : value)).reason, 'NON_FINITE')
  assert.equal(validateBiometricVector(new Array(FACE_EMBEDDING_DIMENSION).fill(0)).reason, 'ZERO_NORM')
  assert.equal(validateBiometricVector(unit(0).map((value) => value * 0.5)).reason, 'NOT_NORMALIZED')
})

test('aceita top-1 forte e agrupa múltiplos templates da mesma pessoa', () => {
  const result = evaluateBiometricIdentification(
    unit(0),
    [
      { collaboratorId: 'a', embedding: withQueryScore(0.91, 1), payload: 'a-1' },
      { collaboratorId: 'a', embedding: withQueryScore(0.90, 2), payload: 'a-2' },
      { collaboratorId: 'b', embedding: withQueryScore(0.80, 3), payload: 'b-1' },
    ],
    0.72,
    0.06,
  )

  assert.equal(result.accepted, true)
  assert.equal(result.best?.collaboratorId, 'a')
  assert.equal(result.second?.collaboratorId, 'b')
  assert.equal(result.candidateCount, 2)
  assert.equal(result.validTemplateCount, 3)
  assert.ok((result.margin ?? 0) > 0.10)
})

test('recusa score abaixo do limiar e faces próximas sem margem', () => {
  const below = evaluateBiometricIdentification(
    unit(0),
    [{ collaboratorId: 'a', embedding: withQueryScore(0.71), payload: 'a' }],
    0.72,
    0.06,
  )
  assert.equal(below.reason, 'BELOW_THRESHOLD')

  const ambiguous = evaluateBiometricIdentification(
    unit(0),
    [
      { collaboratorId: 'a', embedding: withQueryScore(0.90, 1), payload: 'a' },
      { collaboratorId: 'b', embedding: withQueryScore(0.86, 2), payload: 'b' },
    ],
    0.72,
    0.06,
  )
  assert.equal(ambiguous.accepted, false)
  assert.equal(ambiguous.reason, 'AMBIGUOUS')
  assert.ok((ambiguous.margin ?? 1) < 0.06)
})

test('descarta templates corrompidos sem perder candidatos íntegros', () => {
  const nonFinite = unit(2)
  nonFinite[4] = Number.POSITIVE_INFINITY
  const result = evaluateBiometricIdentification(
    unit(0),
    [
      { collaboratorId: 'a', embedding: unit(0), payload: 'valid' },
      { collaboratorId: 'b', embedding: [1, 0], payload: 'wrong-dimension' },
      { collaboratorId: 'c', embedding: nonFinite, payload: 'non-finite' },
    ],
    0.72,
    0.06,
  )

  assert.equal(result.accepted, true)
  assert.equal(result.best?.collaboratorId, 'a')
  assert.equal(result.validTemplateCount, 1)
  assert.equal(result.rejectedTemplateCount, 2)
})

test('cadastro exige cinco amostras coerentes e consolidado correspondente', () => {
  const coherent = [unit(0), unit(0), withQueryScore(0.98), withQueryScore(0.97), withQueryScore(0.99)]
  assert.equal(evaluateEnrollmentConsistency(normalizedAverage(coherent), coherent).valid, true)

  const switched = [unit(0), unit(0), unit(0), unit(0), unit(1)]
  assert.equal(
    evaluateEnrollmentConsistency(normalizedAverage(switched), switched).reason,
    'INCONSISTENT_SAMPLES',
  )
  assert.equal(evaluateEnrollmentConsistency(unit(1), coherent).reason, 'CONSOLIDATED_MISMATCH')
})

test('benchmark determinístico de busca exata no catálogo', (t) => {
  const templates = []
  for (let collaborator = 0; collaborator < 100; collaborator++) {
    const embedding = unit(collaborator === 0 ? 0 : (collaborator % 127) + 1)
    for (let variant = 0; variant < 5; variant++) {
      templates.push({
        collaboratorId: `collaborator-${collaborator}`,
        embedding,
        payload: variant,
      })
    }
  }

  const iterations = 250
  const started = performance.now()
  let last = evaluateBiometricIdentification(unit(0), templates, 0.72, 0.06)
  for (let iteration = 1; iteration < iterations; iteration++) {
    last = evaluateBiometricIdentification(unit(0), templates, 0.72, 0.06)
  }
  const elapsed = performance.now() - started

  assert.equal(last.accepted, true)
  assert.equal(last.best?.collaboratorId, 'collaborator-0')
  t.diagnostic(
    `catálogo=100 colaboradores/500 templates; ${iterations} buscas; ` +
      `total=${elapsed.toFixed(2)} ms; média=${(elapsed / iterations).toFixed(3)} ms`,
  )
})
