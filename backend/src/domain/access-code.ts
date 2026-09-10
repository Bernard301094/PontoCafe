import { randomBytes } from 'node:crypto'

/**
 * Código de acesso ao café: 6 caracteres que o Supervisor emite e a pessoa
 * digita no quiosque — uma vez para sair e outra para voltar.
 *
 * O alfabeto é o Crockford Base32: os dígitos e as letras, menos I, L, O e U.
 * I/L/O saem porque ninguém os distingue de 1 e 0 num papel escrito à pressa;
 * U sai para nenhum sorteio produzir uma palavra ofensiva. Como sobram
 * exactamente 32 símbolos, cada byte aleatório mascarado com 31 escolhe um
 * símbolo com probabilidade idêntica — não há rejeição nem viés a corrigir.
 */
export const ACCESS_CODE_ALPHABET = '0123456789ABCDEFGHJKMNPQRSTVWXYZ'
export const ACCESS_CODE_LENGTH = 6

/** 32^6 ≈ 1,07 mil milhões de combinações. */
export const ACCESS_CODE_KEYSPACE = ACCESS_CODE_ALPHABET.length ** ACCESS_CODE_LENGTH

export function generateAccessCode(): string {
  const bytes = randomBytes(ACCESS_CODE_LENGTH)
  let code = ''
  for (const byte of bytes) code += ACCESS_CODE_ALPHABET[byte & 31]
  return code
}

/**
 * Aceita o que a pessoa realmente digita: minúsculas, espaços, hífens e as
 * confusões visuais que o alfabeto foi desenhado para tolerar. Devolve null
 * quando o resultado não é um código possível, para o chamador nunca ter de
 * decidir se uma string vazia é "inválida" ou "ainda não preenchida".
 */
export function normalizeAccessCode(raw: string): string | null {
  const cleaned = raw
    .toUpperCase()
    .replace(/[\s.\-_]/g, '')
    .replace(/[IL]/g, '1')
    .replace(/O/g, '0')

  if (cleaned.length !== ACCESS_CODE_LENGTH) return null
  for (const char of cleaned) {
    if (!ACCESS_CODE_ALPHABET.includes(char)) return null
  }
  return cleaned
}

export function isAccessCode(value: string): boolean {
  return normalizeAccessCode(value) !== null
}

/** Formato de leitura em voz alta e nos cartões do Supervisor: `A7K-2M9`. */
export function formatAccessCode(code: string): string {
  return `${code.slice(0, 3)}-${code.slice(3)}`
}
