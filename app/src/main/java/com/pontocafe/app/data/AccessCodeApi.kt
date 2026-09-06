package com.pontocafe.app.data

/**
 * Contratos do código de acesso ao café, partilhados por Admin e Supervisor.
 *
 * As duas áreas emitem exatamente o mesmo passe, contra as mesmas rotas
 * (`/admin/codigos` e `/supervisor/codigos`, que montam o mesmo módulo no
 * Worker). Manter um só conjunto de modelos evita que uma das telas passe a
 * mostrar um campo que a outra não conhece.
 */

data class CreateAccessCodeRequest(
    val colaboradorId: String,
    val motivo: String? = null,
)

data class AccessCodeCreatedResponse(
    val id: String,
    /** O código em claro. Só existe nesta resposta e na lista autenticada. */
    val codigo: String,
    val codigoFormatado: String,
    val colaboradorId: String,
    val colaboradorNome: String,
    val setor: String? = null,
    val turno: String? = null,
    val criadoEm: String? = null,
    val expiraEm: String? = null,
    val expiraEmSegundos: Int,
    val carenciaSegundos: Int = 60,
    val limiteSegundos: Int? = null,
    val aviso: String? = null,
)

/**
 * `estado` vem do servidor e vale mais do que qualquer conta feita aqui:
 * `AGUARDANDO_SAIDA`, `EXPIRADO` ou `EM_PAUSA`. Um código EM_PAUSA nunca expira
 * — negar o retorno deixaria a pausa aberta para sempre.
 */
data class AccessCodeItem(
    val id: String,
    val codigo: String,
    val codigoFormatado: String,
    val estado: String,
    val colaboradorId: String,
    val nome: String,
    val setor: String? = null,
    val turno: String? = null,
    val emitidoPor: String? = null,
    val motivo: String? = null,
    val criadoEm: String? = null,
    val expiraEm: String? = null,
    val expiraEmSegundos: Int = 0,
    val saidaEm: String? = null,
    val pausaId: String? = null,
    val limiteSegundos: Int? = null,
    val carenciaSegundos: Int? = null,
)

data class AccessCodesResponse(
    val codigos: List<AccessCodeItem>,
    val validadeSegundos: Int = 900,
    val carenciaSegundos: Int = 60,
)

data class CancelAccessCodeRequest(val colaboradorId: String)

data class CancelAccessCodeResponse(
    val ok: Boolean,
    val cancelado: Boolean,
    val id: String,
)
