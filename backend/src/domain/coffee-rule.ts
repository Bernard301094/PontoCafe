export const STANDARD_COFFEE_LIMIT_SECONDS = 15 * 60
export const MIN_COFFEE_LIMIT_SECONDS = 60
export const MAX_COFFEE_LIMIT_SECONDS = 120 * 60

/**
 * Minuto de tolerância entre registar a saída e o limite começar a correr.
 *
 * Quem regista o ponto ainda está em frente ao quiosque, não em frente ao café:
 * o percurso até lá não pode sair do tempo de pausa. A carência fica gravada em
 * `pausas_cafe.carencia_segundos`, por linha, para que um relatório antigo seja
 * lido com a tolerância que valeu naquele dia e não com a de hoje.
 */
export const DEFAULT_COFFEE_GRACE_SECONDS = 60
export const MAX_COFFEE_GRACE_SECONDS = 3600

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

/** Tempo total que a pessoa pode ficar fora: a carência mais o limite. */
export function totalAwaySeconds(limitSeconds: number, graceSeconds: number): number {
  return Math.max(0, Math.floor(limitSeconds)) + Math.max(0, Math.floor(graceSeconds))
}

/** Segundos já consumidos do limite. Durante a carência devolve sempre 0. */
export function countedSeconds(elapsedSeconds: number, graceSeconds: number): number {
  return Math.max(0, Math.floor(elapsedSeconds) - Math.max(0, Math.floor(graceSeconds)))
}

export function exceededLimit(
  elapsedSeconds: number,
  limitSeconds: number,
  graceSeconds: number,
): boolean {
  return Math.floor(elapsedSeconds) > totalAwaySeconds(limitSeconds, graceSeconds)
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
