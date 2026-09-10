import { normalizeAccessCode } from './access-code.js'

/**
 * O conteúdo do QR do café.
 *
 * Formato: `PONTOCAFE1|<uuid do colaborador>|<código de 6>`
 *
 * Porquê levar o colaborador dentro do QR, e não só o código: sem ele a pessoa
 * ainda teria de se encontrar numa lista de quase cem nomes antes de apontar a
 * câmara, e a leitura pouparia seis toques de um lado para custar cinco do
 * outro. Com os dois, um único gesto resolve a batida inteira -- que é a razão
 * de existir do QR.
 *
 * E não acrescenta autoridade nenhuma: o código de acesso JÁ está preso a um
 * colaborador no banco. Um QR que dissesse outro nome seria recusado na mesma,
 * porque a comparação é sempre (este colaborador, este código).
 *
 * O que o QR é, honestamente: o mesmo segredo de 6 caracteres, em forma
 * legível por câmara. Quem o reencaminha por mensagem reencaminha o código --
 * exactamente como quem o dita em voz alta. O que contém esse risco é o prazo
 * do código, a liberação por aparelho e a auditoria própria de cada batida
 * lida por câmara; não é a codificação.
 *
 * A versão vai no prefixo para que um leitor antigo recuse em silêncio um
 * formato futuro em vez de o interpretar mal.
 */
const PREFIX = 'PONTOCAFE1'
const SEPARATOR = '|'
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

export type QrPayload = {
  colaboradorId: string
  codigo: string
}

export function buildQrPayload(colaboradorId: string, codigo: string): string {
  return [PREFIX, colaboradorId, codigo].join(SEPARATOR)
}

/**
 * Devolve null para tudo o que não seja um QR do Ponto Café — um QR de wi-fi,
 * um link, um código de outra instalação. A câmara lê o que estiver à frente e
 * cabe a quem chama distinguir "ainda não apontou para o certo" de "erro".
 */
export function parseQrPayload(raw: string): QrPayload | null {
  const partes = raw.trim().split(SEPARATOR)
  if (partes.length !== 3) return null
  const [prefixo, colaboradorId, codigo] = partes
  if (prefixo !== PREFIX) return null
  if (!UUID_PATTERN.test(colaboradorId ?? '')) return null

  const normalizado = normalizeAccessCode(codigo ?? '')
  if (!normalizado) return null

  return { colaboradorId: colaboradorId!.toLowerCase(), codigo: normalizado }
}
