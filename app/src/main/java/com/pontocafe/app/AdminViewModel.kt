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
import com.pontocafe.app.ui.NewAccountInput
import kotlinx.coroutines.launch
import kotlin.math.sqrt


enum class AdminDestination {
    LOADING,
    LOGIN,
    FIRST_SETUP,
    HOME,
    NEW_ACCOUNT,
    USER_DETAIL,
    AUTHORIZATION,
    SETTINGS,
    COLLABORATORS,
    NEW_COLLABORATOR,
    BIOMETRIC_ENROLLMENT,
    AUDIT,
}

data class AdminUiState(
    val destination: AdminDestination = AdminDestination.LOADING,
    val carregando: Boolean = false,
    val primeiroAdminNecessario: Boolean = false,
    val instalacaoConfigurada: Boolean = false,
    val usuarios: List<AdminUser> = emptyList(),
    val colaboradores: List<Colaborador> = emptyList(),
    val regrasCafe: List<AdminCoffeeRule> = emptyList(),
    val auditoria: List<AuditEvent> = emptyList(),
    val resumoOperacional: AdminOperationalSummary? = null,
    val selecionado: AdminUser? = null,
    val colaboradorSelecionado: Colaborador? = null,
    val biometricScanCycle: Int = 0,
    val biometricStepIndex: Int = 0,
    val biometricSamplesCaptured: Int = 0,
    val deviceTokenGerado: String? = null,
    val deviceNome: String? = null,
    val authorizationCode: String? = null,
    val authorizationEmployeeName: String? = null,
    val authorizationExpiresSeconds: Int? = null,
    val mensagem: String? = null,
    val erro: String? = null,
)

class AdminViewModel(
    private val repository: AdminRepository,
    private val embeddingEngine: FaceEmbeddingEngine,
) : ViewModel() {
    var state by mutableStateOf(AdminUiState())
        private set

    private val biometricSamples = mutableListOf<FloatArray>()

    val faceModelReady: Boolean get() = embeddingEngine.isReady

    init {
        carregarEstadoInicial()
    }

    private fun carregarEstadoInicial() {
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null)
            runCatching { repository.setupStatus() }
                .onSuccess { setup ->
                    if (repository.hasSession() && !setup.primeiroAdminNecessario) {
                        carregarUsuariosInterno()
                    } else {
                        state = state.copy(
                            carregando = false,
                            primeiroAdminNecessario = setup.primeiroAdminNecessario,
                            instalacaoConfigurada = setup.instalacaoConfigurada,
                            destination = if (setup.primeiroAdminNecessario) AdminDestination.FIRST_SETUP else AdminDestination.LOGIN,
                        )
                    }
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

    fun criarConta(input: NewAccountInput) {
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

    fun criarColaborador(matricula: String, nome: String, setor: String, turno: String) {
        if (nome.trim().length < 2) {
            state = state.copy(erro = "Informe o nome do colaborador.")
            return
        }
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = null)
            runCatching { repository.createCollaborator(matricula, nome, setor, turno) }
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
                    destination = AdminDestination.COLLABORATORS,
                    colaboradores = colaboradores,
                    colaboradorSelecionado = null,
                    biometricStepIndex = 0,
                    biometricSamplesCaptured = 0,
                    mensagem = "Rosto de ${colaborador.nome} cadastrado com 5 amostras com sucesso.",
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

    fun gerarTokenDispositivo(nome: String) {
        if (nome.trim().length < 2) {
            state = state.copy(erro = "Informe um nome para o dispositivo.")
            return
        }
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = null, deviceTokenGerado = null)
            runCatching { repository.createDevice(nome) }
                .onSuccess { device ->
                    state = state.copy(
                        carregando = false,
                        deviceTokenGerado = device.token,
                        deviceNome = device.nome,
                        mensagem = "Token gerado. Copie agora: ele não será exibido novamente.",
                    )
                }
                .onFailure { state = state.copy(carregando = false, erro = AdminRepository.message(it)) }
        }
    }

    fun limparTokenGerado() {
        state = state.copy(deviceTokenGerado = null, deviceNome = null)
    }

    fun abrirAutorizacao() {
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = null, authorizationCode = null)
            runCatching { repository.collaborators() }
                .onSuccess { state = state.copy(carregando = false, destination = AdminDestination.AUTHORIZATION, colaboradores = it) }
                .onFailure { state = state.copy(carregando = false, erro = AdminRepository.message(it)) }
        }
    }

    fun gerarAutorizacao(colaborador: Colaborador, periodo: String, motivo: String) {
        if (periodo != "MANHA" && periodo != "TARDE") {
            state = state.copy(erro = "Selecione o período autorizado.")
            return
        }
        if (motivo.trim().length < 2) {
            state = state.copy(erro = "Informe o motivo da autorização.")
            return
        }
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = null, authorizationCode = null)
            runCatching { repository.createAuthorization(colaborador.id, periodo, motivo) }
                .onSuccess { authorization ->
                    state = state.copy(
                        carregando = false,
                        authorizationCode = authorization.codigo,
                        authorizationEmployeeName = colaborador.nome,
                        authorizationExpiresSeconds = authorization.expiraEmSegundos,
                        mensagem = "Código gerado. Informe-o ao colaborador antes que expire.",
                    )
                }
                .onFailure { state = state.copy(carregando = false, erro = AdminRepository.message(it)) }
        }
    }

    fun limparAutorizacaoGerada() {
        state = state.copy(
            authorizationCode = null,
            authorizationEmployeeName = null,
            authorizationExpiresSeconds = null,
            mensagem = null,
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
            authorizationCode = null,
            authorizationEmployeeName = null,
            authorizationExpiresSeconds = null,
            erro = null,
        )
    }

    fun logout() {
        biometricSamples.clear()
        viewModelScope.launch {
            state = state.copy(carregando = true)
            repository.signOut()
            state = AdminUiState(destination = AdminDestination.LOGIN, mensagem = "Sessão encerrada.")
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

    private suspend fun carregarUsuariosInterno(message: String? = null) {
        runCatching { repository.users() }
            .onSuccess { usuarios ->
                val colaboradores = runCatching { repository.collaborators() }.getOrElse { state.colaboradores }
                val resumo = runCatching { repository.operationalSummary() }.getOrNull()
                state = state.copy(
                    carregando = false,
                    destination = AdminDestination.HOME,
                    usuarios = usuarios,
                    colaboradores = colaboradores,
                    resumoOperacional = resumo,
                    selecionado = null,
                    mensagem = message,
                    erro = null,
                )
            }
            .onFailure { error ->
                if (AdminRepository.isAuthFailure(error)) {
                    repository.clearSession()
                    state = state.copy(
                        carregando = false,
                        destination = AdminDestination.LOGIN,
                        erro = "Sua sessão administrativa expirou ou não possui acesso de administrador.",
                    )
                } else {
                    state = state.copy(
                        carregando = false,
                        destination = AdminDestination.HOME,
                        erro = "Sem conexão. Sua sessão foi preservada e os últimos dados disponíveis continuam na tela.",
                    )
                }
            }
    }

    companion object {
        private const val BIOMETRIC_SAMPLE_COUNT = 5
    }
}

class AdminViewModelFactory(private val creator: () -> AdminViewModel) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(AdminViewModel::class.java))
        return creator() as T
    }
}
