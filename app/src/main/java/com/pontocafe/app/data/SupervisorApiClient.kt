package com.pontocafe.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pontocafe.app.BuildConfig
import java.security.cert.CertPathValidatorException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import okhttp3.Interceptor
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query


data class PausaSupervisor(
    val id: String,
    val periodo: String,
    val data: String? = null,
    val inicioLocal: String,
    val fimLocal: String? = null,
    val limiteSegundos: Int,
    /**
     * Tolerância que correu antes de o limite começar a contar. Todo cálculo de
     * "está atrasado?" no Supervisor tem de somar isto ao limite — senão a
     * pessoa aparece em vermelho um minuto antes da hora.
     */
    val carenciaSegundos: Int = 0,
    val foraHorario: Boolean,
    val tempoSegundos: Int? = null,
    val duracaoSegundos: Int? = null,
    /** Tempo já descontado da carência: é este que se compara ao limite. */
    val tempoContadoSegundos: Int? = null,
    val emCarencia: Boolean? = null,
    val excedeuLimite: Boolean? = null,
    val colaboradorId: String,
    val nome: String,
    val setor: String?,
    val clienteAtualizadoEmMillis: Long = 0L,
)

data class PausasSupervisorResponse(val pausas: List<PausaSupervisor>)

data class ReportSummary(
    val totalPausas: Int,
    val colaboradores: Int,
    val mediaSegundos: Int?,
    val acimaLimite: Int,
    val foraHorario: Int,
)
data class ReportDay(
    val data: String,
    val pausas: Int,
    val acimaLimite: Int,
    val foraHorario: Int,
)
data class ReportDelay(
    val colaboradorId: String,
    val nome: String,
    val ocorrencias: Int,
    val maiorDuracaoSegundos: Int,
    val excessoTotalSegundos: Int,
)
data class ReportPeriod(val inicio: String, val fim: String)
data class SupervisorReportResponse(
    val periodo: ReportPeriod,
    val resumo: ReportSummary,
    val porDia: List<ReportDay>,
    val maioresAtrasos: List<ReportDelay>,
)

data class CollaboratorMutationResponse(
    val ok: Boolean = true,
    val excluido: Boolean? = null,
)

data class TemporaryPasswordChangeRequest(val newPassword: String)
data class TemporaryPasswordChangeResponse(val ok: Boolean, val mustChangePassword: Boolean)

interface SupervisorApi {
    @POST("api/auth/sign-in/email") suspend fun signIn(@Body body: SignInRequest): Response<SignInResponse>
    @POST("api/auth/change-temporary-password")
    suspend fun changeTemporaryPassword(@Body body: TemporaryPasswordChangeRequest): TemporaryPasswordChangeResponse
    @POST("api/auth/sign-out") suspend fun signOut(): Response<Unit>
    @GET("supervisor/pausas/ativas") suspend fun pausasAtivas(): PausasSupervisorResponse
    @GET("supervisor/pausas") suspend fun historico(@Query("data") data: String? = null): PausasSupervisorResponse
    @GET("supervisor/relatorios/resumo") suspend fun report(
        @Query("inicio") inicio: String,
        @Query("fim") fim: String,
    ): SupervisorReportResponse
    @GET("supervisor/relatorios/csv") suspend fun reportCsv(
        @Query("inicio") inicio: String,
        @Query("fim") fim: String,
    ): ResponseBody
    @GET("supervisor/codigos") suspend fun accessCodes(): AccessCodesResponse
    @POST("supervisor/codigos") suspend fun createAccessCode(
        @Body body: CreateAccessCodeRequest,
    ): AccessCodeCreatedResponse
    @POST("supervisor/codigos/cancelar") suspend fun cancelAccessCode(
        @Body body: CancelAccessCodeRequest,
    ): CancelAccessCodeResponse
    // Ambas existem no Worker: finalizar desde 88bc890, iniciar desde a migração
    // 011. Ver backend/src/routes/manual-pause-routes.ts.
    @POST("supervisor/pausas/manual/iniciar") suspend fun iniciarPausaManual(
        @Body body: RegistrarPausaManualRequest,
    ): RegistrarPausaManualResponse
    @POST("supervisor/pausas/manual/finalizar") suspend fun finalizarPausaManual(
        @Body body: FinalizarPausaManualRequest,
    ): FinalizarPausaManualResponse
    @GET("gestao/colaboradores") suspend fun collaborators(): ColaboradoresResponse
    @POST("gestao/colaboradores") suspend fun createCollaborator(@Body body: CreateCollaboratorRequest): Colaborador
    @POST("gestao/colaboradores/{id}/excluir") suspend fun deleteCollaborator(@Path("id") id: String): CollaboratorMutationResponse
}

object SupervisorPasswordChangeRuntime {
    var required by mutableStateOf(false)
        private set
    var submitting by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var success by mutableStateOf(false)
        private set

    private var repository: SupervisorRepository? = null

    internal fun bind(repository: SupervisorRepository) {
        this.repository = repository
    }

    internal fun requireChange() {
        required = true
        success = false
        error = null
    }

    internal fun clear() {
        required = false
        submitting = false
        error = null
        success = false
    }

    fun dismissError() {
        error = null
    }

    suspend fun submit(newPassword: String): Boolean {
        val bound = repository ?: run {
            error = "Não foi possível acessar a sessão do Supervisor."
            return false
        }
        submitting = true
        error = null
        success = false
        return try {
            bound.changeTemporaryPassword(newPassword)
            success = true
            required = false
            true
        } catch (throwable: Throwable) {
            error = SupervisorRepository.message(throwable)
            false
        } finally {
            submitting = false
        }
    }
}

class SupervisorRepository(
    private val api: SupervisorApi,
    private val supervisorSessionStore: SecureAdminSessionStore,
) {
    private fun supervisorToken(): String? = supervisorSessionStore.read()?.takeIf { it.isNotBlank() }

    fun activeToken(): String? = supervisorToken()
    fun hasSession(): Boolean = supervisorToken() != null
    fun usingAdminSession(): Boolean = false

    suspend fun signIn(email: String, senha: String) {
        val response = api.signIn(SignInRequest(email = email, password = senha))
        if (!response.isSuccessful) throw HttpException(response)
        val bearer = response.headers()["set-auth-token"]
            ?: error("O servidor não retornou a sessão do supervisor.")
        supervisorSessionStore.save(bearer)
    }

    suspend fun changeTemporaryPassword(newPassword: String) {
        api.changeTemporaryPassword(TemporaryPasswordChangeRequest(newPassword))
        SupervisorPasswordChangeRuntime.clear()
    }

    suspend fun pausasAtivas(): List<PausaSupervisor> {
        val pausas = api.pausasAtivas().pausas
        val atualizadoEm = System.currentTimeMillis()
        return pausas.map { pausa -> pausa.copy(clienteAtualizadoEmMillis = atualizadoEm) }
    }

    suspend fun historico(data: String? = null) = api.historico(data).pausas
    suspend fun report(inicio: String, fim: String) = api.report(inicio, fim)
    suspend fun reportCsv(inicio: String, fim: String): ByteArray = api.reportCsv(inicio, fim).bytes()
    suspend fun accessCodes(): AccessCodesResponse = api.accessCodes()
    suspend fun createAccessCode(colaboradorId: String, motivo: String?) =
        api.createAccessCode(CreateAccessCodeRequest(colaboradorId, motivo?.trim()?.ifBlank { null }))
    suspend fun cancelAccessCode(colaboradorId: String) =
        api.cancelAccessCode(CancelAccessCodeRequest(colaboradorId))
    suspend fun iniciarPausaManual(colaboradorId: String, motivo: String) =
        api.iniciarPausaManual(RegistrarPausaManualRequest(colaboradorId, motivo.trim()))
    suspend fun finalizarPausaManual(colaboradorId: String, motivo: String) =
        api.finalizarPausaManual(FinalizarPausaManualRequest(colaboradorId, motivo.trim()))
    suspend fun collaborators() = api.collaborators().colaboradores
        .sortedBy { it.nome.lowercase() }

    suspend fun createCollaborator(name: String, sector: String?, shift: String?) = api.createCollaborator(
        CreateCollaboratorRequest(
            nome = name.trim(),
            setor = sector?.trim()?.ifBlank { null },
            turno = shift?.trim()?.ifBlank { null },
        ),
    )

    suspend fun deleteCollaborator(collaboratorId: String) = api.deleteCollaborator(collaboratorId)

    suspend fun signOutSupervisor() {
        if (supervisorToken() != null) {
            runCatching { api.signOut() }
            supervisorSessionStore.clear()
        }
        SupervisorPasswordChangeRuntime.clear()
    }

    fun clearActiveSession() {
        supervisorSessionStore.clear()
        SupervisorPasswordChangeRuntime.clear()
    }

    companion object {
        private fun apiErrorCode(error: Throwable): String? {
            if (error !is HttpException) return null
            val errorBody = error.response()?.errorBody() ?: return null
            return runCatching {
                val source = errorBody.source()
                source.request(Long.MAX_VALUE)
                val text = source.buffer.clone().readUtf8()
                JSONObject(text).optString("codigo").takeIf { it.isNotBlank() }
            }.getOrNull()
        }

        fun isSessionExpired(error: Throwable): Boolean =
            error is HttpException && error.code() == 401

        fun isAccessDenied(error: Throwable): Boolean {
            if (error !is HttpException || error.code() != 403) return false
            return when (apiErrorCode(error)) {
                "PASSWORD_CHANGE_REQUIRED" -> {
                    SupervisorPasswordChangeRuntime.requireChange()
                    false
                }
                "AUTH_ROLE_DENIED" -> false
                "AUTH_ACCOUNT_DISABLED", "AUTH_ROLE_INVALID" -> true
                else -> true
            }
        }

        fun isAuthFailure(error: Throwable): Boolean =
            isSessionExpired(error) || isAccessDenied(error)

        fun sessionRecoveryMessage(error: Throwable): String = when {
            isSessionExpired(error) ->
                "Sua sessão terminou. Digite sua senha novamente para continuar."
            apiErrorCode(error) == "AUTH_ACCOUNT_DISABLED" ->
                "Esta conta de Supervisor está desativada. Fale com um administrador."
            apiErrorCode(error) == "AUTH_ROLE_INVALID" ->
                "O perfil desta conta não é válido para Supervisor."
            else ->
                "Não foi possível validar o acesso de Supervisor. Entre novamente."
        }

        fun isTlsTrustFailure(error: Throwable): Boolean {
            var current: Throwable? = error
            while (current != null) {
                if (
                    current is SSLHandshakeException ||
                    current is SSLPeerUnverifiedException ||
                    current is CertPathValidatorException ||
                    current.javaClass.name.contains("CertPathValidatorException")
                ) return true
                current = current.cause
            }
            return false
        }

        fun message(error: Throwable): String {
            if (isTlsTrustFailure(error)) {
                // A mensagem antiga culpava data e hora do aparelho. Isso é UMA das
                // causas, e na prática a menos frequente: SSLHandshakeException sai
                // igual quando a rede inspeciona o tráfego. Redes corporativas
                // trocam o certificado por um assinado pela CA da empresa, que é
                // instalada como certificado DE USUÁRIO -- e desde o Android 7 os
                // apps não confiam nessas. O navegador funciona, o app não.
                //
                // Mandar o operador conferir o relógio quando o problema é o Wi-Fi
                // faz procurar no lugar errado. A dica de trocar de rede separa os
                // dois casos em segundos.
                return "A conexão segura falhou antes de o servidor validar o e-mail e a senha. " +
                    "Tente por outra rede (dados móveis, por exemplo): redes de empresa costumam " +
                    "bloquear esta conexão. Se funcionar em outra rede, avise a equipe de TI. " +
                    "Se falhar em todas, confira data e hora automáticas do aparelho."
            }
            if (apiErrorCode(error) == "PASSWORD_CHANGE_REQUIRED") {
                return "Crie uma nova senha para concluir seu primeiro acesso."
            }
            return AdminRepository.message(error)
        }
    }
}

object SupervisorApiClient {
    private const val SIGN_IN_PATH = "/api/auth/sign-in/email"

    fun create(
        supervisorSessionStore: SecureAdminSessionStore,
    ): SupervisorRepository {
        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val isCredentialSignIn =
                original.method.equals("POST", ignoreCase = true) &&
                    original.url.encodedPath == SIGN_IN_PATH
            val token = supervisorSessionStore.read()?.takeIf { it.isNotBlank() }

            val request = original.newBuilder().apply {
                if (isCredentialSignIn) {
                    removeHeader("Authorization")
                    header("Cache-Control", "no-store")
                } else {
                    token?.let { header("Authorization", "Bearer $it") }
                }
                header("X-App-Version", BuildConfig.VERSION_NAME)
            }.build()
            chain.proceed(request)
        }

        val client = PontoHttpClients.baseBuilder()
            .addInterceptor(authInterceptor)
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val repository = SupervisorRepository(
            api = retrofit.create(SupervisorApi::class.java),
            supervisorSessionStore = supervisorSessionStore,
        )
        SupervisorPasswordChangeRuntime.bind(repository)
        return repository
    }
}
