package com.pontocafe.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pontocafe.app.data.AdminRepository
import com.pontocafe.app.data.AdminUser
import com.pontocafe.app.ui.NewAccountInput
import kotlinx.coroutines.launch


enum class AdminDestination {
    LOADING,
    LOGIN,
    FIRST_SETUP,
    HOME,
    NEW_ACCOUNT,
    USER_DETAIL,
}

data class AdminUiState(
    val destination: AdminDestination = AdminDestination.LOADING,
    val carregando: Boolean = false,
    val primeiroAdminNecessario: Boolean = false,
    val instalacaoConfigurada: Boolean = false,
    val usuarios: List<AdminUser> = emptyList(),
    val selecionado: AdminUser? = null,
    val mensagem: String? = null,
    val erro: String? = null,
)

class AdminViewModel(private val repository: AdminRepository) : ViewModel() {
    var state by androidx.compose.runtime.mutableStateOf(AdminUiState())
        private set

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
                            destination = if (setup.primeiroAdminNecessario) {
                                AdminDestination.FIRST_SETUP
                            } else {
                                AdminDestination.LOGIN
                            },
                        )
                    }
                }
                .onFailure {
                    state = state.copy(
                        carregando = false,
                        destination = AdminDestination.LOGIN,
                        erro = AdminRepository.message(it),
                    )
                }
        }
    }

    fun login(email: String, senha: String) {
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = null)
            runCatching { repository.signIn(email.trim().lowercase(), senha) }
                .onSuccess { carregarUsuariosInterno() }
                .onFailure {
                    state = state.copy(carregando = false, erro = AdminRepository.message(it))
                }
        }
    }

    fun criarPrimeiroAdmin(nome: String, email: String, senha: String, chave: String) {
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = null)
            runCatching {
                repository.createFirstAdmin(nome.trim(), email.trim().lowercase(), senha, chave)
            }.onSuccess {
                state = state.copy(
                    carregando = false,
                    primeiroAdminNecessario = false,
                    destination = AdminDestination.LOGIN,
                    mensagem = "Administrador criado. Entre com o e-mail e a senha cadastrados.",
                )
            }.onFailure {
                state = state.copy(carregando = false, erro = AdminRepository.message(it))
            }
        }
    }

    fun abrirNovaConta() {
        state = state.copy(destination = AdminDestination.NEW_ACCOUNT, erro = null, mensagem = null)
    }

    fun criarConta(input: NewAccountInput) {
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = null)
            runCatching {
                repository.createUser(input.nome, input.email, input.senha, input.perfil.name)
            }.onSuccess {
                carregarUsuariosInterno("Conta cadastrada com sucesso.")
            }.onFailure {
                state = state.copy(carregando = false, erro = AdminRepository.message(it))
            }
        }
    }

    fun selecionarUsuario(user: AdminUser) {
        state = state.copy(
            selecionado = user,
            destination = AdminDestination.USER_DETAIL,
            erro = null,
            mensagem = null,
        )
    }

    fun alterarAtivo(user: AdminUser) {
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = null)
            runCatching { repository.setActive(user.id, !user.ativo) }
                .onSuccess {
                    carregarUsuariosInterno(if (user.ativo) "Conta desativada." else "Conta reativada.")
                }
                .onFailure {
                    state = state.copy(carregando = false, erro = AdminRepository.message(it))
                }
        }
    }

    fun redefinirSenha(userId: String, novaSenha: String) {
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = null)
            runCatching { repository.resetPassword(userId, novaSenha) }
                .onSuccess {
                    state = state.copy(
                        carregando = false,
                        mensagem = "Senha redefinida. As sessões dessa conta foram encerradas.",
                    )
                }
                .onFailure {
                    state = state.copy(carregando = false, erro = AdminRepository.message(it))
                }
        }
    }

    fun alterarPerfil(user: AdminUser, perfil: String) {
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = null)
            runCatching { repository.changeProfile(user.id, perfil) }
                .onSuccess { carregarUsuariosInterno("Perfil atualizado.") }
                .onFailure {
                    state = state.copy(carregando = false, erro = AdminRepository.message(it))
                }
        }
    }

    fun voltarHome() {
        state = state.copy(destination = AdminDestination.HOME, selecionado = null, erro = null)
    }

    fun logout() {
        viewModelScope.launch {
            state = state.copy(carregando = true)
            repository.signOut()
            state = AdminUiState(destination = AdminDestination.LOGIN, mensagem = "Sessão encerrada.")
        }
    }

    fun limparAviso() {
        state = state.copy(erro = null, mensagem = null)
    }

    private suspend fun carregarUsuariosInterno(message: String? = null) {
        runCatching { repository.users() }
            .onSuccess {
                state = state.copy(
                    carregando = false,
                    destination = AdminDestination.HOME,
                    usuarios = it,
                    selecionado = null,
                    mensagem = message,
                    erro = null,
                )
            }
            .onFailure {
                repository.clearSession()
                state = state.copy(
                    carregando = false,
                    destination = AdminDestination.LOGIN,
                    erro = "Sua sessão administrativa expirou ou não possui acesso de administrador.",
                )
            }
    }
}

class AdminViewModelFactory(private val creator: () -> AdminViewModel) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(AdminViewModel::class.java))
        return creator() as T
    }
}
