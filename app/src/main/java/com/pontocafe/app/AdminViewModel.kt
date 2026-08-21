package com.pontocafe.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pontocafe.app.camera.FaceEmbeddingEngine
import com.pontocafe.app.camera.FaceFrame
import com.pontocafe.app.data.AdminCoffeeRule
import com.pontocafe.app.data.AdminOperationalSummary
import com.pontocafe.app.data.AdminRepository
import com.pontocafe.app.data.AdminUser
import com.pontocafe.app.data.AuditEvent
import com.pontocafe.app.data.Colaborador
import kotlinx.coroutines.launch
import kotlin.math.sqrt


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
        biometricSamples.clear()
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = null)
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
                    biometricSamples.clear()
                    state = state.copy(
                        carregando = false,
                        destination = AdminDestination.BIOMETRIC_ENROLLMENT,
                        colaboradorSelecionado = colaborador,
                        biometricScanCycle = state.biometricScanCycle + 1,
                        biometricStepIndex = 0,
                        biometricSamplesCaptured = 0,
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
        biometricSamples.clear()
        state = state.copy(
            destination = AdminDestination.BIOMETRIC_ENROLLMENT,
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
                    destination = AdminDestination.COLLABORATORS,
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
                    erro = AdminRepository.message(error),
                )
            }
        }
    }

    fun voltarColaboradores() {
        biometricSamples.clear()
        state = state.copy(
            destination = AdminDestination.COLLABORATORS,
            colaboradorSelecionado = null,
            biometricStepIndex = 0,
            biometricSamplesCaptured = 0,
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
        biometricSamples.clear()
        state = state.copy(
            destination = AdminDestination.HOME,
            selecionado = null,
            colaboradorSelecionado = null,
            biometricStepIndex = 0,
            biometricSamplesCaptured = 0,
            erro = null,
            mensagem = null,
        )
    }

    fun logout() {
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

    companion object {
        private const val BIOMETRIC_SAMPLE_COUNT = 5

        fun combineBiometricSamples(samples: List<FloatArray>): FloatArray {
            require(samples.isNotEmpty()) { "Nenhuma amostra biométrica foi capturada." }
            val dimension = samples.first().size
            require(dimension > 0 && samples.all { it.size == dimension }) { "As amostras biométricas possuem dimensões incompatíveis." }

            val average = FloatArray(dimension)
            samples.forEach { sample ->
                for (index in sample.indices) average[index] += sample[index]
            }
            for (index in average.indices) average[index] /= samples.size.toFloat()

            var normSquared = 0.0
            average.forEach { value -> normSquared += value.toDouble() * value.toDouble() }
            val norm = sqrt(normSquared).toFloat()
            require(norm > 0f && norm.isFinite()) { "Não foi possível normalizar as amostras biométricas." }
            for (index in average.indices) average[index] /= norm
            return average
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
