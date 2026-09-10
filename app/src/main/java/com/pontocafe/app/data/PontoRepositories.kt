package com.pontocafe.app.data

/**
 * Repositórios de vida longa, por escopo de sessão.
 *
 * Três telas criavam o seu com `remember { XApiClient.create(...) }`. `remember`
 * vive o que o composable vive: ao sair da tela o repositório era descartado e,
 * ao voltar, nascia outro com o cache vazio. Toda visita refazia as mesmas
 * chamadas — e com `latenciaBancoMs` na casa dos 850 ms medidos em /health, duas
 * consultas seguidas já são os "alguns segundos" de tela vazia.
 *
 * O cache em si estava certo: AdminRepository invalida por mutação, não por
 * tempo. O problema era onde ele morava.
 *
 * A chave é o escopo do SecureAdminSessionStore ("admin" / "supervisor") porque é
 * o que separa duas sessões distintas. Sem isso, um cache criado como Admin
 * sobreviveria a um login de Supervisor no mesmo aparelho e devolveria dados que
 * a nova sessão não deveria ver.
 */
object PontoRepositories {
    private val adminRepositories = mutableMapOf<String, AdminRepository>()
    private val supervisorRepositories = mutableMapOf<String, SupervisorRepository>()

    @Synchronized
    fun admin(sessionStore: SecureAdminSessionStore): AdminRepository =
        adminRepositories.getOrPut(sessionStore.scope) { AdminApiClient.create(sessionStore) }

    @Synchronized
    fun supervisor(sessionStore: SecureAdminSessionStore): SupervisorRepository =
        supervisorRepositories.getOrPut(sessionStore.scope) { SupervisorApiClient.create(sessionStore) }

    /**
     * Chamar ao encerrar sessão. Um repositório guarda listas de colaboradores e
     * usuários; mantê-lo depois do logout deixaria esses dados acessíveis para
     * quem entrasse em seguida no mesmo aparelho.
     */
    @Synchronized
    fun clear(scope: String) {
        adminRepositories.remove(scope)
        supervisorRepositories.remove(scope)
    }

    @Synchronized
    fun clearAll() {
        adminRepositories.clear()
        supervisorRepositories.clear()
    }
}
