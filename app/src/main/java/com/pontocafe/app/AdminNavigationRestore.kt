package com.pontocafe.app

/**
 * Restaura a última tela administrativa sem persistir dados sensíveis.
 * Telas que dependem de dados remotos usam os métodos normais do ViewModel
 * para recarregar o conteúdo antes de exibi-lo.
 */
fun AdminViewModel.restaurarNavegacao(
    destinationName: String?,
    userId: String?,
    collaboratorId: String?,
) {
    val destination = destinationName?.let {
        runCatching { AdminDestination.valueOf(it) }.getOrNull()
    } ?: return

    when (destination) {
        AdminDestination.NEW_ACCOUNT -> abrirNovaConta()
        AdminDestination.USER_DETAIL -> {
            state.usuarios.firstOrNull { it.id == userId }?.let(::selecionarUsuario)
        }
        AdminDestination.AUTHORIZATION -> abrirAutorizacao()
        AdminDestination.SETTINGS -> abrirConfiguracoes()
        AdminDestination.COLLABORATORS -> abrirColaboradores()
        AdminDestination.NEW_COLLABORATOR -> abrirNovoColaborador()
        AdminDestination.BIOMETRIC_ENROLLMENT -> {
            state.colaboradores.firstOrNull { it.id == collaboratorId }
                ?.let(::cadastrarOuAtualizarRosto)
                ?: abrirColaboradores()
        }
        AdminDestination.AUDIT -> abrirAuditoria()
        AdminDestination.HOME,
        AdminDestination.LOADING,
        AdminDestination.LOGIN,
        AdminDestination.FIRST_SETUP -> Unit
    }
}
