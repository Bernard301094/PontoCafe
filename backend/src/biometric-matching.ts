export const FACE_EMBEDDING_DIMENSION = 128
export const MINIMUM_INTRA_USER_SIMILARITY = 0.60

export type BiometricVectorValidation = {
  valid: boolean
  reason?: 'WRONG_DIMENSION' | 'NON_FINITE' | 'ZERO_NORM' | 'NOT_NORMALIZED'
  norm?: number
}

export function validateBiometricVector(
  embedding: number[],
  expectedDimension = FACE_EMBEDDING_DIMENSION,
  requireUnitNorm = true,
): BiometricVectorValidation {
  if (embedding.length !== expectedDimension) return { valid: false, reason: 'WRONG_DIMENSION' }
  let normSquared = 0
  for (const value of embedding) {
    if (!Number.isFinite(value)) return { valid: false, reason: 'NON_FINITE' }
    normSquared += value * value
  }
  if (!Number.isFinite(normSquared)) return { valid: false, reason: 'NON_FINITE' }
  const norm = Math.sqrt(normSquared)
  if (norm <= 1e-12) return { valid: false, reason: 'ZERO_NORM', norm }
  if (requireUnitNorm && Math.abs(norm - 1) > 0.02) {
    return { valid: false, reason: 'NOT_NORMALIZED', norm }
  }
  return { valid: true, norm }
}

function cosineSimilarity(left: number[], right: number[]): number {
  if (left.length !== right.length || left.length === 0) return Number.NaN
  let dot = 0
  let leftNorm = 0
  let rightNorm = 0
  for (let index = 0; index < left.length; index++) {
    dot += left[index] * right[index]
    leftNorm += left[index] * left[index]
    rightNorm += right[index] * right[index]
  }
  if (leftNorm <= 1e-12 || rightNorm <= 1e-12) return Number.NaN
  return dot / Math.sqrt(leftNorm * rightNorm)
}

export type BiometricCandidateTemplate<T> = {
  collaboratorId: string
  embedding: number[]
  payload: T
}

export type BiometricIdentificationResult<T> = {
  accepted: boolean
  reason: 'ACCEPTED' | 'INVALID_QUERY' | 'NO_CANDIDATE' | 'BELOW_THRESHOLD' | 'AMBIGUOUS'
  best: { collaboratorId: string; score: number; payload: T } | null
  second: { collaboratorId: string; score: number; payload: T } | null
  margin: number | null
  candidateCount: number
  validTemplateCount: number
  rejectedTemplateCount: number
}

/** Maximum template score per collaborator prevents template-count advantage. */
export function evaluateBiometricIdentification<T>(
  query: number[],
  templates: BiometricCandidateTemplate<T>[],
  threshold: number,
  minimumMargin: number,
  expectedDimension = FACE_EMBEDDING_DIMENSION,
): BiometricIdentificationResult<T> {
  const queryValidation = validateBiometricVector(query, expectedDimension)
  if (!queryValidation.valid) {
    return result('INVALID_QUERY', null, null, 0, 0, templates.length)
  }

  const byCollaborator = new Map<string, { collaboratorId: string; score: number; payload: T }>()
  let validTemplateCount = 0
  let rejectedTemplateCount = 0
  for (const template of templates) {
    if (!template.collaboratorId || !validateBiometricVector(template.embedding, expectedDimension).valid) {
      rejectedTemplateCount += 1
      continue
    }
    const score = cosineSimilarity(template.embedding, query)
    if (!Number.isFinite(score)) {
      rejectedTemplateCount += 1
      continue
    }
    validTemplateCount += 1
    const current = byCollaborator.get(template.collaboratorId)
    if (!current || score > current.score) {
      byCollaborator.set(template.collaboratorId, {
        collaboratorId: template.collaboratorId,
        score,
        payload: template.payload,
      })
    }
  }

  const candidates = [...byCollaborator.values()].sort((left, right) => right.score - left.score)
  const best = candidates[0] ?? null
  const second = candidates[1] ?? null
  if (!best) return result('NO_CANDIDATE', null, null, 0, validTemplateCount, rejectedTemplateCount)
  if (best.score < threshold) {
    return result('BELOW_THRESHOLD', best, second, candidates.length, validTemplateCount, rejectedTemplateCount)
  }
  if (second && best.score - second.score < minimumMargin) {
    return result('AMBIGUOUS', best, second, candidates.length, validTemplateCount, rejectedTemplateCount)
  }
  return result('ACCEPTED', best, second, candidates.length, validTemplateCount, rejectedTemplateCount)
}

function result<T>(
  reason: BiometricIdentificationResult<T>['reason'],
  best: BiometricIdentificationResult<T>['best'],
  second: BiometricIdentificationResult<T>['second'],
  candidateCount: number,
  validTemplateCount: number,
  rejectedTemplateCount: number,
): BiometricIdentificationResult<T> {
  return {
    accepted: reason === 'ACCEPTED',
    reason,
    best,
    second,
    margin: best && second ? best.score - second.score : best ? 1 : null,
    candidateCount,
    validTemplateCount,
    rejectedTemplateCount,
  }
}

export type EnrollmentConsistency = {
  valid: boolean
  reason?: 'INVALID_VECTOR' | 'INCONSISTENT_SAMPLES' | 'CONSOLIDATED_MISMATCH'
  minimumSimilarity: number
  meanSimilarity: number
  consolidatedSimilarity: number
}

export function evaluateEnrollmentConsistency(
  consolidated: number[],
  samples: number[][],
  minimumSimilarity = MINIMUM_INTRA_USER_SIMILARITY,
  expectedDimension = FACE_EMBEDDING_DIMENSION,
): EnrollmentConsistency {
  const all = [consolidated, ...samples]
  if (all.some((embedding) => !validateBiometricVector(embedding, expectedDimension).valid)) {
    return { valid: false, reason: 'INVALID_VECTOR', minimumSimilarity: -1, meanSimilarity: -1, consolidatedSimilarity: -1 }
  }
  if (samples.length === 0) {
    return { valid: true, minimumSimilarity: 1, meanSimilarity: 1, consolidatedSimilarity: 1 }
  }

  const sums = samples.map((sample, index) =>
    samples.reduce((sum, other, otherIndex) => sum + (index === otherIndex ? 1 : cosineSimilarity(sample, other)), 0),
  )
  const medoidIndex = sums.indexOf(Math.max(...sums))
  const similarities = samples
    .filter((_, index) => index !== medoidIndex)
    .map((sample) => cosineSimilarity(samples[medoidIndex], sample))
  const minimumToMedoid = similarities.length > 0 ? Math.min(...similarities) : 1
  const mean = similarities.length > 0
    ? similarities.reduce((sum, score) => sum + score, 0) / similarities.length
    : 1

  const average = new Array<number>(expectedDimension).fill(0)
  for (const sample of samples) {
    for (let index = 0; index < expectedDimension; index++) average[index] += sample[index]
  }
  let normSquared = 0
  for (let index = 0; index < average.length; index++) {
    average[index] /= samples.length
    normSquared += average[index] * average[index]
  }
  const norm = Math.sqrt(normSquared)
  if (!Number.isFinite(norm) || norm <= 1e-12) {
    return { valid: false, reason: 'INVALID_VECTOR', minimumSimilarity: minimumToMedoid, meanSimilarity: mean, consolidatedSimilarity: -1 }
  }
  for (let index = 0; index < average.length; index++) average[index] /= norm
  const consolidatedSimilarity = cosineSimilarity(consolidated, average)
  const minimumToAggregate = Math.min(...samples.map((sample) => cosineSimilarity(average, sample)))
  const minimum = Math.min(minimumToMedoid, minimumToAggregate)

  if (minimum < minimumSimilarity) {
    return { valid: false, reason: 'INCONSISTENT_SAMPLES', minimumSimilarity: minimum, meanSimilarity: mean, consolidatedSimilarity }
  }
  if (consolidatedSimilarity < 0.995) {
    return { valid: false, reason: 'CONSOLIDATED_MISMATCH', minimumSimilarity: minimum, meanSimilarity: mean, consolidatedSimilarity }
  }
  return { valid: true, minimumSimilarity: minimum, meanSimilarity: mean, consolidatedSimilarity }
}
