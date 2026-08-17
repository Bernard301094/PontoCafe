package com.pontocafe.app.data

import com.pontocafe.app.BuildConfig
import java.security.cert.CertPathValidatorException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query


data class PausaSupervisor(
    val id: String,
    val periodo: String,
    val data: String? = null,
    val inicioLocal: String,
    val fimLocal: String? = null,
    val limiteSegundos: Int,
    val foraHorario: Boolean,
    val tempoSegundos: Int? = null,
    val duracaoSegundos: Int? = null,
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
    val rostoExcluido: Boolean? = null,
)

data class CancelAuthorizationRequest(
    val colaboradorId: String,
    val periodo: String,
)

data class CancelAuthorizationResponse(
    val ok: Boolean,
    val cancelada: Boolean,
    val id: String,
)

interface SupervisorApi {
    @POST("api/auth/sign-in/email") suspend fun signIn(@Body body: SignInRequest): Response<SignInResponse>
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
    @POST("supervisor/autorizacoes") suspend fun createAuthorization(
        @Body body: CreateAuthorizationRequest,
    ): AuthorizationCreatedResponse
    @POST("supervisor/autorizacoes/cancelar") suspend fun cancelAuthorization(
        @Body body: CancelAuthorizationRequest,
    ): CancelAuthorizationResponse
    @GET("gestao/colaboradores") suspend fun collaborators(): ColaboradoresResponse
    @POST("gestao/colaboradores") suspend fun createCollaborator(@Body body: CreateCollaboratorRequest): Colaborador
    @PUT("gestao/colaboradores/{id}/biometria") suspend fun saveBiometric(
        @Path("id") id: String,
        @Body body: BiometricEnrollmentRequest,
    ): BiometricEnrollmentResponse
    @POST("gestao/colaboradores/{id}/biometria/excluir") suspend fun deleteBiometric(@Path("id") id: String): CollaboratorMutationResponse
    @POST("gestao/colaboradores/{id}/excluir") suspend fun deleteCollaborator(@Path("id") id: String): CollaboratorMutationResponse
}

class SupervisorRepository(
    private val api: SupervisorApi,
    private val supervisorSessionStore: SecureAdminSessionStore,
) {
    private fun supervisorToken(): String? = supervisorSessionStore.read()?.takeIf { it.isNotBlank() }

    fun activeToken(): String? = supervisorToken()
    fun hasSession(): Boolean = supervisorToken() != null
    fun usingAdminSession(): Boolean = false

    /**
     * O login termina quando o servidor autentica as credenciais e devolve o
     * bearer token. A carga da Operação acontece depois, no ViewModel.
     *
     * Antes, pausasAtivas() era chamada aqui para validar o papel. Isso fazia
     * uma falha de rede/TLS posterior ao login parecer "falha de login" e
     * devolvia o usuário ao formulário mesmo com uma sessão válida já criada.
     */
    suspend fun signIn(email: String, senha: String) {
        val response = api.signIn(SignInRequest(email = email, password = senha))
        if (!response.isSuccessful) throw HttpException(response)
        val bearer = response.headers()["set-auth-token"]
            ?: error("O servidor não retornou a sessão do supervisor.")
        supervisorSessionStore.save(bearer)
    }

    suspend fun pausasAtivas(): List<PausaSupervisor> {
        val pausas = api.pausasAtivas().pausas
        val atualizadoEm = System.currentTimeMillis()
        return pausas.map { pausa ->
            pausa.copy(clienteAtualizadoEmMillis = atualizadoEm)
        }
    }

    suspend fun historico(data: String? = null) = api.historico(data).pausas
    suspend fun report(inicio: String, fim: String) = api.report(inicio, fim)
    suspend fun reportCsv(inicio: String, fim: String): ByteArray = api.reportCsv(inicio, fim).bytes()
    suspend fun createAuthorization(colaboradorId: String, periodo: String, motivo: String) =
        api.createAuthorization(CreateAuthorizationRequest(colaboradorId, periodo, motivo.trim()))
    suspend fun cancelAuthorization(colaboradorId: String, periodo: String) =
        api.cancelAuthorization(CancelAuthorizationRequest(colaboradorId, periodo))
    suspend fun collaborators() = api.collaborators().colaboradores
        .sortedWith(compareBy<Colaborador> { it.rostoCadastrado }.thenBy { it.nome.lowercase() })

    suspend fun createCollaborator(name: String, sector: String?, shift: String?) = api.createCollaborator(
        CreateCollaboratorRequest(
            nome = name.trim(),
            setor = sector?.trim()?.ifBlank { null },
            turno = shift?.trim()?.ifBlank { null },
        ),
    )

    suspend fun saveBiometric(
        collaboratorId: String,
        embedding: FloatArray,
        model: String,
        modelVersion: String,
        samples: List<FloatArray> = emptyList(),
    ) = api.saveBiometric(
        collaboratorId,
        BiometricEnrollmentRequest(
            embedding = embedding.toList(),
            modelo = model,
            versaoModelo = modelVersion,
            amostras = samples.takeIf { it.isNotEmpty() }?.map { it.toList() },
        ),
    )

    suspend fun deleteBiometric(collaboratorId: String) = api.deleteBiometric(collaboratorId)
    suspend fun deleteCollaborator(collaboratorId: String) = api.deleteCollaborator(collaboratorId)

    suspend fun signOutSupervisor() {
        if (supervisorToken() != null) {
            runCatching { api.signOut() }
            supervisorSessionStore.clear()
        }
    }

    fun clearActiveSession() {
        supervisorSessionStore.clear()
    }

    companion object {
        fun isAuthFailure(error: Throwable): Boolean = AdminRepository.isAuthFailure(error)

        fun isTlsTrustFailure(error: Throwable): Boolean {
            var current: Throwable? = error
            while (current != null) {
                if (
                    current is SSLHandshakeException ||
                    current is SSLPeerUnverifiedException ||
                    current is CertPathValidatorException ||
                    current.javaClass.name.contains("CertPathValidatorException")
                ) {
                    return true
                }
                current = current.cause
            }
            return false
        }

        fun message(error: Throwable): String {
            if (isTlsTrustFailure(error)) {
                return "Não foi possível validar a conexão segura com o servidor. Verifique se data e hora automáticas estão ativadas e tente novamente. Se persistir, teste outra rede."
            }
            return AdminRepository.message(error)
        }
    }
}

object SupervisorApiClient {
    fun create(
        supervisorSessionStore: SecureAdminSessionStore,
    ): SupervisorRepository {
        val authInterceptor = Interceptor { chain ->
            val token = supervisorSessionStore.read()?.takeIf { it.isNotBlank() }
            val request = chain.request().newBuilder().apply {
                token?.let { header("Authorization", "Bearer $it") }
                header("X-App-Version", BuildConfig.VERSION_NAME)
            }.build()
            chain.proceed(request)
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return SupervisorRepository(
            api = retrofit.create(SupervisorApi::class.java),
            supervisorSessionStore = supervisorSessionStore,
        )
    }
}
