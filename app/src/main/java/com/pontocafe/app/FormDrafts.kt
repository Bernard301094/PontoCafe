package com.pontocafe.app

/**
 * Rascunhos de formulários mantidos apenas na memória do ViewModel.
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
