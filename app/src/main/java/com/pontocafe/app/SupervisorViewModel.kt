package com.pontocafe.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pontocafe.app.data.PausaSupervisor
import com.pontocafe.app.data.SupervisorRepository
import kotlinx.coroutines.launch


enum class SupervisorDestination { LOGIN, AO_VIVO, HISTORICO }

data class SupervisorUiState(
    val destination: SupervisorDestination = SupervisorDestination.LOGIN,
    val carregando: Boolean = false,
    val pausasAtivas: List<PausaSupervisor> = emptyList(),
    val historico: List<PausaSupervisor> = emptyList(),
    val erro: String? = null,
)

class SupervisorViewModel(private val repository: SupervisorRepository) : ViewModel() {
    var state by mutableStateOf(
        SupervisorUiState(
            destination = if (repository.hasSession()) SupervisorDestination.AO_VIVO else SupervisorDestination.LOGIN,
        ),
    )
        private set

    init {
        if (repository.hasSession()) atualizarAoVivo()
    }

    fun login(email: String, senha: String) {
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null)
            runCatching { repository.signIn(email.trim().lowercase(), senha) }
                .onSuccess { atualizarAoVivoInterno() }
                .onFailure {
                    state = state.copy(carregando = false, erro = SupervisorRepository.message(it))
                }
        }
    }

    fun atualizarAoVivo() {
        viewModelScope.launch { atualizarAoVivoInterno() }
    }

    private suspend fun atualizarAoVivoInterno() {
        runCatching { repository.pausasAtivas() }
            .onSuccess {
                state = state.copy(
                    destination = SupervisorDestination.AO_VIVO,
                    carregando = false,
                    pausasAtivas = it,
                    erro = null,
                )
            }
            .onFailure {
                repository.clearSession()
                state = SupervisorUiState(
                    destination = SupervisorDestination.LOGIN,
                    erro = "Sua sessão expirou ou não possui acesso de supervisor.",
                )
            }
    }

    fun abrirHistorico() {
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null)
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

    fun voltarAoVivo() {
        state = state.copy(destination = SupervisorDestination.AO_VIVO, erro = null)
        atualizarAoVivo()
    }

    fun sair() {
        viewModelScope.launch {
            repository.signOut()
            state = SupervisorUiState(destination = SupervisorDestination.LOGIN)
        }
    }

    fun formatarTempo(segundos: Int): String = "%02d:%02d".format(segundos / 60, segundos % 60)
}

class SupervisorViewModelFactory(private val creator: () -> SupervisorViewModel) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(SupervisorViewModel::class.java))
        return creator() as T
    }
}
