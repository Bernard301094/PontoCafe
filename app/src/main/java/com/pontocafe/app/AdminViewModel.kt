package com.pontocafe.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pontocafe.app.data.AdminCoffeeRule
import com.pontocafe.app.data.AdminOperationalSummary
import com.pontocafe.app.data.AdminRepository
import com.pontocafe.app.data.AdminUser
import com.pontocafe.app.data.AuditEvent
import com.pontocafe.app.data.AccessCodeCreatedResponse
import com.pontocafe.app.data.AccessCodeItem
import com.pontocafe.app.data.Colaborador
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
    SETTINGS,
    ACCESS_CODES,
    AUDIT,
}

/**
 * Tamanho mínimo do motivo de um registro manual.
 *
 * Espelha `z.string().trim().min(3)` das rotas de pausa manual (iniciar e
 * finalizar). Se os dois divergirem, o campo aceita e a rede recusa — o pior
 * dos dois mundos.
 */
internal const val MOTIVO_MANUAL_MINIMO = 3

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
    val codigosAtivos: List<AccessCodeItem> = emptyList(),
    val codigoEmitido: AccessCodeCreatedResponse? = null,
    /**
     * Carga do atalho de códigos do Início.
     *
     * Separada de [carregando] e de [erro] de propósito: é um carregamento de
     * fundo que ninguém pediu, e não pode desabilitar botões da tela inteira
     * nem abrir um banner vermelho por cima do painel operacional.
     */
    val codigosAtalhoCarregando: Boolean = false,
    val codigosAtalhoErro: String? = null,
    /**
     * Quem tem uma ação individual em curso (emitir ou cancelar código).
     *
     * Existe porque [carregando] é um sinalizador de tela inteira: passá-lo a
     * cada linha da lista fazia os noventa e seis botões "Gerar" acenderem
     * juntos quando se emitia um código para uma pessoa só — parecia que todos
     * tinham sido premidos. Cada linha compara este id com o seu.
     */
    val colaboradorOcupadoId: String? = null,
    val manualPunchResult: ManualPunchResult? = null,
    val mensagem: String? = null,
    val erro: String? = null,
)

class AdminViewModel(
    private val repository: AdminRepository,
) : ViewModel() {
    var state by mutableStateOf(AdminUiState())
        private set


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

    /**
     * Navega primeiro, busca depois.
     *
     * As abas faziam o contrário: pediam os dados e só trocavam de `destination`
     * dentro do `onSuccess`. Cada toque numa aba era uma ida à rede inteira
     * olhando para a tela anterior, e o que já estava em memória reaparecia
     * "do zero" a cada visita. Aqui a tela troca no mesmo instante, o que já foi
     * carregado continua visível, e o indicador de carga só aparece quando não
     * há nada para mostrar.
     *
     * Uma falha de rede também deixou de esvaziar a tela: a lista anterior fica,
     * e o erro aparece como aviso em cima dela.
     */
    private fun <T> navegarEAtualizar(
        destino: AdminDestination,
        temDados: Boolean,
        aoNavegar: (AdminUiState) -> AdminUiState = { it },
        buscar: suspend () -> T,
        aoReceber: (T, AdminUiState) -> AdminUiState,
    ) {
        state = aoNavegar(
            state.copy(
                destination = destino,
                carregando = !temDados,
                erro = null,
                mensagem = null,
            ),
        )
        viewModelScope.launch {
            runCatching { buscar() }
                .onSuccess { state = aoReceber(it, state).copy(carregando = false) }
                .onFailure { state = state.copy(carregando = false, erro = AdminRepository.message(it)) }
        }
    }

    fun abrirAuditoria() = navegarEAtualizar(
        destino = AdminDestination.AUDIT,
        temDados = state.auditoria.isNotEmpty(),
        buscar = { repository.audit(150) },
        aoReceber = { eventos, atual -> atual.copy(auditoria = eventos) },
    )

    fun abrirColaboradores() = navegarEAtualizar(
        destino = AdminDestination.COLLABORATORS,
        temDados = state.colaboradores.isNotEmpty(),
        aoNavegar = { it.copy(colaboradorSelecionado = null) },
        buscar = { repository.collaborators() },
        aoReceber = { pessoas, atual -> atual.copy(colaboradores = pessoas) },
    )

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
                    state = state.copy(
                        carregando = false,
                        destination = AdminDestination.COLLABORATORS,
                        colaboradores = upsertCollaborator(state.colaboradores, colaborador),
                        colaboradorSelecionado = colaborador,
                        mensagem = "${colaborador.nome} cadastrado. Gere um código quando ele for tomar café.",
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

    fun voltarColaboradores() {
        state = state.copy(
            destination = AdminDestination.COLLABORATORS,
            colaboradorSelecionado = null,
            erro = null,
        )
    }

    fun abrirConfiguracoes() = navegarEAtualizar(
        destino = AdminDestination.SETTINGS,
        temDados = state.regrasCafe.isNotEmpty(),
        buscar = { repository.coffeeRules() },
        aoReceber = { regras, atual -> atual.copy(regrasCafe = regras) },
    )

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

    fun abrirCodigos() = navegarEAtualizar(
        destino = AdminDestination.ACCESS_CODES,
        temDados = state.colaboradores.isNotEmpty(),
        // O código em claro da emissão anterior não pode reabrir o diálogo ao
        // voltar à tela.
        aoNavegar = { it.copy(codigoEmitido = null) },
        buscar = { repository.collaborators() to repository.accessCodes() },
        aoReceber = { (pessoas, codigos), atual ->
            atual.copy(colaboradores = pessoas, codigosAtivos = codigos.codigos)
        },
    )

    fun atualizarCodigos() {
        viewModelScope.launch {
            runCatching { repository.accessCodes() }
                .onSuccess { state = state.copy(codigosAtivos = it.codigos) }
        }
    }

    /**
     * Prepara o atalho de emissão do Início sem sair do Início.
     *
     * `abrirCodigos` carregaria os mesmos dados, mas troca a destination — e a
     * tela inicial navegaria sozinha ao abrir. Aqui o carregamento é de fundo:
     * não mexe em `carregando` nem em `erro`, para não desabilitar o resto da
     * tela nem transformar um Worker desatualizado num banner vermelho por
     * cima do painel operacional.
     */
    fun carregarAtalhoDeCodigos(silencioso: Boolean = false) {
        if (state.codigosAtalhoCarregando) return
        viewModelScope.launch {
            state = state.copy(codigosAtalhoCarregando = !silencioso, codigosAtalhoErro = null)
            runCatching { repository.collaborators() to repository.accessCodes() }
                .onSuccess { (pessoas, codigos) ->
                    state = state.copy(
                        codigosAtalhoCarregando = false,
                        colaboradores = pessoas,
                        codigosAtivos = codigos.codigos,
                    )
                }
                .onFailure {
                    state = state.copy(
                        codigosAtalhoCarregando = false,
                        codigosAtalhoErro = AdminRepository.message(it),
                    )
                }
        }
    }

    /**
     * Emite o passe de café de uma pessoa.
     *
     * O código volta em claro nesta resposta e fica no estado até o Admin sair
     * da tela: é o que ele lê em voz alta. A lista de códigos vivos é recarregada
     * a seguir para o cartão da pessoa passar imediatamente a "aguardando saída".
     */
    fun emitirCodigo(colaborador: Colaborador, motivo: String?) {
        if (state.carregando) return
        viewModelScope.launch {
            state = state.copy(
                carregando = true,
                colaboradorOcupadoId = colaborador.id,
                erro = null,
                mensagem = null,
                codigoEmitido = null,
            )
            runCatching { repository.createAccessCode(colaborador.id, motivo) }
                .onSuccess { codigo ->
                    state = state.copy(
                        carregando = false,
                        colaboradorOcupadoId = null,
                        codigoEmitido = codigo,
                        mensagem = "Código gerado para ${codigo.colaboradorNome}.",
                        erro = null,
                    )
                    atualizarCodigos()
                }
                .onFailure {
                    state = state.copy(
                        carregando = false,
                        colaboradorOcupadoId = null,
                        erro = AdminRepository.message(it),
                    )
                }
        }
    }

    /**
     * Abre a pausa de [colaborador] sem passar pelo quiosque.
     *
     * É a saída para quando o fluxo normal já falhou: a pessoa perdeu o código,
     * ele expirou antes de ser usado, ou o quiosque estava fora do ar. Quem
     * autentica a operação é a sessão de quem a executa, e não um código
     * apresentado — por isso o motivo é obrigatório, fica gravado quem fez, e o
     * evento vai para a auditoria. É a diferença entre "o sistema confirmou" e
     * "alguém afirmou", e ela precisa estar registada.
     */
    fun registrarPausaManual(colaborador: Colaborador, motivo: String) {
        // O servidor exige 3 (z.string().min(3)). Validar 2 aqui deixava passar
        // um motivo que ia ser recusado do outro lado -- e a pessoa via um erro
        // de validação vindo da rede em vez de um aviso imediato no campo.
        if (motivo.trim().length < MOTIVO_MANUAL_MINIMO) {
            state = state.copy(erro = "Informe o motivo do registro manual.")
            return
        }
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = null)
            runCatching { repository.iniciarPausaManual(colaborador.id, motivo) }
                .onSuccess { resposta ->
                    state = state.copy(
                        carregando = false,
                        mensagem = "Saída registrada manualmente para ${colaborador.nome}.",
                        manualPunchResult = ManualPunchResult(colaborador.nome, ManualPunchType.INICIO, resposta.inicioLocal),
                    )
                }
                .onFailure { state = state.copy(carregando = false, erro = AdminRepository.message(it)) }
        }
    }

    fun limparRegistroManual() {
        state = state.copy(manualPunchResult = null)
    }

    fun cancelarCodigo(colaborador: Colaborador) {
        if (state.carregando) return
        viewModelScope.launch {
            state = state.copy(
                carregando = true,
                colaboradorOcupadoId = colaborador.id,
                erro = null,
                mensagem = null,
            )
            runCatching { repository.cancelAccessCode(colaborador.id) }
                .onSuccess {
                    state = state.copy(
                        carregando = false,
                        colaboradorOcupadoId = null,
                        codigoEmitido = if (state.codigoEmitido?.colaboradorId == colaborador.id) {
                            null
                        } else {
                            state.codigoEmitido
                        },
                        mensagem = "Código de ${colaborador.nome} cancelado.",
                        erro = null,
                    )
                    atualizarCodigos()
                }
                .onFailure {
                    state = state.copy(
                        carregando = false,
                        colaboradorOcupadoId = null,
                        erro = AdminRepository.message(it),
                    )
                }
        }
    }

    fun limparCodigoEmitido() {
        state = state.copy(codigoEmitido = null, mensagem = null, erro = null)
    }

    fun voltarHome() {
        state = state.copy(
            destination = AdminDestination.HOME,
            selecionado = null,
            colaboradorSelecionado = null,
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
            AdminDestination.ACCESS_CODES -> abrirCodigos()
            AdminDestination.USER_DETAIL -> {
                val user = state.usuarios.firstOrNull { it.id == userId }
                if (user != null) selecionarUsuario(user) else state = state.copy(destination = AdminDestination.HOME)
            }
            AdminDestination.NEW_ACCOUNT -> state = state.copy(destination = AdminDestination.NEW_ACCOUNT)
            AdminDestination.NEW_COLLABORATOR -> state = state.copy(destination = AdminDestination.NEW_COLLABORATOR)
            AdminDestination.LOADING,
            AdminDestination.LOGIN,
            AdminDestination.FIRST_SETUP -> Unit
        }
    }

    fun limparFeedback() {
        state = state.copy(erro = null, mensagem = null)
    }

    /**
     * Purga manual do cache local (listas de usuários, regras, dispositivos,
     * colaboradores e resumo em memória nesta instância). Não afeta sessão
     * nem fila de sincronização offline -- só força a próxima leitura de
     * cada lista a vir do servidor em vez de reaproveitar o que já foi
     * buscado nesta sessão de app.
     */
    fun purgarCacheLocal() {
        repository.purgeLocalCaches()
        state = state.copy(mensagem = "Cache local limpo. Os próximos dados virão direto do servidor.")
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
