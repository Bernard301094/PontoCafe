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
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import retrofit2.HttpException


data class AdminDeviceUiState(
    val carregando: Boolean = false,
    val dispositivos: List<AdminDevice> = emptyList(),
    val health: SystemHealthResponse? = null,
    val appStatus: AppStatusResponse? = null,
    val tokenGerado: String? = null,
    val tokenDeviceId: String? = null,
    val tokenDeviceName: String? = null,
    val tokenRotacionado: Boolean = false,
    val pinAtualizadoDeviceId: String? = null,
    val mensagem: String? = null,
    val erro: String? = null,
)

class AdminDeviceViewModel(
    private val repository: AdminRepository,
) : ViewModel() {
    var state by mutableStateOf(AdminDeviceUiState())
        private set

    private var pendingRegistrationKey: String? = null
    private var pendingRegistrationFingerprint: String? = null

    fun carregar() {
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null)
            runCatching {
                coroutineScope {
                    val devices = async { repository.devices() }
                    val health = async { runCatching { repository.health() }.getOrNull() }
                    val appStatus = async { runCatching { repository.appStatus() }.getOrNull() }
                    Triple(devices.await(), health.await(), appStatus.await())
                }
            }
                .onSuccess { (devices, health, appStatus) ->
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

    private suspend fun atualizarListaOu(fallback: List<AdminDevice>): Pair<List<AdminDevice>, Boolean> =
        runCatching { repository.devices() }
            .fold(
                onSuccess = { it to true },
                onFailure = { fallback to false },
            )

    private fun mensagemComRefresh(base: String, atualizado: Boolean): String =
        if (atualizado) base else "$base A lista não pôde ser atualizada agora; toque em Atualizar quando a conexão voltar."

    private fun registrationFingerprint(nome: String, pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest("$nome\u0000$pin".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }

    private fun idempotencyKeyFor(nome: String, pin: String): String {
        val fingerprint = registrationFingerprint(nome, pin)
        if (pendingRegistrationKey == null || pendingRegistrationFingerprint != fingerprint) {
            pendingRegistrationKey = UUID.randomUUID().toString()
            pendingRegistrationFingerprint = fingerprint
        }
        return checkNotNull(pendingRegistrationKey)
    }

    private fun clearPendingRegistration() {
        pendingRegistrationKey = null
        pendingRegistrationFingerprint = null
    }

    private fun shouldDiscardPendingRegistration(error: Throwable): Boolean =
        error is HttpException && error.code() in setOf(400, 409, 422)

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

        val idempotencyKey = idempotencyKeyFor(cleanName, cleanPin)
        viewModelScope.launch {
            state = state.copy(
                carregando = true,
                erro = null,
                mensagem = null,
                tokenGerado = null,
                tokenDeviceId = null,
            )
            runCatching { repository.createDevice(cleanName, cleanPin, idempotencyKey) }
                .onSuccess { created ->
                    clearPendingRegistration()
                    val (devices, refreshed) = atualizarListaOu(state.dispositivos)
                    state = state.copy(
                        carregando = false,
                        dispositivos = devices,
                        tokenGerado = created.token,
                        tokenDeviceId = created.id,
                        tokenDeviceName = created.nome,
                        tokenRotacionado = false,
                        mensagem = mensagemComRefresh(
                            "Dispositivo criado com PIN próprio. O token ficará disponível no cartão enquanto aguarda ativação.",
                            refreshed,
                        ),
                        erro = null,
                    )
                }
                .onFailure { error ->
                    if (shouldDiscardPendingRegistration(error)) clearPendingRegistration()
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
            state = state.copy(
                carregando = true,
                erro = null,
                mensagem = null,
                pinAtualizadoDeviceId = null,
            )
            runCatching { repository.updateDevicePin(dispositivo.id, cleanPin) }
                .onSuccess {
                    val local = state.dispositivos.map { item ->
                        if (item.id == dispositivo.id) item.copy(pinConfigurado = true) else item
                    }
                    val (devices, refreshed) = atualizarListaOu(local)
                    state = state.copy(
                        carregando = false,
                        dispositivos = devices,
                        pinAtualizadoDeviceId = dispositivo.id,
                        mensagem = mensagemComRefresh(
                            "PIN de ${dispositivo.nome} atualizado com sucesso e já está valendo.",
                            refreshed,
                        ),
                        erro = null,
                    )
                }
                .onFailure { error ->
                    state = state.copy(
                        carregando = false,
                        pinAtualizadoDeviceId = null,
                        erro = AdminRepository.message(error),
                    )
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
            runCatching { repository.renameDevice(dispositivo.id, cleanName) }
                .onSuccess {
                    val local = state.dispositivos.map { item ->
                        if (item.id == dispositivo.id) item.copy(nome = cleanName) else item
                    }
                    val (devices, refreshed) = atualizarListaOu(local)
                    state = state.copy(
                        carregando = false,
                        dispositivos = devices,
                        mensagem = mensagemComRefresh("Dispositivo renomeado para $cleanName.", refreshed),
                        erro = null,
                    )
                }
                .onFailure { state = state.copy(carregando = false, erro = AdminRepository.message(it)) }
        }
    }

    fun desativar(dispositivo: AdminDevice) {
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = null)
            runCatching { repository.deactivateDevice(dispositivo.id) }
                .onSuccess {
                    val local = state.dispositivos.map { item ->
                        if (item.id == dispositivo.id) {
                            item.copy(ativo = false, statusAtivacao = "INATIVO")
                        } else {
                            item
                        }
                    }
                    val (devices, refreshed) = atualizarListaOu(local)
                    state = state.copy(
                        carregando = false,
                        dispositivos = devices,
                        mensagem = mensagemComRefresh(
                            "Acesso de ${dispositivo.nome} bloqueado. Este aparelho não poderá registrar pontos até receber um novo token de ativação.",
                            refreshed,
                        ),
                        erro = null,
                    )
                }
                .onFailure { state = state.copy(carregando = false, erro = AdminRepository.message(it)) }
        }
    }

    fun excluirPermanentemente(dispositivo: AdminDevice) {
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = null)
            runCatching { repository.deleteDevice(dispositivo.id) }
                .onSuccess {
                    val local = state.dispositivos.filterNot { it.id == dispositivo.id }
                    val (devices, refreshed) = atualizarListaOu(local)
                    state = state.copy(
                        carregando = false,
                        dispositivos = devices,
                        mensagem = mensagemComRefresh(
                            "${dispositivo.nome} foi excluído da gestão de dispositivos. O histórico de ponto, quando existente, foi preservado para auditoria.",
                            refreshed,
                        ),
                        erro = null,
                    )
                }
                .onFailure {
                    state = state.copy(
                        carregando = false,
                        erro = AdminRepository.message(it),
                    )
                }
        }
    }

    fun rotacionarToken(dispositivo: AdminDevice) {
        viewModelScope.launch {
            state = state.copy(
                carregando = true,
                erro = null,
                mensagem = null,
                tokenGerado = null,
                tokenDeviceId = null,
            )
            runCatching { repository.rotateDeviceToken(dispositivo.id) }
                .onSuccess { rotated ->
                    val local = state.dispositivos.map { item ->
                        if (item.id == dispositivo.id) {
                            item.copy(
                                ativo = true,
                                statusAtivacao = "AGUARDANDO_ATIVACAO",
                                ativadoEm = null,
                            )
                        } else {
                            item
                        }
                    }
                    val (devices, refreshed) = atualizarListaOu(local)
                    state = state.copy(
                        carregando = false,
                        dispositivos = devices,
                        tokenGerado = rotated.token,
                        tokenDeviceId = rotated.dispositivoId,
                        tokenDeviceName = rotated.nome,
                        tokenRotacionado = true,
                        mensagem = mensagemComRefresh(
                            "Token anterior revogado. O novo token ficará disponível no cartão até a ativação.",
                            refreshed,
                        ),
                        erro = null,
                    )
                }
                .onFailure { state = state.copy(carregando = false, erro = AdminRepository.message(it)) }
        }
    }

    fun limparToken() {
        state = state.copy(
            tokenGerado = null,
            tokenDeviceId = null,
            tokenDeviceName = null,
            tokenRotacionado = false,
            mensagem = null,
        )
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
