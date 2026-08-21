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
import com.pontocafe.app.data.SupervisorReportResponse
import com.pontocafe.app.data.SupervisorRepository
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.launch
import kotlin.math.sqrt


enum class SupervisorDestination {
    LOGIN,
    AO_VIVO,
    HISTORICO,
    COLABORADORES,
    NOVO_COLABORADOR,
    BIOMETRIA,
    AUTORIZACAO,
    RELATORIOS,
}

data class SupervisorUiState(
    val destination: SupervisorDestination = SupervisorDestination.LOGIN,
    val carregando: Boolean = false,
    val pausasAtivas: List<PausaSupervisor> = emptyList(),
    val ultimoRetorno: PausaSupervisor? = null,
    val historico: List<PausaSupervisor> = emptyList(),
    val historicoData: String? = null,
    val colaboradores: List<Colaborador> = emptyList(),
    val relatorio: SupervisorReportResponse? = null,
    val relatorioAnterior: SupervisorReportResponse? = null,
    val relatorioInicio: String? = null,
    val relatorioFim: String? = null,
    val authorizationId: String? = null,
    val authorizationEmployeeName: String? = null,
    val authorizationPeriod: String? = null,
    val authorizationExpiresSeconds: Int? = null,
    val colaboradorSelecionado: Colaborador? = null,
    val biometricScanCycle: Int = 0,
    val biometricStepIndex: Int = 0,
    val biometricSamplesCaptured: Int = 0,
    val sessaoAdministrativa: Boolean = false,
    val ultimaAtualizacaoAoVivoEmMillis: Long? = null,
    val conexaoAoVivoOk: Boolean = true,
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
    private var atualizacaoAoVivoEmAndamento = false
    private var atualizacaoPausasEmAndamento = false
    private var atualizacaoRetornoEmAndamento = false

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
        if (atualizacaoAoVivoEmAndamento || atualizacaoPausasEmAndamento) return
        viewModelScope.launch { atualizarAoVivoInterno() }
    }

    fun atualizarPausasAoVivoSilencioso() {
        if (
            state.destination != SupervisorDestination.AO_VIVO ||
            atualizacaoAoVivoEmAndamento ||
            atualizacaoPausasEmAndamento
        ) return

        viewModelScope.launch {
            atualizacaoPausasEmAndamento = true
            try {
                runCatching { repository.pausasAtivas() }
                    .onSuccess { pausas ->
                        state = state.copy(
                            pausasAtivas = pausas,
                            ultimaAtualizacaoAoVivoEmMillis = System.currentTimeMillis(),
                            conexaoAoVivoOk = true,
                        )
                    }
                    .onFailure { error ->
                        if (SupervisorRepository.isAuthFailure(error)) {
                            repository.clearActiveSession()
                            state = SupervisorUiState(
                                destination = SupervisorDestination.LOGIN,
                                erro = "Sua sessão expirou ou não possui acesso de supervisor.",
                                conexaoAoVivoOk = false,
                            )
                        } else {
                            state = state.copy(conexaoAoVivoOk = false)
                        }
                    }
            } finally {
                atualizacaoPausasEmAndamento = false
            }
        }
    }

    fun atualizarUltimoRetornoSilencioso() {
        if (
            state.destination != SupervisorDestination.AO_VIVO ||
            atualizacaoAoVivoEmAndamento ||
            atualizacaoRetornoEmAndamento
        ) return

        viewModelScope.launch {
            atualizacaoRetornoEmAndamento = true
            try {
                runCatching { repository.historico(LocalDate.now().toString()) }
                    .onSuccess { historico ->
                        state = state.copy(ultimoRetorno = historico.ultimoRetorno())
                    }
                    .onFailure { error ->
                        if (SupervisorRepository.isAuthFailure(error)) {
                            repository.clearActiveSession()
                            state = SupervisorUiState(
                                destination = SupervisorDestination.LOGIN,
                                erro = "Sua sessão expirou ou não possui acesso de supervisor.",
                                conexaoAoVivoOk = false,
                            )
                        }
                    }
            } finally {
                atualizacaoRetornoEmAndamento = false
            }
        }
    }

    private suspend fun atualizarAoVivoInterno() {
        if (atualizacaoAoVivoEmAndamento || atualizacaoPausasEmAndamento) return
        atualizacaoAoVivoEmAndamento = true
        try {
            runCatching {
                val pausas = repository.pausasAtivas()
                val colaboradores = repository.collaborators()
                val historicoHoje = repository.historico(LocalDate.now().toString())
                Triple(pausas, colaboradores, historicoHoje.ultimoRetorno())
            }
                .onSuccess { (pausas, colaboradores, ultimoRetorno) ->
                    state = state.copy(
                        destination = SupervisorDestination.AO_VIVO,
                        carregando = false,
                        pausasAtivas = pausas,
                        ultimoRetorno = ultimoRetorno,
                        colaboradores = colaboradores,
                        sessaoAdministrativa = repository.usingAdminSession(),
                        ultimaAtualizacaoAoVivoEmMillis = System.currentTimeMillis(),
                        conexaoAoVivoOk = true,
                        authorizationId = null,
                        authorizationEmployeeName = null,
                        authorizationPeriod = null,
                        authorizationExpiresSeconds = null,
                        erro = null,
                    )
                }
                .onFailure { error ->
                    if (SupervisorRepository.isAuthFailure(error)) {
                        repository.clearActiveSession()
                        state = SupervisorUiState(
                            destination = SupervisorDestination.LOGIN,
                            erro = "Sua sessão expirou ou não possui acesso de supervisor.",
                            conexaoAoVivoOk = false,
                        )
                    } else {
                        state = state.copy(
                            destination = SupervisorDestination.AO_VIVO,
                            carregando = false,
                            conexaoAoVivoOk = false,
                            erro = "Sem conexão com o servidor. Sua sessão foi preservada e os últimos dados continuam disponíveis.",
                        )
                    }
                }
        } finally {
            atualizacaoAoVivoEmAndamento = false
        }
    }

    fun abrirHistorico(data: String = LocalDate.now().toString()) {
        viewModelScope.launch {
            state = state.copy(
                destination = SupervisorDestination.HISTORICO,
                carregando = true,
                historicoData = data,
                erro = null,
                mensagem = null,
            )
            runCatching { repository.historico(data) }
                .onSuccess { historico ->
                    state = state.copy(
                        destination = SupervisorDestination.HISTORICO,
                        carregando = false,
                        historico = historico,
                        historicoData = data,
                    )
                }
                .onFailure {
                    state = state.copy(carregando = false, erro = SupervisorRepository.message(it))
                }
        }
    }

    fun abrirAutorizacao() {
        viewModelScope.launch {
            state = state.copy(
                carregando = true,
                erro = null,
                mensagem = null,
                authorizationId = null,
                authorizationEmployeeName = null,
                authorizationPeriod = null,
                authorizationExpiresSeconds = null,
            )
            runCatching { repository.collaborators() }
                .onSuccess { colaboradores ->
                    state = state.copy(
                        destination = SupervisorDestination.AUTORIZACAO,
                        carregando = false,
                        colaboradores = colaboradores,
                    )
                }
                .onFailure {
                    state = state.copy(carregando = false, erro = SupervisorRepository.message(it))
                }
        }
    }

    fun autorizarPausa(colaborador: Colaborador, motivo: String) {
        if (motivo.trim().length < 2) {
            state = state.copy(erro = "Informe o motivo da liberação.")
            return
        }

        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = null, authorizationId = null)
            runCatching { repository.createAuthorization(colaborador.id, motivo) }
                .onSuccess { authorization ->
                    state = state.copy(
                        carregando = false,
                        authorizationId = authorization.id,
                        authorizationEmployeeName = colaborador.nome,
                        authorizationPeriod = authorization.periodo,
                        authorizationExpiresSeconds = authorization.expiraEmSegundos,
                        mensagem = null,
                        erro = null,
                    )
                }
                .onFailure {
                    state = state.copy(carregando = false, erro = SupervisorRepository.message(it))
                }
        }
    }

    fun cancelarAutorizacao(colaborador: Colaborador) {
        if (state.authorizationId == null || state.carregando) return
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = null)
            runCatching { repository.cancelAuthorization(colaborador.id) }
                .onSuccess {
                    state = state.copy(
                        carregando = false,
                        authorizationId = null,
                        authorizationEmployeeName = null,
                        authorizationPeriod = null,
                        authorizationExpiresSeconds = null,
                        mensagem = "Liberação de ${colaborador.nome} cancelada.",
                        erro = null,
                    )
                }
                .onFailure {
                    state = state.copy(carregando = false, erro = SupervisorRepository.message(it))
                }
        }
    }

    fun limparAutorizacao() {
        state = state.copy(
            authorizationId = null,
            authorizationEmployeeName = null,
            authorizationPeriod = null,
            authorizationExpiresSeconds = null,
            mensagem = null,
            erro = null,
        )
    }

    fun abrirRelatorios(dias: Int = 7) {
        val fim = LocalDate.now()
        val inicio = fim.minusDays((dias.coerceAtLeast(1) - 1).toLong())
        carregarRelatorio(inicio.toString(), fim.toString())
    }

    fun carregarRelatorio(inicio: String, fim: String) {
        viewModelScope.launch {
            state = state.copy(
                destination = SupervisorDestination.RELATORIOS,
                carregando = true,
                relatorioInicio = inicio,
                relatorioFim = fim,
                relatorioAnterior = null,
                erro = null,
                mensagem = null,
            )

            val inicioDate = runCatching { LocalDate.parse(inicio) }.getOrNull()
            val fimDate = runCatching { LocalDate.parse(fim) }.getOrNull()

            runCatching {
                val atual = repository.report(inicio, fim)
                val anterior = if (inicioDate != null && fimDate != null && !fimDate.isBefore(inicioDate)) {
                    val dias = (ChronoUnit.DAYS.between(inicioDate, fimDate) + 1L).coerceAtLeast(1L)
                    val fimAnterior = inicioDate.minusDays(1)
                    val inicioAnterior = fimAnterior.minusDays(dias - 1L)
                    runCatching { repository.report(inicioAnterior.toString(), fimAnterior.toString()) }.getOrNull()
                } else {
                    null
                }
                atual to anterior
            }
                .onSuccess { (atual, anterior) ->
                    state = state.copy(
                        destination = SupervisorDestination.RELATORIOS,
                        carregando = false,
                        relatorio = atual,
                        relatorioAnterior = anterior,
                    )
                }
                .onFailure {
                    state = state.copy(carregando = false, erro = SupervisorRepository.message(it))
                }
        }
    }

    suspend fun baixarRelatorioCsv(): ByteArray {
        val inicio = state.relatorioInicio ?: LocalDate.now().toString()
        val fim = state.relatorioFim ?: inicio
        return repository.reportCsv(inicio, fim)
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

                val samplesForValidation = biometricSamples.map { it.copyOf() }
                val combined = combineBiometricSamples(samplesForValidation)
                repository.saveBiometric(
                    collaboratorId = colaborador.id,
                    embedding = combined,
                    model = embeddingEngine.modelName,
                    modelVersion = embeddingEngine.modelVersion,
                    samples = samplesForValidation,
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
                    mensagem = "Rosto de ${colaborador.nome} cadastrado com 5 amostras e verificação de duplicidade.",
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
                    val base = runCatching { repository.collaborators() }
                        .getOrElse { state.colaboradores }
                    val atualizados = base.map { item ->
                        if (item.id == colaborador.id) item.copy(rostoCadastrado = false) else item
                    }
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
                    state = state.copy(
                        carregando = false,
                        colaboradores = state.colaboradores.filterNot { item -> item.id == colaborador.id },
                        mensagem = "${colaborador.nome} foi removido dos colaboradores ativos e sua biometria foi excluída.",
                    )
                    runCatching { repository.collaborators() }
                        .onSuccess { refreshed ->
                            state = state.copy(
                                colaboradores = refreshed.filterNot { item -> item.id == colaborador.id },
                            )
                        }
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
        state = state.copy(
            destination = SupervisorDestination.AO_VIVO,
            authorizationId = null,
            authorizationEmployeeName = null,
            authorizationPeriod = null,
            authorizationExpiresSeconds = null,
            erro = null,
            mensagem = null,
        )
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

    private fun List<PausaSupervisor>.ultimoRetorno(): PausaSupervisor? =
        asSequence()
            .filter { !it.fimLocal.isNullOrBlank() }
            .maxWithOrNull(
                compareBy<PausaSupervisor> { it.data.orEmpty() }
                    .thenBy { it.fimLocal.orEmpty() }
                    .thenBy { it.inicioLocal },
            )

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
