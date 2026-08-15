package com.pontocafe.app.data

import com.pontocafe.app.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.json.JSONObject
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


data class SetupStatusResponse(
    val primeiroAdminNecessario: Boolean,
    val instalacaoConfigurada: Boolean,
)

data class FirstAdminRequest(
    val nome: String,
    val email: String,
    val senha: String,
    val chaveInstalacao: String,
)

data class SignInRequest(
    val email: String,
    val password: String,
    val rememberMe: Boolean = true,
)

data class AuthUserDto(
    val id: String,
    val name: String,
    val email: String,
)

data class SignInResponse(val user: AuthUserDto?)

data class AdminUser(
    val id: String,
    val nome: String,
    val email: String,
    val perfil: String,
    val ativo: Boolean,
    val criadoEm: String,
)

data class AdminUsersResponse(val usuarios: List<AdminUser>)
data class AdminCollaboratorsResponse(val colaboradores: List<Colaborador>)

data class AdminDevice(
    val id: String,
    val nome: String,
    val ativo: Boolean,
    val criadoEm: String,
    val atualizadoEm: String,
    val ultimoAcessoEm: String? = null,
    val pinConfigurado: Boolean,
)

data class AdminDevicesResponse(val dispositivos: List<AdminDevice>)
data class UpdateDevicePinRequest(val pin: String)
data class UpdateDevicePinResponse(
    val ok: Boolean,
    val dispositivoId: String,
    val nome: String,
    val pinConfigurado: Boolean,
)
data class RenameDeviceRequest(val nome: String)
data class DeviceLifecycleResponse(
    val ok: Boolean,
    val dispositivoId: String,
    val nome: String,
    val ativo: Boolean,
)
data class DeviceDeleteResponse(
    val ok: Boolean,
    val dispositivoId: String,
    val nome: String,
    val excluido: Boolean,
)
data class DeviceTokenRotationResponse(
    val ok: Boolean,
    val dispositivoId: String,
    val nome: String,
    val token: String,
    val ativo: Boolean,
    val aviso: String,
)

data class AuditEvent(
    val id: String,
    val atorTipo: String,
    val atorNome: String,
    val acao: String,
    val entidade: String?,
    val entidadeId: String?,
    val detalhes: Map<String, Any?>? = null,
    val criadoEm: String,
    val criadoLocal: String,
)
data class AuditEventsResponse(val eventos: List<AuditEvent>)

data class AdminOperationalSummary(
    val colaboradoresAtivos: Int,
    val rostosPendentes: Int,
    val dispositivosAtivos: Int,
    val dispositivosSemPin: Int,
    val dispositivosInativos: Int,
    val supervisoresAtivos: Int,
    val administradoresAtivos: Int,
    val pausasAbertas: Int,
)
data class AdminOperationalSummaryResponse(val resumo: AdminOperationalSummary)

data class CreateAdminUserRequest(
    val nome: String,
    val email: String,
    val senha: String,
    val perfil: String,
)

data class ChangePasswordRequest(val novaSenha: String)
data class ChangeProfileRequest(val perfil: String)
data class CreateDeviceRequest(val nome: String, val pin: String)

data class DeviceCreatedResponse(
    val id: String,
    val nome: String,
    val token: String,
    val pinConfigurado: Boolean = true,
    val aviso: String,
)

data class CreateAuthorizationRequest(
    val colaboradorId: String,
    val periodo: String,
    val motivo: String,
)

data class AuthorizationCreatedResponse(
    val id: String,
    val codigo: String,
    val expiraEm: String?,
    val expiraEmSegundos: Int,
    val aviso: String,
)

data class AdminCoffeeRule(
    val periodo: String,
    val inicio: String,
    val fim: String,
    val limiteMinutos: Int,
    val ativo: Boolean,
)

data class AdminCoffeeRulesResponse(val regras: List<AdminCoffeeRule>)
data class UpdateCoffeeRuleRequest(
    val inicio: String,
    val fim: String,
    val limiteMinutos: Int,
    val ativo: Boolean = true,
)
data class UpdateCoffeeRuleResponse(val regra: AdminCoffeeRule)

data class CreateCollaboratorRequest(
    val nome: String,
    val setor: String?,
    val turno: String?,
)

data class UpdateCollaboratorRequest(
    val nome: String,
    val setor: String?,
    val turno: String?,
)

data class BiometricEnrollmentRequest(
    val embedding: List<Float>,
    val modelo: String,
    val versaoModelo: String,
)

data class BiometricEnrollmentResponse(
    val ok: Boolean,
    val colaboradorId: String,
    val dimensao: Int,
)

data class SimpleAdminResponse(
    val ok: Boolean = true,
    val ativo: Boolean? = null,
    val perfil: String? = null,
    val sessoesRevogadas: Boolean? = null,
    val excluido: Boolean? = null,
)

interface AdminApi {
    @GET("setup/status") suspend fun setupStatus(): SetupStatusResponse
    @POST("setup/primeiro-admin") suspend fun createFirstAdmin(@Body body: FirstAdminRequest): Response<Unit>
    @POST("api/auth/sign-in/email") suspend fun signIn(@Body body: SignInRequest): Response<SignInResponse>
    @POST("api/auth/sign-out") suspend fun signOut(): Response<Unit>

    @GET("admin/usuarios") suspend fun users(): AdminUsersResponse
    @POST("admin/usuarios") suspend fun createUser(@Body body: CreateAdminUserRequest): Response<Unit>
    @POST("admin/usuarios/{id}/bloquear") suspend fun disableUser(@Path("id") id: String): SimpleAdminResponse
    @POST("admin/usuarios/{id}/reativar") suspend fun enableUser(@Path("id") id: String): SimpleAdminResponse
    @POST("admin/usuarios/{id}/excluir") suspend fun deleteUser(@Path("id") id: String): SimpleAdminResponse
    @PUT("admin/usuarios/{id}/senha") suspend fun resetPassword(@Path("id") id: String, @Body body: ChangePasswordRequest): SimpleAdminResponse
    @PUT("admin/usuarios/{id}/perfil") suspend fun changeProfile(@Path("id") id: String, @Body body: ChangeProfileRequest): SimpleAdminResponse

    @GET("admin/regras-cafe") suspend fun coffeeRules(): AdminCoffeeRulesResponse
    @PUT("admin/regras-cafe/{periodo}")
    suspend fun updateCoffeeRule(@Path("periodo") periodo: String, @Body body: UpdateCoffeeRuleRequest): UpdateCoffeeRuleResponse

    @POST("admin/device-activation") suspend fun createDevice(@Body body: CreateDeviceRequest): DeviceCreatedResponse
    @GET("admin/devices") suspend fun devices(): AdminDevicesResponse
    @PUT("admin/devices/{id}/unlock-pin")
    suspend fun updateDevicePin(@Path("id") id: String, @Body body: UpdateDevicePinRequest): UpdateDevicePinResponse
    @PUT("admin/devices/{id}/nome")
    suspend fun renameDevice(@Path("id") id: String, @Body body: RenameDeviceRequest): Response<Unit>
    @POST("admin/devices/{id}/desativar")
    suspend fun deactivateDevice(@Path("id") id: String): DeviceLifecycleResponse
    @POST("admin/devices/{id}/novo-token")
    suspend fun rotateDeviceToken(@Path("id") id: String): DeviceTokenRotationResponse
    @POST("admin/devices/{id}/excluir")
    suspend fun deleteDevice(@Path("id") id: String): DeviceDeleteResponse

    @GET("admin/auditoria") suspend fun audit(
        @Query("limite") limite: Int = 100,
        @Query("acao") acao: String? = null,
    ): AuditEventsResponse
    @GET("admin/operacao/resumo") suspend fun operationalSummary(): AdminOperationalSummaryResponse
    @GET("health") suspend fun health(): SystemHealthResponse
    @GET("app-status") suspend fun appStatus(): AppStatusResponse

    @GET("gestao/colaboradores") suspend fun collaborators(): AdminCollaboratorsResponse
    @POST("gestao/colaboradores") suspend fun createCollaborator(@Body body: CreateCollaboratorRequest): Colaborador
    @PUT("gestao/colaboradores/{id}")
    suspend fun updateCollaborator(@Path("id") id: String, @Body body: UpdateCollaboratorRequest): Colaborador
    @PUT("gestao/colaboradores/{id}/biometria")
    suspend fun saveBiometric(@Path("id") id: String, @Body body: BiometricEnrollmentRequest): BiometricEnrollmentResponse

    @POST("admin/autorizacoes") suspend fun createAuthorization(@Body body: CreateAuthorizationRequest): AuthorizationCreatedResponse
}

class AdminRepository(
    private val api: AdminApi,
    private val sessionStore: SecureAdminSessionStore,
) {
    suspend fun setupStatus() = api.setupStatus()
    suspend fun createFirstAdmin(nome: String, email: String, senha: String, chave: String) {
        ensureSuccess(api.createFirstAdmin(FirstAdminRequest(nome, email, senha, chave)))
    }

    suspend fun signIn(email: String, senha: String) {
        val response = api.signIn(SignInRequest(email = email, password = senha))
        if (!response.isSuccessful) throw HttpException(response)
        val bearer = response.headers()["set-auth-token"] ?: error("O servidor não retornou a sessão administrativa.")
        sessionStore.save(bearer)
        try {
            api.users()
        } catch (error: Throwable) {
            if (isAuthFailure(error)) sessionStore.clear()
            throw error
        }
    }

    suspend fun signOut() { runCatching { api.signOut() }; sessionStore.clear() }
    suspend fun users() = api.users().usuarios
    suspend fun createUser(nome: String, email: String, senha: String, perfil: String) {
        ensureSuccess(api.createUser(CreateAdminUserRequest(nome, email, senha, perfil)))
    }
    suspend fun setActive(userId: String, active: Boolean) { if (active) api.enableUser(userId) else api.disableUser(userId) }
    suspend fun deleteUser(userId: String) { api.deleteUser(userId) }
    suspend fun resetPassword(userId: String, newPassword: String) { api.resetPassword(userId, ChangePasswordRequest(newPassword)) }
    suspend fun changeProfile(userId: String, profile: String) { api.changeProfile(userId, ChangeProfileRequest(profile)) }

    suspend fun coffeeRules() = api.coffeeRules().regras
    suspend fun updateCoffeeRule(period: String, start: String, end: String, limitMinutes: Int, active: Boolean) =
        api.updateCoffeeRule(period, UpdateCoffeeRuleRequest(start, end, limitMinutes, active)).regra

    suspend fun createDevice(name: String, pin: String) = api.createDevice(CreateDeviceRequest(name.trim(), pin.trim()))
    suspend fun devices() = api.devices().dispositivos
    suspend fun updateDevicePin(deviceId: String, pin: String) = api.updateDevicePin(deviceId, UpdateDevicePinRequest(pin.trim()))
    suspend fun renameDevice(deviceId: String, name: String) {
        ensureSuccess(api.renameDevice(deviceId, RenameDeviceRequest(name.trim())))
    }
    suspend fun deactivateDevice(deviceId: String) = api.deactivateDevice(deviceId)
    suspend fun rotateDeviceToken(deviceId: String) = api.rotateDeviceToken(deviceId)
    suspend fun deleteDevice(deviceId: String) = api.deleteDevice(deviceId)

    suspend fun audit(limit: Int = 100, action: String? = null) = api.audit(limit, action).eventos
    suspend fun operationalSummary() = api.operationalSummary().resumo
    suspend fun health() = api.health()
    suspend fun appStatus() = api.appStatus()

    suspend fun collaborators() = api.collaborators().colaboradores
    suspend fun createCollaborator(name: String, sector: String?, shift: String?) = api.createCollaborator(
        CreateCollaboratorRequest(
            nome = name.trim(),
            setor = sector?.trim()?.ifBlank { null },
            turno = shift?.trim()?.ifBlank { null },
        ),
    )
    suspend fun updateCollaborator(collaboratorId: String, name: String, sector: String?, shift: String?) =
        api.updateCollaborator(
            collaboratorId,
            UpdateCollaboratorRequest(
                nome = name.trim(),
                setor = sector?.trim()?.ifBlank { null },
                turno = shift?.trim()?.ifBlank { null },
            ),
        )

    @Deprecated("Matrícula não é mais utilizada")
    suspend fun createCollaborator(registration: String?, name: String, sector: String?, shift: String?) =
        createCollaborator(name, sector, shift)

    suspend fun saveBiometric(collaboratorId: String, embedding: FloatArray, model: String, modelVersion: String) = api.saveBiometric(
        collaboratorId,
        BiometricEnrollmentRequest(embedding.toList(), model, modelVersion),
    )

    suspend fun createAuthorization(collaboratorId: String, period: String, reason: String) = api.createAuthorization(
        CreateAuthorizationRequest(collaboratorId, period, reason.trim()),
    )

    fun hasSession() = sessionStore.hasToken()
    fun clearSession() = sessionStore.clear()
    private fun ensureSuccess(response: Response<*>) { if (!response.isSuccessful) throw HttpException(response) }

    companion object {
        fun isAuthFailure(error: Throwable): Boolean =
            error is HttpException && (error.code() == 401 || error.code() == 403)

        fun message(error: Throwable): String {
            if (error is HttpException) {
                val body = runCatching { error.response()?.errorBody()?.string() }.getOrNull()
                val apiMessage = runCatching { body?.let { JSONObject(it).optString("erro") } }.getOrNull()
                if (!apiMessage.isNullOrBlank()) return apiMessage
                return when (error.code()) {
                    401 -> "E-mail ou senha inválidos."
                    403 -> "Acesso não autorizado."
                    429 -> "Muitas tentativas. Aguarde e tente novamente."
                    else -> "Falha na comunicação com o servidor (${error.code()})."
                }
            }
            return error.message ?: "Não foi possível concluir a operação."
        }
    }
}

object AdminApiClient {
    fun create(sessionStore: SecureAdminSessionStore): AdminRepository {
        val authInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder().apply {
                sessionStore.read()?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") }
                header("X-App-Version", BuildConfig.VERSION_NAME)
            }.build()
            chain.proceed(request)
        }
        val client = OkHttpClient.Builder().addInterceptor(authInterceptor).build()
        val retrofit = Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).client(client).addConverterFactory(GsonConverterFactory.create()).build()
        return AdminRepository(retrofit.create(AdminApi::class.java), sessionStore)
    }
}
