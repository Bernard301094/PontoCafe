package com.pontocafe.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import java.util.WeakHashMap
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Rascunhos de formulários mantidos exclusivamente em memória.
 *
 * Campos sensíveis como senha nunca são persistidos em SharedPreferences,
 * SavedState/Bundle ou banco. Eles sobrevivem apenas enquanto o processo da
 * aplicação continuar vivo, o suficiente para bloquear/desbloquear a área
 * restrita ao minimizar o app sem apagar o que já foi digitado.
 */
data class AccountRegistrationDraft(
    val nome: String = "",
    val email: String = "",
    val senha: String = "",
    val confirmarSenha: String = "",
    val perfil: String = "SUPERVISOR",
    val erroLocal: String? = null,
)

data class CollaboratorRegistrationDraft(
    val nome: String = "",
    val setor: String = "Produção",
    val turno: String = "A",
)

class AccountRegistrationDraftState internal constructor() {
    var draft by mutableStateOf(AccountRegistrationDraft())
        private set

    private var submitted = false

    fun update(updated: AccountRegistrationDraft) {
        draft = updated
        submitted = false
    }

    fun setValidationError(error: String?) {
        draft = draft.copy(erroLocal = error)
        if (error != null) submitted = false
    }

    fun markSubmitted() {
        draft = draft.copy(erroLocal = null)
        submitted = true
    }

    fun markServerFailure() {
        submitted = false
    }

    fun reset() {
        draft = AccountRegistrationDraft()
        submitted = false
    }

    fun prepareForDisplay(serverError: String?, loading: Boolean) {
        if (submitted && !loading && serverError == null) reset()
    }
}

class CollaboratorRegistrationDraftState internal constructor() {
    var draft by mutableStateOf(CollaboratorRegistrationDraft())
        private set

    private var submitted = false

    fun update(updated: CollaboratorRegistrationDraft) {
        draft = updated
        submitted = false
    }

    fun markSubmitted() {
        submitted = true
    }

    fun markServerFailure() {
        submitted = false
    }

    fun reset() {
        draft = CollaboratorRegistrationDraft()
        submitted = false
    }

    fun prepareForDisplay(serverError: String?, loading: Boolean) {
        if (submitted && !loading && serverError == null) reset()
    }
}

private data class DraftSubmissionSnapshot(
    val loading: Boolean,
    val destination: String,
    val error: String?,
)

/**
 * Observa a operação no próprio escopo do ViewModel, que continua ativo quando
 * a tela protegida é substituída pelo desbloqueio. Assim o resultado da rede
 * pode limpar ou preservar o rascunho mesmo com o app minimizado.
 */
fun AdminViewModel.trackAccountDraftSubmission(draftState: AccountRegistrationDraftState) {
    viewModelScope.launch {
        val result = snapshotFlow {
            DraftSubmissionSnapshot(state.carregando, state.destination.name, state.erro)
        }
            .dropWhile { !it.loading }
            .first { !it.loading }

        if (result.error == null && result.destination != AdminDestination.NEW_ACCOUNT.name) {
            draftState.reset()
        } else {
            draftState.markServerFailure()
        }
    }
}

fun AdminViewModel.trackCollaboratorDraftSubmission(draftState: CollaboratorRegistrationDraftState) {
    viewModelScope.launch {
        val result = snapshotFlow {
            DraftSubmissionSnapshot(state.carregando, state.destination.name, state.erro)
        }
            .dropWhile { !it.loading }
            .first { !it.loading }

        if (result.error == null && result.destination == AdminDestination.BIOMETRIC_ENROLLMENT.name) {
            draftState.reset()
        } else {
            draftState.markServerFailure()
        }
    }
}

fun SupervisorViewModel.trackCollaboratorDraftSubmission(draftState: CollaboratorRegistrationDraftState) {
    viewModelScope.launch {
        val result = snapshotFlow {
            DraftSubmissionSnapshot(state.carregando, state.destination.name, state.erro)
        }
            .dropWhile { !it.loading }
            .first { !it.loading }

        if (result.error == null && result.destination == SupervisorDestination.BIOMETRIA.name) {
            draftState.reset()
        } else {
            draftState.markServerFailure()
        }
    }
}

/**
 * O ViewModel da Activity sobrevive quando a área protegida é substituída pela
 * tela de desbloqueio. O WeakHashMap usa esse ViewModel apenas como chave de
 * sessão e não impede sua coleta quando a Activity é destruída.
 */
object FormDraftRegistry {
    private val accountDrafts = WeakHashMap<AdminViewModel, AccountRegistrationDraftState>()
    private val adminCollaboratorDrafts = WeakHashMap<AdminViewModel, CollaboratorRegistrationDraftState>()
    private val supervisorCollaboratorDrafts = WeakHashMap<SupervisorViewModel, CollaboratorRegistrationDraftState>()

    @Synchronized
    fun account(viewModel: AdminViewModel): AccountRegistrationDraftState =
        accountDrafts.getOrPut(viewModel) { AccountRegistrationDraftState() }

    @Synchronized
    fun adminCollaborator(viewModel: AdminViewModel): CollaboratorRegistrationDraftState =
        adminCollaboratorDrafts.getOrPut(viewModel) { CollaboratorRegistrationDraftState() }

    @Synchronized
    fun supervisorCollaborator(viewModel: SupervisorViewModel): CollaboratorRegistrationDraftState =
        supervisorCollaboratorDrafts.getOrPut(viewModel) { CollaboratorRegistrationDraftState() }

    @Synchronized
    fun clearAdmin(viewModel: AdminViewModel) {
        accountDrafts.remove(viewModel)?.reset()
        adminCollaboratorDrafts.remove(viewModel)?.reset()
    }

    @Synchronized
    fun clearSupervisor(viewModel: SupervisorViewModel) {
        supervisorCollaboratorDrafts.remove(viewModel)?.reset()
    }
}
