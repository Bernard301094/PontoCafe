package com.pontocafe.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pontocafe.app.avatar.AvatarImageOptimizer
import com.pontocafe.app.avatar.EnrollmentAvatarCaptureSession
import com.pontocafe.app.avatar.EnrollmentAvatarUploadStatus
import com.pontocafe.app.camera.FaceEmbeddingEngine
import com.pontocafe.app.camera.FaceFrame
import com.pontocafe.app.data.BiometricTemplateAggregator
import com.pontocafe.app.data.Colaborador
import com.pontocafe.app.data.FaceEmbeddingIntegrity
import com.pontocafe.app.data.LocalFaceMatcher
import com.pontocafe.app.data.OperationalAlertHistoryStore
import com.pontocafe.app.data.PausaSupervisor
import com.pontocafe.app.data.SecureFaceCatalogStore
import com.pontocafe.app.data.SupervisorReportResponse
import com.pontocafe.app.data.SupervisorRepository
import com.pontocafe.app.notifications.SupervisorAlertNotifier
import com.pontocafe.app.ui.SUPERVISOR_LIVE_ALERT_CRITICAL_THRESHOLD_SECONDS
import com.pontocafe.app.ui.SUPERVISOR_LIVE_ALERT_WARNING_THRESHOLD_SECONDS
import com.pontocafe.app.ui.SupervisorLiveAlertType
import com.pontocafe.app.ui.selectSupervisorLiveAlert
import com.pontocafe.app.ui.tempoAtualSupervisor
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


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
    val biometricEnrollmentCompleted: Boolean = false,
    val enrollmentAvatarCaptured: Boolean = false,
    val enrollmentAvatarPreview: ByteArray? = null,
    val enrollmentAvatarStatus: EnrollmentAvatarUploadStatus = EnrollmentAvatarUploadStatus.NOT_CAPTURED,
    val enrollmentAvatarUrl: String? = null,
    val enrollmentAvatarError: String? = null,
    val sessaoAdministrativa: Boolean = false,
    val ultimaAtualizacaoAoVivoEmMillis: Long? = null,
    val conexaoAoVivoOk: Boolean = true,
    val mensagem: String? = null,
    val erro: String? = null,
    val enrollmentDuplicateWarning: EnrollmentDuplicateWarning? = null,
)

data class EnrollmentDuplicateWarning(
    val matchedCollaboradorName: String,
    val score: Double,
)

class SupervisorViewModel(
    private val repository: SupervisorRepository,
    private val embeddingEngine: FaceEmbeddingEngine,
    private val faceCatalogStore: SecureFaceCatalogStore,
    private val applicationContext: Context,
) : ViewModel() {
    var state by mutableStateOf(
        SupervisorUiState(
            destination = if (repository.hasSession()) SupervisorDestination.AO_VIVO else SupervisorDestination.LOGIN,
            sessaoAdministrativa = repository.usingAdminSession(),
        ),
    )
        private set

    private val biometricSamples = mutableListOf<FloatArray>()
    private val enrollmentAvatarCapture = EnrollmentAvatarCaptureSession()
    private var pendingDuplicateEnrollment: PendingDuplicateEnrollment? = null
    private val liveAlertHistoryStore by lazy { OperationalAlertHistoryStore(applicationContext) }
    private var liveAlertMonitoringJob: Job? = null
    private var liveAlertBaseline: Map<String, PausaSupervisor>? = null
    private var liveAlertOverdueBaseline: Set<String> = emptySet()
    private var liveAlertWarningBaseline: Set<String> = emptySet()
    private var liveAlertCriticalBaseline: Set<String> = emptySet()
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

    private fun recoverFromAuthFailure(error: Throwable) {
        repository.clearActiveSession()
        state = SupervisorUiState(
            destination = SupervisorDestination.LOGIN,
            erro = SupervisorRepository.sessionRecoveryMessage(error),
            conexaoAoVivoOk = false,
        )
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
                            recoverFromAuthFailure(error)
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
                            recoverFromAuthFailure(error)
                        }
                    }
            } finally {
                atualizacaoRetornoEmAndamento = false
            }
        }
    }

    /**
     * Tab-independent live-alert monitor: system notifications for departures,
     * returns and pause-limit crossings must keep working while the
     * supervisor is on any tab (Pessoas/Relatórios), not only the "Ao Vivo"
     * screen. Deliberately separate from [atualizarPausasAoVivoSilencioso] —
     * that method (and its AO_VIVO gate) is untouched and keeps driving the
     * Live tab's own on-screen display; this owns notification delivery only,
     * as the single source of truth for "was this event notified". Intended
     * to be started/stopped from SupervisorAreaShell's composition lifetime
     * (any tab open -> running; back to Ponto -> stopped) — it does not, by
     * itself, survive the app being backgrounded or the process dying.
     */
    fun startLiveAlertMonitoring() {
        if (liveAlertMonitoringJob?.isActive == true) return
        liveAlertBaseline = null
        liveAlertOverdueBaseline = emptySet()
        liveAlertWarningBaseline = emptySet()
        liveAlertCriticalBaseline = emptySet()
        liveAlertMonitoringJob = viewModelScope.launch {
            while (true) {
                runCatching { pollLiveAlertsOnce() }
                delay(LIVE_ALERT_MONITOR_INTERVAL_MILLIS)
            }
        }
    }

    fun stopLiveAlertMonitoring() {
        liveAlertMonitoringJob?.cancel()
        liveAlertMonitoringJob = null
    }

    private suspend fun pollLiveAlertsOnce() {
        if (!repository.hasSession()) return
        val pausas = withContext(Dispatchers.IO) { repository.pausasAtivas() }
        val agora = System.currentTimeMillis()
        val atual = pausas.associateBy { it.id }
        val excessosAtuais = atual.values
            .filter { tempoAtualSupervisor(it, agora) > it.limiteSegundos }
            .mapTo(mutableSetOf()) { it.id }
        val criticosAtuais = atual.values
            .filter {
                val remaining = it.limiteSegundos - tempoAtualSupervisor(it, agora)
                remaining in 0..SUPERVISOR_LIVE_ALERT_CRITICAL_THRESHOLD_SECONDS
            }
            .mapTo(mutableSetOf()) { it.id }
        val avisosAtuais = atual.values
            .filter {
                val remaining = it.limiteSegundos - tempoAtualSupervisor(it, agora)
                remaining in (SUPERVISOR_LIVE_ALERT_CRITICAL_THRESHOLD_SECONDS + 1)..SUPERVISOR_LIVE_ALERT_WARNING_THRESHOLD_SECONDS
            }
            .mapTo(mutableSetOf()) { it.id }
        val anterior = liveAlertBaseline

        if (anterior == null) {
            liveAlertBaseline = atual
            liveAlertOverdueBaseline = excessosAtuais
            liveAlertWarningBaseline = avisosAtuais
            liveAlertCriticalBaseline = criticosAtuais
            return
        }

        val novas = atual.filterKeys { it !in anterior }.values.toList()
        val retornos = anterior.filterKeys { it !in atual }.values.toList()
        val novosExcessos = excessosAtuais.filter { it !in liveAlertOverdueBaseline }.mapNotNull(atual::get)
        val novosCriticos = criticosAtuais.filter { it !in liveAlertCriticalBaseline }.mapNotNull(atual::get)
        val novosAvisos = avisosAtuais.filter { it !in liveAlertWarningBaseline }.mapNotNull(atual::get)

        liveAlertBaseline = atual
        liveAlertOverdueBaseline = excessosAtuais
        liveAlertWarningBaseline = avisosAtuais
        liveAlertCriticalBaseline = criticosAtuais

        val alert = selectSupervisorLiveAlert(
            novas = novas,
            retornos = retornos,
            novosExcessos = novosExcessos,
            novosCriticos = novosCriticos,
            novosAvisos = novosAvisos,
            alertId = System.nanoTime(),
        ) ?: return

        // O estágio crítico de 15 s é deliberadamente visual e permanece no
        // centro de alertas da tela Ao Vivo; a notificação do Android já
        // ocorre em ~60 s (aviso) e volta a ocorrer somente se o limite for
        // efetivamente excedido, evitando ruído excessivo.
        if (alert.type == SupervisorLiveAlertType.CRITICO.name) return

        val wasDuplicate = liveAlertHistoryStore.record(
            id = alert.id,
            type = alert.type,
            title = alert.title,
            message = alert.message,
        )
        if (wasDuplicate) return

        SupervisorAlertNotifier.notify(
            context = applicationContext,
            eventType = alert.type,
            title = alert.title,
            message = alert.message,
            uniqueKey = alert.id,
        )
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
                        recoverFromAuthFailure(error)
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
        releaseEnrollmentAvatarArtifacts()
        biometricSamples.clear()
        viewModelScope.launch {
            state = state.copy(
                carregando = true,
                biometricEnrollmentCompleted = false,
                enrollmentAvatarCaptured = false,
                enrollmentAvatarPreview = null,
                enrollmentAvatarStatus = EnrollmentAvatarUploadStatus.NOT_CAPTURED,
                enrollmentAvatarUrl = null,
                enrollmentAvatarError = null,
                erro = null,
                mensagem = null,
            )
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
        releaseEnrollmentAvatarArtifacts()
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
                    releaseEnrollmentAvatarArtifacts()
                    biometricSamples.clear()
                    state = state.copy(
                        carregando = false,
                        destination = SupervisorDestination.BIOMETRIA,
                        colaboradorSelecionado = colaborador,
                        biometricScanCycle = state.biometricScanCycle + 1,
                        biometricStepIndex = 0,
                        biometricSamplesCaptured = 0,
                        biometricEnrollmentCompleted = false,
                        enrollmentAvatarCaptured = false,
                        enrollmentAvatarPreview = null,
                        enrollmentAvatarStatus = EnrollmentAvatarUploadStatus.NOT_CAPTURED,
                        enrollmentAvatarUrl = colaborador.avatarUrl,
                        enrollmentAvatarError = null,
                        mensagem = "Colaborador cadastrado. Agora registre o rosto em 5 etapas.",
                    )
                }
                .onFailure {
                    state = state.copy(carregando = false, erro = SupervisorRepository.message(it))
                }
        }
    }

    fun cadastrarOuAtualizarRosto(colaborador: Colaborador) {
        releaseEnrollmentAvatarArtifacts()
        biometricSamples.clear()
        state = state.copy(
            destination = SupervisorDestination.BIOMETRIA,
            colaboradorSelecionado = colaborador,
            biometricScanCycle = state.biometricScanCycle + 1,
            biometricStepIndex = 0,
            biometricSamplesCaptured = 0,
            biometricEnrollmentCompleted = false,
            enrollmentAvatarCaptured = false,
            enrollmentAvatarPreview = null,
            enrollmentAvatarStatus = EnrollmentAvatarUploadStatus.NOT_CAPTURED,
            enrollmentAvatarUrl = colaborador.avatarUrl,
            enrollmentAvatarError = null,
            erro = null,
            mensagem = null,
        )
    }

    fun processarAmostraBiometrica(frame: FaceFrame) {
        val colaborador = state.colaboradorSelecionado
        if (colaborador == null || state.carregando) {
            if (!frame.bitmap.isRecycled) frame.bitmap.recycle()
            return
        }
        if (!embeddingEngine.isReady) {
            if (!frame.bitmap.isRecycled) frame.bitmap.recycle()
            state = state.copy(erro = "O modelo de reconhecimento facial ainda não está instalado neste APK.")
            return
        }

        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = "Processando amostra facial...")
            try {
                val stagedAvatar = runCatching {
                    withContext(Dispatchers.Default) { enrollmentAvatarCapture.stage(frame) }
                }.getOrNull()
                var biometricSampleAccepted = false
                val embedding = try {
                    embeddingEngine.embed(frame).also {
                        FaceEmbeddingIntegrity.requireValid(it)
                        biometricSampleAccepted = true
                    }
                } finally {
                    if (stagedAvatar != null) {
                        if (biometricSampleAccepted) {
                            runCatching {
                                withContext(Dispatchers.Default) {
                                    enrollmentAvatarCapture.consider(stagedAvatar)
                                }
                            }
                        }
                        stagedAvatar.close()
                    }
                }
                biometricSamples += embedding.copyOf()

                val captured = biometricSamples.size
                if (captured < BIOMETRIC_SAMPLE_COUNT) {
                    val avatarCaptured = enrollmentAvatarCapture.hasCandidate()
                    state = state.copy(
                        carregando = false,
                        biometricStepIndex = captured,
                        biometricSamplesCaptured = captured,
                        biometricScanCycle = state.biometricScanCycle + 1,
                        enrollmentAvatarCaptured = avatarCaptured,
                        mensagem = if (avatarCaptured) {
                            "Amostra $captured de $BIOMETRIC_SAMPLE_COUNT capturada. Foto de perfil selecionada."
                        } else {
                            "Amostra $captured de $BIOMETRIC_SAMPLE_COUNT capturada."
                        },
                        erro = null,
                    )
                    return@launch
                }

                val samplesForValidation = biometricSamples.map { it.copyOf() }
                val combined = combineBiometricSamples(samplesForValidation)

                val duplicate = runCatching {
                    faceCatalogStore.read()?.let { catalog ->
                        LocalFaceMatcher.evaluateEnrollmentDuplicate(
                            candidateEmbedding = combined,
                            catalog = catalog,
                            excludeCollaboratorId = colaborador.id,
                        )
                    }
                }.getOrNull()

                if (duplicate?.duplicate == true) {
                    pendingDuplicateEnrollment = PendingDuplicateEnrollment(colaborador, combined, samplesForValidation)
                    state = state.copy(
                        carregando = false,
                        enrollmentDuplicateWarning = EnrollmentDuplicateWarning(
                            matchedCollaboradorName = duplicate.matchedCollaborador?.nome
                                ?: "outro colaborador já cadastrado",
                            score = duplicate.score ?: 0.0,
                        ),
                        mensagem = null,
                        erro = null,
                    )
                    return@launch
                }

                salvarBiometriaConsolidada(colaborador, combined, samplesForValidation)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                val completedSequence = biometricSamples.size >= BIOMETRIC_SAMPLE_COUNT
                if (completedSequence) {
                    biometricSamples.clear()
                    enrollmentAvatarCapture.clear()
                }
                val captured = biometricSamples.size.coerceAtMost(BIOMETRIC_SAMPLE_COUNT - 1)
                state = state.copy(
                    carregando = false,
                    biometricStepIndex = captured,
                    biometricSamplesCaptured = captured,
                    biometricScanCycle = state.biometricScanCycle + 1,
                    enrollmentAvatarCaptured = enrollmentAvatarCapture.hasCandidate(),
                    mensagem = null,
                    erro = SupervisorRepository.message(error),
                )
            }
        }
    }

    /**
     * The candidate embedding looked like a likely duplicate of another
     * collaborator's already-enrolled face (best-effort, local-catalog-only
     * check). A supervisor explicitly confirmed this is a false positive
     * (e.g. identical twins) and the enrollment should proceed anyway, using
     * the same samples already captured — no need to re-scan.
     */
    fun confirmarCadastroApesarDeDuplicidade() {
        val pending = pendingDuplicateEnrollment ?: return
        pendingDuplicateEnrollment = null
        state = state.copy(enrollmentDuplicateWarning = null, carregando = true)
        viewModelScope.launch {
            try {
                salvarBiometriaConsolidada(pending.colaborador, pending.combined, pending.samples)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                biometricSamples.clear()
                enrollmentAvatarCapture.clear()
                state = state.copy(
                    carregando = false,
                    biometricStepIndex = 0,
                    biometricSamplesCaptured = 0,
                    biometricScanCycle = state.biometricScanCycle + 1,
                    enrollmentAvatarCaptured = false,
                    mensagem = null,
                    erro = SupervisorRepository.message(error),
                )
            }
        }
    }

    /** Discards the pending duplicate-flagged enrollment; the sequence must be redone. */
    fun cancelarCadastroPorDuplicidade() {
        pendingDuplicateEnrollment = null
        biometricSamples.clear()
        enrollmentAvatarCapture.clear()
        state = state.copy(
            carregando = false,
            biometricStepIndex = 0,
            biometricSamplesCaptured = 0,
            biometricScanCycle = state.biometricScanCycle + 1,
            enrollmentAvatarCaptured = false,
            enrollmentDuplicateWarning = null,
            mensagem = "Cadastro cancelado. Você pode tentar novamente.",
            erro = null,
        )
    }

    private suspend fun salvarBiometriaConsolidada(
        colaborador: Colaborador,
        combined: FloatArray,
        samplesForValidation: List<FloatArray>,
    ) {
        repository.saveBiometric(
            collaboratorId = colaborador.id,
            embedding = combined,
            model = embeddingEngine.modelName,
            modelVersion = embeddingEngine.modelVersion,
            samples = samplesForValidation,
        )

        biometricSamples.clear()
        val avatarBytes = enrollmentAvatarCapture.takeBestWebp()
        val existingAvatarAvailable = !colaborador.avatarUrl.isNullOrBlank()
        val biometricCollaborator = colaborador.copy(rostoCadastrado = true)
        state = state.copy(
            carregando = true,
            colaboradorSelecionado = biometricCollaborator,
            biometricEnrollmentCompleted = true,
            enrollmentAvatarCaptured = avatarBytes != null,
            enrollmentAvatarPreview = avatarBytes,
            enrollmentAvatarStatus = when {
                avatarBytes != null -> EnrollmentAvatarUploadStatus.UPLOADING
                existingAvatarAvailable -> EnrollmentAvatarUploadStatus.SAVED
                else -> EnrollmentAvatarUploadStatus.NOT_CAPTURED
            },
            enrollmentAvatarUrl = colaborador.avatarUrl,
            enrollmentAvatarError = null,
            enrollmentDuplicateWarning = null,
            mensagem = "Biometria de ${colaborador.nome} salva com segurança.",
            erro = null,
        )

        var avatarUrl = colaborador.avatarUrl
        var avatarFailure: Throwable? = null
        if (avatarBytes != null) {
            try {
                avatarUrl = repository.uploadAvatar(colaborador.id, avatarBytes).avatarUrl
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                avatarFailure = error
            }
        }

        val updatedCollaborator = biometricCollaborator.copy(avatarUrl = avatarUrl)
        state = state.copy(
            carregando = false,
            colaboradores = upsertCollaborator(state.colaboradores, updatedCollaborator),
            colaboradorSelecionado = updatedCollaborator,
            enrollmentAvatarStatus = when {
                avatarBytes == null && existingAvatarAvailable -> EnrollmentAvatarUploadStatus.SAVED
                avatarBytes == null -> EnrollmentAvatarUploadStatus.NOT_CAPTURED
                avatarFailure == null -> EnrollmentAvatarUploadStatus.SAVED
                else -> EnrollmentAvatarUploadStatus.FAILED
            },
            enrollmentAvatarUrl = avatarUrl,
            enrollmentAvatarError = avatarFailure?.let {
                "A biometria foi salva, mas a foto de perfil não. ${SupervisorRepository.message(it)}"
            },
            mensagem = when {
                avatarBytes == null && existingAvatarAvailable ->
                    "Rosto de ${colaborador.nome} cadastrado. A foto de perfil existente foi mantida."
                avatarBytes == null ->
                    "Rosto de ${colaborador.nome} cadastrado. Você pode adicionar a foto de perfil sem repetir a biometria."
                avatarFailure == null ->
                    "Rosto e foto de perfil de ${colaborador.nome} cadastrados com sucesso."
                else ->
                    "Rosto de ${colaborador.nome} cadastrado. Falta apenas salvar a foto de perfil."
            },
            erro = null,
        )
    }

    fun tentarNovamenteAvatarDoCadastro() {
        val preview = state.enrollmentAvatarPreview ?: return
        saveEnrollmentAvatar(preview)
    }

    fun substituirAvatarDoCadastro(webp: ByteArray) {
        if (webp.isEmpty() || webp.size > AvatarImageOptimizer.MAX_BYTES) {
            state = state.copy(enrollmentAvatarError = "A foto de perfil preparada é inválida.")
            return
        }
        saveEnrollmentAvatar(webp)
    }

    private fun saveEnrollmentAvatar(webp: ByteArray) {
        val collaborator = state.colaboradorSelecionado ?: return
        if (!state.biometricEnrollmentCompleted || state.carregando) return

        state = state.copy(
            carregando = true,
            enrollmentAvatarCaptured = true,
            enrollmentAvatarPreview = webp,
            enrollmentAvatarStatus = EnrollmentAvatarUploadStatus.UPLOADING,
            enrollmentAvatarError = null,
            erro = null,
        )
        viewModelScope.launch {
            try {
                val result = repository.uploadAvatar(collaborator.id, webp)
                val updated = collaborator.copy(avatarUrl = result.avatarUrl)
                state = state.copy(
                    carregando = false,
                    colaboradores = upsertCollaborator(state.colaboradores, updated),
                    colaboradorSelecionado = updated,
                    enrollmentAvatarStatus = EnrollmentAvatarUploadStatus.SAVED,
                    enrollmentAvatarUrl = result.avatarUrl,
                    enrollmentAvatarError = null,
                    mensagem = "Foto de perfil de ${collaborator.nome} salva. A biometria não foi alterada.",
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                state = state.copy(
                    carregando = false,
                    enrollmentAvatarStatus = EnrollmentAvatarUploadStatus.FAILED,
                    enrollmentAvatarError = "A biometria continua salva. ${SupervisorRepository.message(error)}",
                    mensagem = "Falta apenas salvar a foto de perfil de ${collaborator.nome}.",
                    erro = null,
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
        releaseEnrollmentAvatarArtifacts()
        biometricSamples.clear()
        state = state.copy(
            destination = SupervisorDestination.COLABORADORES,
            colaboradorSelecionado = null,
            biometricStepIndex = 0,
            biometricSamplesCaptured = 0,
            biometricEnrollmentCompleted = false,
            enrollmentAvatarCaptured = false,
            enrollmentAvatarPreview = null,
            enrollmentAvatarStatus = EnrollmentAvatarUploadStatus.NOT_CAPTURED,
            enrollmentAvatarUrl = null,
            enrollmentAvatarError = null,
            erro = null,
        )
    }

    fun voltarAoVivo() {
        releaseEnrollmentAvatarArtifacts()
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
        releaseEnrollmentAvatarArtifacts()
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
        return BiometricTemplateAggregator.aggregate(samples).embedding
    }

    private fun releaseEnrollmentAvatarArtifacts() {
        enrollmentAvatarCapture.clear()
        state.enrollmentAvatarPreview?.fill(0)
    }

    private fun upsertCollaborator(
        collaborators: List<Colaborador>,
        updated: Colaborador,
    ): List<Colaborador> {
        val found = collaborators.any { it.id == updated.id }
        return (if (found) {
            collaborators.map { if (it.id == updated.id) updated else it }
        } else {
            collaborators + updated
        }).sortedWith(compareBy<Colaborador> { it.rostoCadastrado }.thenBy { it.nome.lowercase() })
    }

    override fun onCleared() {
        stopLiveAlertMonitoring()
        releaseEnrollmentAvatarArtifacts()
        biometricSamples.clear()
        super.onCleared()
    }

    companion object {
        private const val BIOMETRIC_SAMPLE_COUNT = 5
        private const val LIVE_ALERT_MONITOR_INTERVAL_MILLIS = 15_000L
    }
}

private data class PendingDuplicateEnrollment(
    val colaborador: Colaborador,
    val combined: FloatArray,
    val samples: List<FloatArray>,
)

class SupervisorViewModelFactory(private val creator: () -> SupervisorViewModel) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(SupervisorViewModel::class.java))
        return creator() as T
    }
}
