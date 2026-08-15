package com.pontocafe.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pontocafe.app.data.AdminDevice
import com.pontocafe.app.data.AdminRepository
import com.pontocafe.app.data.AppStatusResponse
import com.pontocafe.app.data.SystemHealthResponse
import kotlinx.coroutines.launch


data class AdminDeviceUiState(
    val carregando: Boolean = false,
    val dispositivos: List<AdminDevice> = emptyList(),
    val health: SystemHealthResponse? = null,
    val appStatus: AppStatusResponse? = null,
    val tokenGerado: String? = null,
    val tokenDeviceName: String? = null,
    val tokenRotacionado: Boolean = false,
    val mensagem: String? = null,
    val erro: String? = null,
)

class AdminDeviceViewModel(
    private val repository: AdminRepository,
) : ViewModel() {
    var state by mutableStateOf(AdminDeviceUiState())
        private set

    // A carga é iniciada explicitamente ao abrir a tela de dispositivos.
    // Isso evita uma chamada /admin antes de o usuário terminar o login.
    fun carregar() {
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null)
            runCatching { repository.devices() }
                .onSuccess { devices ->
                    val health = runCatching { repository.health() }.getOrNull()
                    val appStatus = runCatching { repository.appStatus() }.getOrNull()
                    state = state.copy(
                        carregando = false,
                        dispositivos = devices,
                        health = health,
                        appStatus = appStatus,
                        erro = null,
                    )
                }
                .onFailure { error ->
                    state = state.copy(carregando = false, erro = AdminRepository.message(error))
                }
        }
    }

    fun criarDispositivo(nome: String, pin: String) {
        val cleanName = nome.trim()
        val cleanPin = pin.trim()
        if (cleanName.length < 2) {
            state = state.copy(erro = "Informe um nome para o dispositivo.")
            return
        }
        if (!Regex("^\\d{4,12}$").matches(cleanPin)) {
            state = state.copy(erro = "O PIN deve ter entre 4 e 12 números.")
            return
        }

        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = null, tokenGerado = null)
            runCatching {
                val created = repository.createDevice(cleanName, cleanPin)
                created to repository.devices()
            }
                .onSuccess { (created, devices) ->
                    state = state.copy(
                        carregando = false,
                        dispositivos = devices,
                        tokenGerado = created.token,
                        tokenDeviceName = created.nome,
                        tokenRotacionado = false,
                        mensagem = "Dispositivo criado com PIN próprio. Copie o token de ativação agora.",
                    )
                }
                .onFailure { error ->
                    state = state.copy(carregando = false, erro = AdminRepository.message(error))
                }
        }
    }

    fun alterarPin(dispositivo: AdminDevice, pin: String) {
        val cleanPin = pin.trim()
        if (!Regex("^\\d{4,12}$").matches(cleanPin)) {
            state = state.copy(erro = "O PIN deve ter entre 4 e 12 números.")
            return
        }

        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = null)
            runCatching {
                repository.updateDevicePin(dispositivo.id, cleanPin)
                repository.devices()
            }
                .onSuccess { devices ->
                    state = state.copy(
                        carregando = false,
                        dispositivos = devices,
                        mensagem = "PIN de ${dispositivo.nome} atualizado com sucesso.",
                    )
                }
                .onFailure { error ->
                    state = state.copy(carregando = false, erro = AdminRepository.message(error))
                }
        }
    }

    fun renomear(dispositivo: AdminDevice, novoNome: String) {
        val cleanName = novoNome.trim()
        if (cleanName.length < 2) {
            state = state.copy(erro = "Informe um nome com pelo menos 2 caracteres.")
            return
        }
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = null)
            runCatching {
                repository.renameDevice(dispositivo.id, cleanName)
                repository.devices()
            }
                .onSuccess { devices ->
                    state = state.copy(
                        carregando = false,
                        dispositivos = devices,
                        mensagem = "Dispositivo renomeado para $cleanName.",
                    )
                }
                .onFailure { state = state.copy(carregando = false, erro = AdminRepository.message(it)) }
        }
    }

    fun desativar(dispositivo: AdminDevice) {
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = null)
            runCatching {
                repository.deactivateDevice(dispositivo.id)
                repository.devices()
            }
                .onSuccess { devices ->
                    state = state.copy(
                        carregando = false,
                        dispositivos = devices,
                        mensagem = "${dispositivo.nome} foi desativado. O token atual não poderá mais registrar pontos.",
                    )
                }
                .onFailure { state = state.copy(carregando = false, erro = AdminRepository.message(it)) }
        }
    }

    fun rotacionarToken(dispositivo: AdminDevice) {
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = null, tokenGerado = null)
            runCatching {
                val rotated = repository.rotateDeviceToken(dispositivo.id)
                rotated to repository.devices()
            }
                .onSuccess { (rotated, devices) ->
                    state = state.copy(
                        carregando = false,
                        dispositivos = devices,
                        tokenGerado = rotated.token,
                        tokenDeviceName = rotated.nome,
                        tokenRotacionado = true,
                        mensagem = "Token anterior revogado. Use o novo código para ativar novamente este aparelho.",
                    )
                }
                .onFailure { state = state.copy(carregando = false, erro = AdminRepository.message(it)) }
        }
    }

    fun limparToken() {
        state = state.copy(tokenGerado = null, tokenDeviceName = null, tokenRotacionado = false, mensagem = null)
    }

    fun limparAviso() {
        state = state.copy(mensagem = null, erro = null)
    }
}

class AdminDeviceViewModelFactory(
    private val creator: () -> AdminDeviceViewModel,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(AdminDeviceViewModel::class.java))
        return creator() as T
    }
}
