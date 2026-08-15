package com.pontocafe.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pontocafe.app.camera.FaceEmbeddingEngine
import com.pontocafe.app.camera.FaceFrame
import com.pontocafe.app.data.AdminReliabilityRepository
import com.pontocafe.app.data.BiometricSummaryResponse
import com.pontocafe.app.data.CalibrationResponse
import com.pontocafe.app.data.CoffeeRuleV2
import com.pontocafe.app.data.CollaboratorHistoryResponse
import com.pontocafe.app.data.CollaboratorImportItem
import com.pontocafe.app.data.CollaboratorImportResponse
import com.pontocafe.app.data.DiagnosticResponse
import com.pontocafe.app.data.PontoCafeRepository
import com.pontocafe.app.data.SecurePontoOfflineStore
import com.pontocafe.app.data.SyncCenterSnapshot
import kotlinx.coroutines.launch


enum class ReliabilityDestination {
    NONE,
    COLLABORATOR_HISTORY,
    BIOMETRIC_DIAGNOSTICS,
    SYNC_CENTER,
    SYSTEM_DIAGNOSTICS,
}

data class AdminReliabilityUiState(
    val destination: ReliabilityDestination = ReliabilityDestination.NONE,
    val loading: Boolean = false,
    val rules: List<CoffeeRuleV2> = emptyList(),
    val history: CollaboratorHistoryResponse? = null,
    val biometricSummary: BiometricSummaryResponse? = null,
    val calibration: CalibrationResponse? = null,
    val diagnostic: DiagnosticResponse? = null,
    val syncCenter: SyncCenterSnapshot? = null,
    val lastImport: CollaboratorImportResponse? = null,
    val message: String? = null,
    val error: String? = null,
)

class AdminReliabilityViewModel(
    private val repository: AdminReliabilityRepository,
    private val pontoRepository: PontoCafeRepository,
    private val offlineStore: SecurePontoOfflineStore,
    private val embeddingEngine: FaceEmbeddingEngine,
    private val onWorkforceChanged: () -> Unit,
) : ViewModel() {

    var state by mutableStateOf(AdminReliabilityUiState())
        private set

    val faceModelReady: Boolean get() = embeddingEngine.isReady
    val faceModelName: String get() = embeddingEngine.modelName
    val faceModelVersion: String get() = embeddingEngine.modelVersion

    fun loadManagement() {
        if (state.loading) return
        viewModelScope.launch {
            state = state.copy(loading = true, error = null)
            runCatching { repository.coffeeRules() }
                .onSuccess { rules -> state = state.copy(loading = false, rules = rules) }
                .onFailure { state = state.copy(loading = false, error = AdminReliabilityRepository.message(it)) }
        }
    }

    fun saveRule(period: String, start: String, end: String, limitSeconds: Int, active: Boolean) {
        viewModelScope.launch {
            state = state.copy(loading = true, error = null, message = null)
            runCatching { repository.updateCoffeeRule(period, start, end, limitSeconds, active) }
                .onSuccess { updated ->
                    state = state.copy(
                        loading = false,
                        rules = state.rules.map { if (it.periodo == updated.periodo) updated else it },
                        message = "Regra atualizada: ${formatDuration(updated.limiteSegundos)} por pausa.",
                    )
                }
                .onFailure { state = state.copy(loading = false, error = AdminReliabilityRepository.message(it)) }
        }
    }

    fun openHistory(collaboratorId: String, days: Int = 30) {
        viewModelScope.launch {
            state = state.copy(
                destination = ReliabilityDestination.COLLABORATOR_HISTORY,
                loading = true,
                history = null,
                error = null,
                message = null,
            )
            runCatching { repository.collaboratorHistory(collaboratorId, days) }
                .onSuccess { state = state.copy(loading = false, history = it) }
                .onFailure { state = state.copy(loading = false, error = AdminReliabilityRepository.message(it)) }
        }
    }

    fun refreshHistory(days: Int = 30) {
        val id = state.history?.colaborador?.id ?: return
        openHistory(id, days)
    }

    fun importCollaborators(items: List<CollaboratorImportItem>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            state = state.copy(loading = true, error = null, message = null, lastImport = null)
            runCatching { repository.importCollaborators(items) }
                .onSuccess { response ->
                    state = state.copy(
                        loading = false,
                        lastImport = response,
                        message = "Importação concluída: ${response.criados} criado(s), ${response.existentes} já existente(s).",
                    )
                    onWorkforceChanged()
                }
                .onFailure { state = state.copy(loading = false, error = AdminReliabilityRepository.message(it)) }
        }
    }

    fun updateBulk(ids: List<String>, sector: String?, shift: String?, active: Boolean?) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            state = state.copy(loading = true, error = null, message = null)
            runCatching { repository.updateCollaborators(ids, sector, shift, active) }
                .onSuccess { response ->
                    state = state.copy(
                        loading = false,
                        message = "${response.atualizados} colaborador(es) atualizado(s).",
                    )
                    onWorkforceChanged()
                }
                .onFailure { state = state.copy(loading = false, error = AdminReliabilityRepository.message(it)) }
        }
    }

    fun openBiometricDiagnostics() {
        viewModelScope.launch {
            state = state.copy(
                destination = ReliabilityDestination.BIOMETRIC_DIAGNOSTICS,
                loading = true,
                biometricSummary = null,
                calibration = null,
                error = null,
            )
            runCatching { repository.biometricSummary() }
                .onSuccess { state = state.copy(loading = false, biometricSummary = it) }
                .onFailure { state = state.copy(loading = false, error = AdminReliabilityRepository.message(it)) }
        }
    }

    fun calibrate(collaboratorId: String, frame: FaceFrame) {
        if (state.loading || !embeddingEngine.isReady) return
        viewModelScope.launch {
            state = state.copy(loading = true, calibration = null, error = null, message = "Processando amostra de calibração...")
            runCatching {
                val embedding = embeddingEngine.embed(frame)
                repository.calibrate(
                    collaboratorId = collaboratorId,
                    embedding = embedding,
                    model = embeddingEngine.modelName,
                    modelVersion = embeddingEngine.modelVersion,
                )
            }.onSuccess { result ->
                state = state.copy(
                    loading = false,
                    calibration = result,
                    message = if (result.aprovado) {
                        "Amostra reconhecida com margem segura."
                    } else {
                        "Amostra abaixo dos critérios atuais. Repita em outra condição de luz/ângulo."
                    },
                )
            }.onFailure { state = state.copy(loading = false, message = null, error = AdminReliabilityRepository.message(it)) }
        }
    }

    fun deleteBiometric(collaboratorId: String) {
        viewModelScope.launch {
            state = state.copy(loading = true, error = null, message = null)
            runCatching { repository.deleteBiometric(collaboratorId) }
                .onSuccess {
                    state = state.copy(loading = false, message = "Biometria excluída com sucesso.")
                    onWorkforceChanged()
                    state.history?.takeIf { it.colaborador.id == collaboratorId }?.let { openHistory(collaboratorId, state.history?.periodoDias ?: 30) }
                }
                .onFailure { state = state.copy(loading = false, error = AdminReliabilityRepository.message(it)) }
        }
    }

    fun runRetentionCleanup() {
        viewModelScope.launch {
            state = state.copy(loading = true, error = null, message = null)
            runCatching { repository.runRetentionCleanup() }
                .onSuccess {
                    state = state.copy(
                        loading = false,
                        message = "Retenção executada: ${it.removidos} biometria(s) removida(s). Política: ${it.retencaoDias} dias.",
                    )
                    openBiometricDiagnostics()
                }
                .onFailure { state = state.copy(loading = false, error = AdminReliabilityRepository.message(it)) }
        }
    }

    fun openSyncCenter() {
        state = state.copy(
            destination = ReliabilityDestination.SYNC_CENTER,
            syncCenter = offlineStore.syncCenterSnapshot(),
            error = null,
            message = null,
        )
    }

    fun syncPending() {
        if (state.loading) return
        val pending = offlineStore.pendingEvents()
        if (pending.isEmpty()) {
            state = state.copy(syncCenter = offlineStore.syncCenterSnapshot(), message = "Não há registros pendentes.")
            return
        }
        viewModelScope.launch {
            state = state.copy(loading = true, error = null, message = null)
            runCatching { pontoRepository.sincronizarOffline(pending.take(100)) }
                .onSuccess { response ->
                    offlineStore.recordSyncResults(response.resultados)
                    offlineStore.removeProcessed(response.processados)
                    offlineStore.markServerOk()
                    state = state.copy(
                        loading = false,
                        syncCenter = offlineStore.syncCenterSnapshot(),
                        message = if (offlineStore.pendingCount() == 0) {
                            "Todos os registros foram sincronizados."
                        } else {
                            "Sincronização concluída. Alguns registros ainda exigem atenção."
                        },
                    )
                }
                .onFailure {
                    state = state.copy(
                        loading = false,
                        syncCenter = offlineStore.syncCenterSnapshot(),
                        error = PontoCafeRepository.mensagemErro(it),
                    )
                }
        }
    }

    fun openSystemDiagnostics() {
        viewModelScope.launch {
            state = state.copy(
                destination = ReliabilityDestination.SYSTEM_DIAGNOSTICS,
                loading = true,
                diagnostic = null,
                error = null,
                message = null,
            )
            runCatching { repository.diagnostic() }
                .onSuccess { state = state.copy(loading = false, diagnostic = it) }
                .onFailure { state = state.copy(loading = false, error = AdminReliabilityRepository.message(it)) }
        }
    }

    fun closeDetail() {
        state = state.copy(
            destination = ReliabilityDestination.NONE,
            history = null,
            calibration = null,
            diagnostic = null,
            syncCenter = null,
            error = null,
            message = null,
        )
    }

    fun clearFeedback() {
        state = state.copy(error = null, message = null)
    }

    companion object {
        fun formatDuration(seconds: Int): String = "%02d:%02d".format(seconds / 60, seconds % 60)
    }
}

class AdminReliabilityViewModelFactory(
    private val creator: () -> AdminReliabilityViewModel,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(AdminReliabilityViewModel::class.java))
        return creator() as T
    }
}
