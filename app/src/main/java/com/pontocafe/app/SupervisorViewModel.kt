package com.pontocafe.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pontocafe.app.camera.FaceEmbeddingEngine
import com.pontocafe.app.camera.FaceFrame
import com.pontocafe.app.data.Colaborador
import com.pontocafe.app.data.PausaSupervisor
import com.pontocafe.app.data.SupervisorRepository
import kotlinx.coroutines.launch
import kotlin.math.sqrt


enum class SupervisorDestination {
    LOGIN,
    AO_VIVO,
    HISTORICO,
    COLABORADORES,
    NOVO_COLABORADOR,
    BIOMETRIA,
}

data class SupervisorUiState(
    val destination: SupervisorDestination = SupervisorDestination.LOGIN,
    val carregando: Boolean = false,
    val pausasAtivas: List<PausaSupervisor> = emptyList(),
    val historico: List<PausaSupervisor> = emptyList(),
    val colaboradores: List<Colaborador> = emptyList(),
    val colaboradorSelecionado: Colaborador? = null,
    val biometricScanCycle: Int = 0,
    val biometricStepIndex: Int = 0,
    val biometricSamplesCaptured: Int = 0,
    val sessaoAdministrativa: Boolean = false,
    val mensagem: String? = null,
    val erro: String? = null,
)

class SupervisorViewModel(
    private val repository: SupervisorRepository,
    private val embeddingEngine: FaceEmbeddingEngine,
) : ViewModel() {
    var state by mutableStateOf(
        SupervisorUiState(
            destination = if (repository.hasSession()) SupervisorDestination.AO_VIVO else SupervisorDestination.LOGIN,
            sessaoAdministrativa = repository.usingAdminSession(),
        ),
    )
        private set

    private val biometricSamples = mutableListOf<FloatArray>()

    val faceModelReady: Boolean get() = embeddingEngine.isReady

    init {
        if (repository.hasSession()) atualizarAoVivo()
    }

    fun prepararEntrada() {
        if (state.destination == SupervisorDestination.LOGIN && repository.hasSession()) {
            state = state.copy(
                sessaoAdministrativa = repository.usingAdminSession(),
                erro = null,
                mensagem = null,
            )
            atualizarAoVivo()
        }
    }

    fun login(email: String, senha: String) {
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = null)
            runCatching { repository.signIn(email.trim().lowercase(), senha) }
                .onSuccess {
                    state = state.copy(sessaoAdministrativa = false)
                    atualizarAoVivoInterno()
                }
                .onFailure {
                    state = state.copy(carregando = false, erro = SupervisorRepository.message(it))
                }
        }
    }

    fun atualizarAoVivo() {
        viewModelScope.launch { atualizarAoVivoInterno() }
    }

    private suspend fun atualizarAoVivoInterno() {
        runCatching {
            val pausas = repository.pausasAtivas()
            val colaboradores = repository.collaborators()
            pausas to colaboradores
        }
            .onSuccess { (pausas, colaboradores) ->
                state = state.copy(
                    destination = SupervisorDestination.AO_VIVO,
                    carregando = false,
                    pausasAtivas = pausas,
                    colaboradores = colaboradores,
                    sessaoAdministrativa = repository.usingAdminSession(),
                    erro = null,
                )
            }
            .onFailure {
                repository.clearActiveSession()
                state = SupervisorUiState(
                    destination = SupervisorDestination.LOGIN,
                    erro = "Sua sessão expirou ou não possui acesso de supervisor.",
                )
            }
    }

    fun abrirHistorico() {
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = null)
            runCatching { repository.historico() }
                .onSuccess {
                    state = state.copy(
                        destination = SupervisorDestination.HISTORICO,
                        carregando = false,
                        historico = it,
                    )
                }
                .onFailure {
                    state = state.copy(carregando = false, erro = SupervisorRepository.message(it))
                }
        }
    }

    fun abrirColaboradores() {
        biometricSamples.clear()
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = null)
            runCatching { repository.collaborators() }
                .onSuccess {
                    state = state.copy(
                        destination = SupervisorDestination.COLABORADORES,
                        carregando = false,
                        colaboradores = it,
                        colaboradorSelecionado = null,
                        biometricStepIndex = 0,
                        biometricSamplesCaptured = 0,
                    )
                }
                .onFailure {
                    state = state.copy(carregando = false, erro = SupervisorRepository.message(it))
                }
        }
    }

    fun abrirNovoColaborador() {
        state = state.copy(
            destination = SupervisorDestination.NOVO_COLABORADOR,
            erro = null,
            mensagem = null,
        )
    }

    fun criarColaborador(nome: String, setor: String, turno: String) {
        if (nome.trim().length < 2) {
            state = state.copy(erro = "Informe o nome do colaborador.")
            return
        }

        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = null)
            runCatching { repository.createCollaborator(nome, setor, turno) }
                .onSuccess { colaborador ->
                    biometricSamples.clear()
                    state = state.copy(
                        carregando = false,
                        destination = SupervisorDestination.BIOMETRIA,
                        colaboradorSelecionado = colaborador,
                        biometricScanCycle = state.biometricScanCycle + 1,
                        biometricStepIndex = 0,
                        biometricSamplesCaptured = 0,
                        mensagem = "Colaborador cadastrado. Agora registre o rosto em 5 etapas.",
                    )
                }
                .onFailure {
                    state = state.copy(carregando = false, erro = SupervisorRepository.message(it))
                }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    @Deprecated("Compatibilidade interna: matrícula não é utilizada")
    fun criarColaborador(matricula: String, nome: String, setor: String, turno: String) {
        criarColaborador(nome, setor, turno)
    }

    fun cadastrarOuAtualizarRosto(colaborador: Colaborador) {
        biometricSamples.clear()
        state = state.copy(
            destination = SupervisorDestination.BIOMETRIA,
            colaboradorSelecionado = colaborador,
            biometricScanCycle = state.biometricScanCycle + 1,
            biometricStepIndex = 0,
            biometricSamplesCaptured = 0,
            erro = null,
            mensagem = null,
        )
    }

    fun processarAmostraBiometrica(frame: FaceFrame) {
        val colaborador = state.colaboradorSelecionado ?: return
        if (state.carregando) return
        if (!embeddingEngine.isReady) {
            state = state.copy(erro = "O modelo de reconhecimento facial ainda não está instalado neste APK.")
            return
        }

        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = "Processando amostra facial...")
            try {
                val embedding = embeddingEngine.embed(frame)
                require(embedding.isNotEmpty() && embedding.all { it.isFinite() }) {
                    "A amostra facial gerada é inválida."
                }
                biometricSamples += embedding.copyOf()

                val captured = biometricSamples.size
                if (captured < BIOMETRIC_SAMPLE_COUNT) {
                    state = state.copy(
                        carregando = false,
                        biometricStepIndex = captured,
                        biometricSamplesCaptured = captured,
                        biometricScanCycle = state.biometricScanCycle + 1,
                        mensagem = "Amostra $captured de $BIOMETRIC_SAMPLE_COUNT capturada.",
                        erro = null,
                    )
                    return@launch
                }

                val combined = combineBiometricSamples(biometricSamples)
                repository.saveBiometric(
                    collaboratorId = colaborador.id,
                    embedding = combined,
                    model = embeddingEngine.modelName,
                    modelVersion = embeddingEngine.modelVersion,
                )

                val colaboradores = repository.collaborators()
                biometricSamples.clear()
                state = state.copy(
                    carregando = false,
                    destination = SupervisorDestination.COLABORADORES,
                    colaboradores = colaboradores,
                    colaboradorSelecionado = null,
                    biometricStepIndex = 0,
                    biometricSamplesCaptured = 0,
                    mensagem = "Rosto de ${colaborador.nome} cadastrado com sucesso.",
                    erro = null,
                )
            } catch (error: Throwable) {
                val completedSequence = biometricSamples.size >= BIOMETRIC_SAMPLE_COUNT
                if (completedSequence) biometricSamples.clear()
                val captured = biometricSamples.size.coerceAtMost(BIOMETRIC_SAMPLE_COUNT - 1)
                state = state.copy(
                    carregando = false,
                    biometricStepIndex = captured,
                    biometricSamplesCaptured = captured,
                    biometricScanCycle = state.biometricScanCycle + 1,
                    mensagem = null,
                    erro = SupervisorRepository.message(error),
                )
            }
        }
    }

    fun excluirRosto(colaborador: Colaborador) {
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = null)
            runCatching { repository.deleteBiometric(colaborador.id) }
                .onSuccess {
                    val atualizados = repository.collaborators()
                    state = state.copy(
                        carregando = false,
                        colaboradores = atualizados,
                        mensagem = "Rosto de ${colaborador.nome} excluído.",
                    )
                }
                .onFailure {
                    state = state.copy(carregando = false, erro = SupervisorRepository.message(it))
                }
        }
    }

    fun excluirColaborador(colaborador: Colaborador) {
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = null)
            runCatching { repository.deleteCollaborator(colaborador.id) }
                .onSuccess {
                    val atualizados = repository.collaborators()
                    state = state.copy(
                        carregando = false,
                        colaboradores = atualizados,
                        mensagem = "${colaborador.nome} foi removido dos colaboradores ativos e sua biometria foi excluída.",
                    )
                }
                .onFailure {
                    state = state.copy(carregando = false, erro = SupervisorRepository.message(it))
                }
        }
    }

    fun voltarColaboradores() {
        biometricSamples.clear()
        state = state.copy(
            destination = SupervisorDestination.COLABORADORES,
            colaboradorSelecionado = null,
            biometricStepIndex = 0,
            biometricSamplesCaptured = 0,
            erro = null,
        )
    }

    fun voltarAoVivo() {
        biometricSamples.clear()
        state = state.copy(destination = SupervisorDestination.AO_VIVO, erro = null, mensagem = null)
        atualizarAoVivo()
    }

    fun sair() {
        biometricSamples.clear()
        viewModelScope.launch {
            repository.signOutSupervisor()
            state = SupervisorUiState(destination = SupervisorDestination.LOGIN)
        }
    }

    fun limparAviso() {
        state = state.copy(erro = null, mensagem = null)
    }

    fun formatarTempo(segundos: Int): String = "%02d:%02d".format(segundos / 60, segundos % 60)

    private fun combineBiometricSamples(samples: List<FloatArray>): FloatArray {
        require(samples.size == BIOMETRIC_SAMPLE_COUNT) { "São necessárias 5 amostras faciais." }
        val dimension = samples.first().size
        require(dimension > 0 && samples.all { it.size == dimension }) { "Amostras faciais incompatíveis." }

        val combined = FloatArray(dimension)
        samples.forEach { sample ->
            for (index in 0 until dimension) combined[index] += sample[index]
        }
        for (index in combined.indices) combined[index] /= samples.size.toFloat()

        var sumSquares = 0.0
        combined.forEach { value -> sumSquares += value * value }
        val norm = sqrt(sumSquares).toFloat()
        require(norm > 1e-12f) { "Não foi possível consolidar as amostras faciais." }
        for (index in combined.indices) combined[index] /= norm
        return combined
    }

    companion object {
        private const val BIOMETRIC_SAMPLE_COUNT = 5
    }
}

class SupervisorViewModelFactory(private val creator: () -> SupervisorViewModel) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(SupervisorViewModel::class.java))
        return creator() as T
    }
}
