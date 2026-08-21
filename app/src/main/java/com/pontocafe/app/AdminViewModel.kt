package com.pontocafe.app

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
import com.pontocafe.app.data.AdminCoffeeRule
import com.pontocafe.app.data.AdminOperationalSummary
import com.pontocafe.app.data.AdminRepository
import com.pontocafe.app.data.AdminUser
import com.pontocafe.app.data.AuditEvent
import com.pontocafe.app.data.BiometricTemplateAggregator
import com.pontocafe.app.data.Colaborador
import com.pontocafe.app.data.FaceEmbeddingIntegrity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


enum class AdminDestination {
    LOADING,
    LOGIN,
    FIRST_SETUP,
    HOME,
    NEW_ACCOUNT,
    USER_DETAIL,
    COLLABORATORS,
    NEW_COLLABORATOR,
    BIOMETRIC_ENROLLMENT,
    SETTINGS,
    AUTHORIZATION,
    AUDIT,
}

data class AdminUiState(
    val destination: AdminDestination = AdminDestination.LOADING,
    val carregando: Boolean = false,
    val primeiroAdminNecessario: Boolean = false,
    val instalacaoConfigurada: Boolean = false,
    val usuarios: List<AdminUser> = emptyList(),
    val selecionado: AdminUser? = null,
    val colaboradores: List<Colaborador> = emptyList(),
    val colaboradorSelecionado: Colaborador? = null,
    val regrasCafe: List<AdminCoffeeRule> = emptyList(),
    val auditoria: List<AuditEvent> = emptyList(),
    val resumoOperacional: AdminOperationalSummary? = null,
    val biometricScanCycle: Int = 0,
    val biometricStepIndex: Int = 0,
    val biometricSamplesCaptured: Int = 0,
    val biometricEnrollmentCompleted: Boolean = false,
    val enrollmentAvatarCaptured: Boolean = false,
    val enrollmentAvatarPreview: ByteArray? = null,
    val enrollmentAvatarStatus: EnrollmentAvatarUploadStatus = EnrollmentAvatarUploadStatus.NOT_CAPTURED,
    val enrollmentAvatarUrl: String? = null,
    val enrollmentAvatarError: String? = null,
    val authorizationId: String? = null,
    val authorizationEmployeeName: String? = null,
    val authorizationPeriod: String? = null,
    val authorizationExpirySeconds: Int? = null,
    val mensagem: String? = null,
    val erro: String? = null,
)

class AdminViewModel(
    private val repository: AdminRepository,
    val embeddingEngine: FaceEmbeddingEngine,
) : ViewModel() {
    var state by mutableStateOf(AdminUiState())
        private set

    private val biometricSamples = mutableListOf<FloatArray>()
    private val enrollmentAvatarCapture = EnrollmentAvatarCaptureSession()

    init {
        inicializar()
    }

    private fun inicializar() {
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null)
            runCatching { repository.setupStatus() }
                .onSuccess { status ->
                    when {
                        status.primeiroAdminNecessario -> state = state.copy(
                            carregando = false,
                            primeiroAdminNecessario = true,
                            instalacaoConfigurada = status.instalacaoConfigurada,
                            destination = AdminDestination.FIRST_SETUP,
                        )
                        repository.hasSession() -> carregarUsuariosInterno()
                        else -> state = state.copy(
                            carregando = false,
                            primeiroAdminNecessario = false,
                            instalacaoConfigurada = status.instalacaoConfigurada,
                            destination = AdminDestination.LOGIN,
                        )
                    }
                }
                .onFailure {
                    state = state.copy(
                        carregando = false,
                        destination = if (repository.hasSession()) AdminDestination.HOME else AdminDestination.LOGIN,
                        erro = AdminRepository.message(it),
                    )
                }
        }
    }

    private suspend fun carregarUsuariosInterno(message: String? = null) {
        runCatching { repository.users() }
            .onSuccess { users ->
                val summary = runCatching { repository.operationalSummary() }.getOrNull()
                state = state.copy(
                    carregando = false,
                    destination = AdminDestination.HOME,
                    usuarios = users,
                    resumoOperacional = summary,
                    selecionado = null,
                    mensagem = message,
                    erro = null,
                )
            }
            .onFailure { error ->
                state = if (repository.hasSession()) {
                    state.copy(
                        carregando = false,
                        destination = AdminDestination.HOME,
                        erro = "Sem conexão com o servidor. Sua sessão foi preservada e os dados serão atualizados quando a conexão voltar.",
                    )
                } else {
                    state.copy(carregando = false, destination = AdminDestination.LOGIN, erro = AdminRepository.message(error))
                }
            }
    }

    fun login(email: String, senha: String) {
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = null)
            runCatching { repository.signIn(email.trim().lowercase(), senha) }
                .onSuccess { carregarUsuariosInterno() }
                .onFailure { state = state.copy(carregando = false, erro = AdminRepository.message(it)) }
        }
    }

    fun criarPrimeiroAdmin(nome: String, email: String, senha: String, chave: String) {
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = null)
            runCatching { repository.createFirstAdmin(nome.trim(), email.trim().lowercase(), senha, chave) }
                .onSuccess {
                    state = state.copy(
                        carregando = false,
                        primeiroAdminNecessario = false,
                        destination = AdminDestination.LOGIN,
                        mensagem = "Administrador criado. Entre com o e-mail e a senha cadastrados.",
                    )
                }
                .onFailure { state = state.copy(carregando = false, erro = AdminRepository.message(it)) }
        }
    }

    fun abrirNovaConta() {
        state = state.copy(destination = AdminDestination.NEW_ACCOUNT, erro = null, mensagem = null)
    }

    fun criarConta(input: com.pontocafe.app.ui.NewAccountInput) {
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = null)
            runCatching { repository.createUser(input.nome, input.email, input.senha, input.perfil.name) }
                .onSuccess { carregarUsuariosInterno("Conta cadastrada com sucesso.") }
                .onFailure { state = state.copy(carregando = false, erro = AdminRepository.message(it)) }
        }
    }

    fun selecionarUsuario(user: AdminUser) {
        state = state.copy(selecionado = user, destination = AdminDestination.USER_DETAIL, erro = null, mensagem = null)
    }

    fun alterarAtivo(user: AdminUser) {
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = null)
            runCatching { repository.setActive(user.id, !user.ativo) }
                .onSuccess { carregarUsuariosInterno(if (user.ativo) "Conta desativada." else "Conta reativada.") }
                .onFailure { state = state.copy(carregando = false, erro = AdminRepository.message(it)) }
        }
    }

    fun excluirUsuario(user: AdminUser) {
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = null)
            runCatching { repository.deleteUser(user.id) }
                .onSuccess { carregarUsuariosInterno("Conta excluída definitivamente.") }
                .onFailure { state = state.copy(carregando = false, erro = AdminRepository.message(it)) }
        }
    }

    fun redefinirSenha(userId: String, novaSenha: String) {
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = null)
            runCatching { repository.resetPassword(userId, novaSenha) }
                .onSuccess { state = state.copy(carregando = false, mensagem = "Senha redefinida. As sessões dessa conta foram encerradas.") }
                .onFailure { state = state.copy(carregando = false, erro = AdminRepository.message(it)) }
        }
    }

    fun alterarPerfil(user: AdminUser, perfil: String) {
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = null)
            runCatching { repository.changeProfile(user.id, perfil) }
                .onSuccess { carregarUsuariosInterno("Perfil atualizado.") }
                .onFailure { state = state.copy(carregando = false, erro = AdminRepository.message(it)) }
        }
    }

    fun abrirAuditoria() {
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = null)
            runCatching { repository.audit(150) }
                .onSuccess {
                    state = state.copy(
                        carregando = false,
                        destination = AdminDestination.AUDIT,
                        auditoria = it,
                    )
                }
                .onFailure { state = state.copy(carregando = false, erro = AdminRepository.message(it)) }
        }
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
                        carregando = false,
                        destination = AdminDestination.COLLABORATORS,
                        colaboradores = it,
                        colaboradorSelecionado = null,
                        biometricStepIndex = 0,
                        biometricSamplesCaptured = 0,
                    )
                }
                .onFailure { state = state.copy(carregando = false, erro = AdminRepository.message(it)) }
        }
    }

    fun abrirNovoColaborador() {
        releaseEnrollmentAvatarArtifacts()
        state = state.copy(destination = AdminDestination.NEW_COLLABORATOR, erro = null, mensagem = null)
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
                        destination = AdminDestination.BIOMETRIC_ENROLLMENT,
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
                        mensagem = "Colaborador cadastrado. Agora cadastre o rosto em 5 etapas.",
                    )
                }
                .onFailure { state = state.copy(carregando = false, erro = AdminRepository.message(it)) }
        }
    }

    fun editarColaborador(colaborador: Colaborador, nome: String, setor: String, turno: String) {
        val cleanName = nome.trim()
        if (cleanName.length < 2) {
            state = state.copy(erro = "Informe o nome do colaborador.")
            return
        }

        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = null)
            runCatching {
                repository.updateCollaborator(
                    collaboratorId = colaborador.id,
                    name = cleanName,
                    sector = setor,
                    shift = turno,
                )
            }.onSuccess { atualizado ->
                state = state.copy(
                    carregando = false,
                    colaboradores = state.colaboradores
                        .map { if (it.id == atualizado.id) atualizado else it }
                        .sortedBy { it.nome.lowercase() },
                    colaboradorSelecionado = if (state.colaboradorSelecionado?.id == atualizado.id) atualizado else state.colaboradorSelecionado,
                    mensagem = "Dados de ${atualizado.nome} atualizados com sucesso.",
                    erro = null,
                )
            }.onFailure {
                state = state.copy(carregando = false, erro = AdminRepository.message(it))
            }
        }
    }

    fun cadastrarOuAtualizarRosto(colaborador: Colaborador) {
        releaseEnrollmentAvatarArtifacts()
        biometricSamples.clear()
        state = state.copy(
            destination = AdminDestination.BIOMETRIC_ENROLLMENT,
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
                        "A biometria foi salva, mas a foto de perfil não. ${AdminRepository.message(it)}"
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
                    erro = AdminRepository.message(error),
                )
            }
        }
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
                    enrollmentAvatarError = "A biometria continua salva. ${AdminRepository.message(error)}",
                    mensagem = "Falta apenas salvar a foto de perfil de ${collaborator.nome}.",
                    erro = null,
                )
            }
        }
    }

    fun voltarColaboradores() {
        releaseEnrollmentAvatarArtifacts()
        biometricSamples.clear()
        state = state.copy(
            destination = AdminDestination.COLLABORATORS,
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

    fun abrirConfiguracoes() {
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = null)
            runCatching { repository.coffeeRules() }
                .onSuccess { state = state.copy(carregando = false, destination = AdminDestination.SETTINGS, regrasCafe = it) }
                .onFailure { state = state.copy(carregando = false, erro = AdminRepository.message(it)) }
        }
    }

    fun salvarRegraCafe(periodo: String, inicio: String, fim: String, limiteMinutos: Int, ativo: Boolean) {
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = null)
            runCatching { repository.updateCoffeeRule(periodo, inicio, fim, limiteMinutos, ativo) }
                .onSuccess { updated ->
                    state = state.copy(
                        carregando = false,
                        regrasCafe = state.regrasCafe.map { if (it.periodo == updated.periodo) updated else it },
                        mensagem = "Regra de ${if (periodo == "MANHA") "manhã" else "tarde"} atualizada.",
                    )
                }
                .onFailure { state = state.copy(carregando = false, erro = AdminRepository.message(it)) }
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
                authorizationExpirySeconds = null,
            )
            runCatching { repository.collaborators() }
                .onSuccess { state = state.copy(carregando = false, destination = AdminDestination.AUTHORIZATION, colaboradores = it) }
                .onFailure { state = state.copy(carregando = false, erro = AdminRepository.message(it)) }
        }
    }

    fun autorizarPausa(colaborador: Colaborador, motivo: String) {
        if (motivo.trim().length < 2) {
            state = state.copy(erro = "Informe o motivo da autorização.")
            return
        }
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = null, authorizationId = null)
            runCatching { repository.createAuthorization(colaborador.id, motivo) }
                .onSuccess { auth ->
                    state = state.copy(
                        carregando = false,
                        authorizationId = auth.id,
                        authorizationEmployeeName = auth.colaboradorNome,
                        authorizationPeriod = auth.periodo,
                        authorizationExpirySeconds = auth.expiraEmSegundos,
                        mensagem = "Autorização concedida para ${auth.colaboradorNome}.",
                        erro = null,
                    )
                }
                .onFailure { state = state.copy(carregando = false, erro = AdminRepository.message(it)) }
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
                        authorizationExpirySeconds = null,
                        mensagem = "Autorização de ${colaborador.nome} cancelada.",
                        erro = null,
                    )
                }
                .onFailure { state = state.copy(carregando = false, erro = AdminRepository.message(it)) }
        }
    }

    fun limparAutorizacao() {
        state = state.copy(
            authorizationId = null,
            authorizationEmployeeName = null,
            authorizationPeriod = null,
            authorizationExpirySeconds = null,
            mensagem = null,
            erro = null,
        )
    }

    fun voltarHome() {
        releaseEnrollmentAvatarArtifacts()
        biometricSamples.clear()
        state = state.copy(
            destination = AdminDestination.HOME,
            selecionado = null,
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
            mensagem = null,
        )
    }

    fun logout() {
        releaseEnrollmentAvatarArtifacts()
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null)
            repository.signOut()
            state = AdminUiState(destination = AdminDestination.LOGIN, carregando = false)
        }
    }

    fun restaurarNavegacao(destinationName: String?, userId: String?, collaboratorId: String?) {
        val destination = destinationName?.let { name ->
            runCatching { AdminDestination.valueOf(name) }.getOrNull()
        } ?: return
        if (destination in setOf(AdminDestination.LOADING, AdminDestination.LOGIN, AdminDestination.FIRST_SETUP)) return

        when (destination) {
            AdminDestination.HOME -> state = state.copy(destination = AdminDestination.HOME)
            AdminDestination.COLLABORATORS -> abrirColaboradores()
            AdminDestination.SETTINGS -> abrirConfiguracoes()
            AdminDestination.AUDIT -> abrirAuditoria()
            AdminDestination.AUTHORIZATION -> abrirAutorizacao()
            AdminDestination.USER_DETAIL -> {
                val user = state.usuarios.firstOrNull { it.id == userId }
                if (user != null) selecionarUsuario(user) else state = state.copy(destination = AdminDestination.HOME)
            }
            AdminDestination.NEW_ACCOUNT -> state = state.copy(destination = AdminDestination.NEW_ACCOUNT)
            AdminDestination.NEW_COLLABORATOR -> state = state.copy(destination = AdminDestination.NEW_COLLABORATOR)
            AdminDestination.BIOMETRIC_ENROLLMENT -> {
                val collaborator = state.colaboradores.firstOrNull { it.id == collaboratorId }
                if (collaborator != null) cadastrarOuAtualizarRosto(collaborator) else abrirColaboradores()
            }
            AdminDestination.LOADING,
            AdminDestination.LOGIN,
            AdminDestination.FIRST_SETUP -> Unit
        }
    }

    fun limparFeedback() {
        state = state.copy(erro = null, mensagem = null)
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
        }).sortedBy { it.nome.lowercase() }
    }

    override fun onCleared() {
        releaseEnrollmentAvatarArtifacts()
        biometricSamples.clear()
        super.onCleared()
    }

    companion object {
        private const val BIOMETRIC_SAMPLE_COUNT = 5

        fun combineBiometricSamples(samples: List<FloatArray>): FloatArray {
            require(samples.isNotEmpty()) { "Nenhuma amostra biométrica foi capturada." }
            return BiometricTemplateAggregator.aggregate(samples).embedding
        }
    }
}

class AdminViewModelFactory(
    private val creator: () -> AdminViewModel,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(AdminViewModel::class.java))
        return creator() as T
    }
}
