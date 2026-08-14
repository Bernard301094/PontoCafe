package com.pontocafe.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pontocafe.app.camera.FaceEmbeddingEngine
import com.pontocafe.app.camera.FaceFrame
import com.pontocafe.app.data.Colaborador
import com.pontocafe.app.data.HorarioCafeResponse
import com.pontocafe.app.data.IniciarPausaResponse
import com.pontocafe.app.data.PontoCafeRepository
import com.pontocafe.app.data.SecureDeviceTokenStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


enum class ScanAction { INICIAR, FINALIZAR }

data class PontoCafeUiState(
    val deviceConfigured: Boolean = false,
    val carregando: Boolean = false,
    val colaboradores: List<Colaborador> = emptyList(),
    val busca: String = "",
    val selecionado: Colaborador? = null,
    val scanAction: ScanAction? = null,
    val scanning: Boolean = false,
    val needsAuthorization: Boolean = false,
    val authorizationPeriod: String? = null,
    val authorizationCode: String? = null,
    val horario: HorarioCafeResponse? = null,
    val pausaAtiva: IniciarPausaResponse? = null,
    val elapsedSeconds: Int = 0,
    val mensagem: String? = null,
    val erro: String? = null,
)

class PontoCafeViewModel(
    private val repository: PontoCafeRepository,
    private val tokenStore: SecureDeviceTokenStore,
    private val embeddingEngine: FaceEmbeddingEngine,
) : ViewModel() {

    var state by mutableStateOf(PontoCafeUiState(deviceConfigured = tokenStore.hasToken()))
        private set

    val faceModelReady: Boolean get() = embeddingEngine.isReady
    val faceModelName: String get() = embeddingEngine.modelName

    private var timerJob: Job? = null

    init {
        if (state.deviceConfigured) carregarColaboradores()
    }

    fun configurarDispositivo(token: String) {
        if (token.trim().length < 20) {
            state = state.copy(erro = "Token do dispositivo inválido.")
            return
        }
        tokenStore.save(token)
        state = state.copy(deviceConfigured = true, erro = null, mensagem = "Dispositivo configurado.")
        carregarColaboradores()
    }

    fun removerConfiguracao() {
        tokenStore.clear()
        timerJob?.cancel()
        state = PontoCafeUiState(deviceConfigured = false)
    }

    fun buscar(valor: String) {
        state = state.copy(busca = valor)
        carregarColaboradores(valor)
    }

    fun carregarColaboradores(busca: String = state.busca) {
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null)
            runCatching { repository.listarColaboradores(busca) }
                .onSuccess { state = state.copy(carregando = false, colaboradores = it) }
                .onFailure {
                    state = state.copy(
                        carregando = false,
                        erro = PontoCafeRepository.mensagemErro(it),
                    )
                }
        }
    }

    fun selecionar(colaborador: Colaborador) {
        state = state.copy(
            selecionado = colaborador,
            scanAction = null,
            scanning = false,
            needsAuthorization = false,
            authorizationPeriod = null,
            authorizationCode = null,
            erro = null,
            mensagem = null,
        )
    }

    fun voltarParaLista() {
        timerJob?.cancel()
        state = state.copy(
            selecionado = null,
            scanAction = null,
            scanning = false,
            needsAuthorization = false,
            authorizationPeriod = null,
            authorizationCode = null,
            pausaAtiva = null,
            elapsedSeconds = 0,
            erro = null,
            mensagem = null,
        )
    }

    fun prepararInicio() {
        if (state.selecionado == null) return
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = null)
            runCatching { repository.consultarHorario() }
                .onSuccess { horario ->
                    if (horario.dentroHorario) {
                        state = state.copy(
                            carregando = false,
                            horario = horario,
                            needsAuthorization = false,
                            authorizationPeriod = null,
                            authorizationCode = null,
                        )
                        iniciarLeituraInterna(ScanAction.INICIAR)
                    } else {
                        state = state.copy(
                            carregando = false,
                            horario = horario,
                            needsAuthorization = true,
                            scanAction = null,
                            scanning = false,
                            mensagem = "Fora do horário permitido. Solicite um código ao supervisor.",
                        )
                    }
                }
                .onFailure {
                    state = state.copy(
                        carregando = false,
                        erro = PontoCafeRepository.mensagemErro(it),
                    )
                }
        }
    }

    fun confirmarAutorizacao(periodo: String, codigo: String) {
        if (codigo.length != 6 || codigo.any { !it.isDigit() }) {
            state = state.copy(erro = "Informe o código de 6 dígitos.")
            return
        }
        if (periodo != "MANHA" && periodo != "TARDE") {
            state = state.copy(erro = "Selecione o período autorizado.")
            return
        }
        state = state.copy(
            needsAuthorization = false,
            authorizationPeriod = periodo,
            authorizationCode = codigo,
            erro = null,
            mensagem = "Autorização informada. Faça a verificação facial.",
        )
        iniciarLeituraInterna(ScanAction.INICIAR)
    }

    fun iniciarFinalizacao() {
        iniciarLeituraInterna(ScanAction.FINALIZAR)
    }

    private fun iniciarLeituraInterna(action: ScanAction) {
        state = state.copy(
            scanAction = action,
            scanning = true,
            erro = if (embeddingEngine.isReady) null else
                "Detecção e prova de vida estão prontas, mas o modelo de reconhecimento 1:1 ainda não foi instalado.",
        )
    }

    fun cancelarLeitura() {
        state = state.copy(scanning = false, scanAction = null, erro = null)
    }

    fun processarFrame(frame: FaceFrame) {
        val colaborador = state.selecionado ?: return
        val action = state.scanAction ?: return
        if (!state.scanning || !embeddingEngine.isReady) return

        viewModelScope.launch {
            state = state.copy(carregando = true, scanning = false, erro = null)
            try {
                val embedding = embeddingEngine.embed(frame)
                val verification = repository.verificar(colaborador.id, embedding)
                if (!verification.reconhecido || verification.verificacaoToken.isNullOrBlank()) {
                    state = state.copy(carregando = false, erro = "Rosto não reconhecido. Tente novamente.")
                    return@launch
                }

                when (action) {
                    ScanAction.INICIAR -> {
                        val pausa = repository.iniciar(
                            colaboradorId = colaborador.id,
                            verificacaoToken = verification.verificacaoToken,
                            periodo = state.authorizationPeriod,
                            codigoAutorizacao = state.authorizationCode,
                        )
                        state = state.copy(
                            carregando = false,
                            scanAction = null,
                            authorizationPeriod = null,
                            authorizationCode = null,
                            pausaAtiva = pausa,
                            elapsedSeconds = 0,
                            mensagem = if (pausa.foraHorario) {
                                "Pausa iniciada com autorização do supervisor."
                            } else {
                                "Pausa iniciada."
                            },
                        )
                        iniciarCronometro()
                    }
                    ScanAction.FINALIZAR -> {
                        val finalizada = repository.finalizar(colaborador.id, verification.verificacaoToken)
                        timerJob?.cancel()
                        state = state.copy(
                            carregando = false,
                            scanAction = null,
                            pausaAtiva = null,
                            elapsedSeconds = finalizada.duracaoSegundos,
                            mensagem = if (finalizada.excedeuLimite) {
                                "Pausa finalizada: ${formatarTempo(finalizada.duracaoSegundos)}. Limite excedido."
                            } else {
                                "Pausa finalizada: ${formatarTempo(finalizada.duracaoSegundos)}."
                            },
                        )
                    }
                }
            } catch (error: Throwable) {
                state = state.copy(
                    carregando = false,
                    scanAction = null,
                    authorizationPeriod = null,
                    authorizationCode = null,
                    erro = PontoCafeRepository.mensagemErro(error),
                )
            }
        }
    }

    fun limparMensagem() {
        state = state.copy(mensagem = null, erro = null)
    }

    private fun iniciarCronometro() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (state.pausaAtiva != null) {
                delay(1_000)
                state = state.copy(elapsedSeconds = state.elapsedSeconds + 1)
            }
        }
    }

    private fun formatarTempo(seconds: Int): String = "%02d:%02d".format(seconds / 60, seconds % 60)
}
