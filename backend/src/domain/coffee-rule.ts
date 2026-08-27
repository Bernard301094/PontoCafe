export const STANDARD_COFFEE_LIMIT_SECONDS = 15 * 60
export const MIN_COFFEE_LIMIT_SECONDS = 60
export const MAX_COFFEE_LIMIT_SECONDS = 120 * 60

export function resolveCoffeeLimitSeconds(input: {
  limiteSegundos?: number
  limiteMinutos?: number
}): number {
  const value = input.limiteSegundos ?? (input.limiteMinutos == null ? NaN : input.limiteMinutos * 60)
  if (!Number.isInteger(value) || value < MIN_COFFEE_LIMIT_SECONDS || value > MAX_COFFEE_LIMIT_SECONDS) {
    throw new Error('COFFEE_LIMIT_INVALID')
  }
  return value
}

export function splitDuration(totalSeconds: number): { minutes: number; seconds: number } {
  const safe = Math.max(0, Math.floor(totalSeconds))
  return {
    minutes: Math.floor(safe / 60),
    seconds: safe % 60,
  }
}

export function formatDuration(totalSeconds: number): string {
  const { minutes, seconds } = splitDuration(totalSeconds)
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}
