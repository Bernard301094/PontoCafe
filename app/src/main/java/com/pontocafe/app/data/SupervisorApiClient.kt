package com.pontocafe.app.data

import com.pontocafe.app.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
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
)

data class PausasSupervisorResponse(val pausas: List<PausaSupervisor>)

data class CollaboratorMutationResponse(
    val ok: Boolean = true,
    val excluido: Boolean? = null,
    val rostoExcluido: Boolean? = null,
)

interface SupervisorApi {
    @POST("api/auth/sign-in/email") suspend fun signIn(@Body body: SignInRequest): Response<SignInResponse>
    @POST("api/auth/sign-out") suspend fun signOut(): Response<Unit>
    @GET("supervisor/pausas/ativas") suspend fun pausasAtivas(): PausasSupervisorResponse
    @GET("supervisor/pausas") suspend fun historico(@Query("data") data: String? = null): PausasSupervisorResponse
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
    private val adminSessionStore: SecureAdminSessionStore,
) {
    private fun supervisorToken(): String? = supervisorSessionStore.read()?.takeIf { it.isNotBlank() }
    private fun adminToken(): String? = adminSessionStore.read()?.takeIf { it.isNotBlank() }

    fun activeToken(): String? = supervisorToken() ?: adminToken()
    fun hasSession(): Boolean = activeToken() != null
    fun usingAdminSession(): Boolean = supervisorToken() == null && adminToken() != null

    suspend fun signIn(email: String, senha: String) {
        val response = api.signIn(SignInRequest(email = email, password = senha))
        if (!response.isSuccessful) throw HttpException(response)
        val bearer = response.headers()["set-auth-token"]
            ?: error("O servidor não retornou a sessão do supervisor.")
        supervisorSessionStore.save(bearer)
        try {
            api.pausasAtivas()
        } catch (error: Throwable) {
            supervisorSessionStore.clear()
            throw error
        }
    }

    suspend fun pausasAtivas() = api.pausasAtivas().pausas
    suspend fun historico(data: String? = null) = api.historico(data).pausas
    suspend fun collaborators() = api.collaborators().colaboradores

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
    ) = api.saveBiometric(
        collaboratorId,
        BiometricEnrollmentRequest(embedding.toList(), model, modelVersion),
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
        if (supervisorToken() != null) supervisorSessionStore.clear() else adminSessionStore.clear()
    }

    companion object {
        fun message(error: Throwable): String = AdminRepository.message(error)
    }
}

object SupervisorApiClient {
    fun create(
        supervisorSessionStore: SecureAdminSessionStore,
        adminSessionStore: SecureAdminSessionStore,
    ): SupervisorRepository {
        val authInterceptor = Interceptor { chain ->
            val token = supervisorSessionStore.read()?.takeIf { it.isNotBlank() }
                ?: adminSessionStore.read()?.takeIf { it.isNotBlank() }
            val request = chain.request().newBuilder().apply {
                token?.let { header("Authorization", "Bearer $it") }
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
            adminSessionStore = adminSessionStore,
        )
    }
}
