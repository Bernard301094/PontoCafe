export type DuplicateBiometricEvidence = {
  duplicate: boolean
  strongestScore: number
  consolidatedScore: number
  bestSampleScore: number
  matchingSamples: number
  strongSampleThreshold: number
}

export function evaluateDuplicateBiometric(
  consolidatedScore: number,
  sampleScores: number[],
  duplicateThreshold: number,
): DuplicateBiometricEvidence {
  const safeThreshold = Math.min(0.999, Math.max(0, duplicateThreshold))
  const strongSampleThreshold = Math.min(0.99, safeThreshold + 0.08)
  const finiteSamples = sampleScores.filter(Number.isFinite)
  const bestSampleScore = finiteSamples.length > 0 ? Math.max(...finiteSamples) : -1
  const matchingSamples = finiteSamples.filter((score) => score >= safeThreshold).length
  const duplicate =
    consolidatedScore >= safeThreshold ||
    matchingSamples >= 2 ||
    bestSampleScore >= strongSampleThreshold

  return {
    duplicate,
    strongestScore: Math.max(consolidatedScore, bestSampleScore),
    consolidatedScore,
    bestSampleScore,
    matchingSamples,
    strongSampleThreshold,
  }
}
