package com.pontocafe.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pontocafe.app.camera.FaceEmbeddingEngine
import com.pontocafe.app.camera.FaceFrame
import com.pontocafe.app.data.FinalizarPausaResponse
import com.pontocafe.app.data.IdentificarBiometriaResponse
import com.pontocafe.app.data.IniciarPausaResponse
import com.pontocafe.app.data.PontoCafeRepository
import com.pontocafe.app.data.SecureDeviceTokenStore
import kotlinx.coroutines.launch


enum class TipoComprovantePonto { INICIO, RETORNO }

data class ComprovantePonto(
    val tipo: TipoComprovantePonto,
    val nome: String,
    val horarioRegistrado: String,
    val retornoAte: String? = null,
    val duracaoSegundos: Int? = null,
    val limiteSegundos: Int,
    val excedeuLimite: Boolean = false,
    val foraHorario: Boolean = false,
)

data class PontoCafeUiState(
    val deviceConfigured: Boolean = false,
    val carregando: Boolean = false,
    val scanning: Boolean = false,
    val scanCycle: Int = 0,
    val identificacao: IdentificarBiometriaResponse? = null,
    val needsAuthorization: Boolean = false,
    val comprovante: ComprovantePonto? = null,
    val mensagem: String? = null,
    val erro: String? = null,
)

class PontoCafeViewModel(
    private val repository: PontoCafeRepository,
    private val tokenStore: SecureDeviceTokenStore,
    private val embeddingEngine: FaceEmbeddingEngine,
) : ViewModel() {

    var state by mutableStateOf(
        PontoCafeUiState(
            deviceConfigured = tokenStore.hasToken(),
            scanning = tokenStore.hasToken(),
        ),
    )
        private set

    val faceModelReady: Boolean get() = embeddingEngine.isReady
    val faceModelName: String get() = embeddingEngine.modelName

    fun configurarDispositivo(token: String) {
        if (token.trim().length < 20) {
            state = state.copy(erro = "Token do dispositivo inválido.")
            return
        }
        tokenStore.save(token)
        state = PontoCafeUiState(
            deviceConfigured = true,
            scanning = true,
            scanCycle = state.scanCycle + 1,
            mensagem = "Dispositivo configurado com sucesso.",
        )
    }

    fun removerConfiguracao() {
        tokenStore.clear()
        state = PontoCafeUiState(deviceConfigured = false)
    }

    fun ativarCamera() {
        state = state.copy(
            scanning = true,
            scanCycle = state.scanCycle + 1,
            identificacao = null,
            needsAuthorization = false,
            comprovante = null,
            mensagem = null,
            erro = null,
        )
    }

    fun processarFrame(frame: FaceFrame) {
        if (!state.scanning || state.carregando) return
        if (!embeddingEngine.isReady) {
            state = state.copy(
                scanning = false,
                erro = "O módulo de reconhecimento facial ainda não está instalado neste dispositivo.",
            )
            return
        }

        viewModelScope.launch {
            state = state.copy(carregando = true, scanning = false, erro = null, mensagem = null)
            try {
                val embedding = embeddingEngine.embed(frame)
                val identificacao = repository.identificar(embedding)
                if (!identificacao.reconhecido || identificacao.colaborador == null || identificacao.verificacaoToken.isNullOrBlank()) {
                    state = state.copy(
                        carregando = false,
                        scanning = true,
                        scanCycle = state.scanCycle + 1,
                        identificacao = null,
                        erro = identificacao.mensagem
                            ?: "Não foi possível reconhecer você. Posicione o rosto novamente.",
                    )
                    return@launch
                }

                state = state.copy(
                    carregando = false,
                    identificacao = identificacao,
                    scanning = false,
                    erro = null,
                )
            } catch (error: Throwable) {
                state = state.copy(
                    carregando = false,
                    scanning = true,
                    scanCycle = state.scanCycle + 1,
                    identificacao = null,
                    erro = PontoCafeRepository.mensagemErro(error),
                )
            }
        }
    }

    fun confirmarIdentidade() {
        val identificacao = state.identificacao ?: return
        val colaborador = identificacao.colaborador ?: return
        val token = identificacao.verificacaoToken ?: return

        if (identificacao.acaoSugerida == "FINALIZAR") {
            finalizarPausa(colaborador.id, colaborador.nome, token)
            return
        }

        if (identificacao.dentroHorario == true) {
            iniciarPausa(
                colaboradorId = colaborador.id,
                nome = colaborador.nome,
                verificacaoToken = token,
            )
        } else {
            state = state.copy(
                needsAuthorization = true,
                mensagem = "Fora do horário permitido. Informe o código temporário gerado pelo supervisor.",
                erro = null,
            )
        }
    }

    fun rejeitarIdentidade() {
        state = state.copy(
            identificacao = null,
            scanning = true,
            scanCycle = state.scanCycle + 1,
            needsAuthorization = false,
            mensagem = "Tudo bem. Vamos tentar identificar novamente.",
            erro = null,
        )
    }

    fun confirmarAutorizacao(periodo: String, codigo: String) {
        val identificacao = state.identificacao ?: return
        val colaborador = identificacao.colaborador ?: return
        val token = identificacao.verificacaoToken ?: return

        if (codigo.length != 6 || codigo.any { !it.isDigit() }) {
            state = state.copy(erro = "Informe o código de 6 dígitos.")
            return
        }
        if (periodo != "MANHA" && periodo != "TARDE") {
            state = state.copy(erro = "Selecione o período autorizado.")
            return
        }

        iniciarPausa(
            colaboradorId = colaborador.id,
            nome = colaborador.nome,
            verificacaoToken = token,
            periodo = periodo,
            codigoAutorizacao = codigo,
        )
    }

    fun cancelarAutorizacao() {
        rejeitarIdentidade()
    }

    fun concluirComprovante() {
        ativarCamera()
    }

    fun limparMensagem() {
        state = state.copy(mensagem = null, erro = null)
    }

    private fun iniciarPausa(
        colaboradorId: String,
        nome: String,
        verificacaoToken: String,
        periodo: String? = null,
        codigoAutorizacao: String? = null,
    ) {
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null)
            runCatching {
                repository.iniciar(
                    colaboradorId = colaboradorId,
                    verificacaoToken = verificacaoToken,
                    periodo = periodo,
                    codigoAutorizacao = codigoAutorizacao,
                )
            }.onSuccess { pausa ->
                state = state.copy(
                    carregando = false,
                    needsAuthorization = false,
                    identificacao = null,
                    comprovante = comprovanteInicio(nome, pausa),
                    mensagem = null,
                    erro = null,
                )
            }.onFailure { error ->
                state = state.copy(
                    carregando = false,
                    erro = PontoCafeRepository.mensagemErro(error),
                )
            }
        }
    }

    private fun finalizarPausa(colaboradorId: String, nome: String, verificacaoToken: String) {
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null)
            runCatching { repository.finalizar(colaboradorId, verificacaoToken) }
                .onSuccess { pausa ->
                    state = state.copy(
                        carregando = false,
                        identificacao = null,
                        comprovante = comprovanteRetorno(nome, pausa),
                        mensagem = null,
                        erro = null,
                    )
                }
                .onFailure { error ->
                    state = state.copy(
                        carregando = false,
                        erro = PontoCafeRepository.mensagemErro(error),
                    )
                }
        }
    }

    private fun comprovanteInicio(nome: String, pausa: IniciarPausaResponse) = ComprovantePonto(
        tipo = TipoComprovantePonto.INICIO,
        nome = nome,
        horarioRegistrado = pausa.inicioLocal,
        retornoAte = pausa.retornoAteLocal,
        limiteSegundos = pausa.limiteSegundos,
        foraHorario = pausa.foraHorario,
    )

    private fun comprovanteRetorno(nome: String, pausa: FinalizarPausaResponse) = ComprovantePonto(
        tipo = TipoComprovantePonto.RETORNO,
        nome = nome,
        horarioRegistrado = pausa.fimLocal,
        duracaoSegundos = pausa.duracaoSegundos,
        limiteSegundos = pausa.limiteSegundos,
        excedeuLimite = pausa.excedeuLimite,
    )

    fun formatarTempo(segundos: Int): String = "%02d:%02d".format(segundos / 60, segundos % 60)
}
