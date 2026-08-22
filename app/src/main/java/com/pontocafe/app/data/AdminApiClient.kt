package com.pontocafe.app.data

import com.pontocafe.app.BuildConfig
import com.pontocafe.app.avatar.PontoAvatarRuntime
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
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
    val statusAtivacao: String = "AGUARDANDO_ATIVACAO",
    val ativadoEm: String? = null,
    val telemetriaEm: String? = null,
    val appVersion: String? = null,
    val deviceModel: String? = null,
    val androidVersion: String? = null,
    val crashCount: Int = 0,
    val stallCount: Int = 0,
    val alertaSaude: Boolean = false,
)

/**
 * Wire DTO kept nullable for fields introduced after the first production API.
 * Gson can bypass Kotlin constructor defaults when a field is absent, so the
 * API object is normalized before it is allowed into UI state.
 */
data class AdminDeviceWire(
    val id: String,
    val nome: String,
    val ativo: Boolean,
    val criadoEm: String,
    val atualizadoEm: String,
    val ultimoAcessoEm: String? = null,
    val pinConfigurado: Boolean = false,
    val statusAtivacao: String? = null,
    val ativadoEm: String? = null,
    val telemetriaEm: String? = null,
    val appVersion: String? = null,
    val deviceModel: String? = null,
    val androidVersion: String? = null,
    val crashCount: Int = 0,
    val stallCount: Int = 0,
    val alertaSaude: Boolean = false,
)

private fun AdminDeviceWire.toDomain(): AdminDevice {
    val normalizedStatus = when {
        !ativo -> "INATIVO"
        statusAtivacao.equals("ATIVADO", ignoreCase = true) -> "ATIVADO"
        statusAtivacao.equals("INATIVO", ignoreCase = true) -> "INATIVO"
        else -> "AGUARDANDO_ATIVACAO"
    }
    return AdminDevice(
        id = id,
        nome = nome,
        ativo = ativo,
        criadoEm = criadoEm,
        atualizadoEm = atualizadoEm,
        ultimoAcessoEm = ultimoAcessoEm,
        pinConfigurado = pinConfigurado,
        statusAtivacao = normalizedStatus,
        ativadoEm = ativadoEm,
        telemetriaEm = telemetriaEm,
        appVersion = appVersion,
        deviceModel = deviceModel,
        androidVersion = androidVersion,
        crashCount = crashCount,
        stallCount = stallCount,
        alertaSaude = alertaSaude,
    )
}

data class AdminDevicesResponse(val dispositivos: List<AdminDeviceWire>)
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
data class CreateDeviceRequest(val nome: String, val pin: String?)

data class DeviceCreatedResponse(
    val id: String,
    val nome: String,
    val token: String,
    val pinConfigurado: Boolean = false,
    val replayIdempotente: Boolean = false,
    val aviso: String,
)

data class CreateAuthorizationRequest(
    val colaboradorId: String,
    val motivo: String,
)

data class AuthorizationCreatedResponse(
    val id: String,
    val liberada: Boolean,
    val colaboradorNome: String,
    val periodo: String,
    val periodoDefinidoAutomaticamente: Boolean = true,
    val expiraEm: String?,
    val expiraEmSegundos: Int,
    val usoUnico: Boolean = true,
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
    val amostras: List<List<Float>>? = null,
)

data class BiometricEnrollmentResponse(
    val ok: Boolean,
    val colaboradorId: String,
    val dimensao: Int,
    val verificacaoDuplicidade: Boolean? = null,
    val limiteDuplicidade: Double? = null,
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

    @POST("admin/device-activation")
    suspend fun createDevice(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: CreateDeviceRequest,
    ): DeviceCreatedResponse
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
    @PUT("gestao/colaboradores/{id}/avatar")
    suspend fun uploadAvatar(@Path("id") id: String, @Body body: okhttp3.RequestBody): AvatarMutationResponse
    @POST("gestao/colaboradores/{id}/avatar/excluir")
    suspend fun deleteAvatar(@Path("id") id: String): AvatarMutationResponse
    @PUT("gestao/colaboradores/{id}/biometria")
    suspend fun saveBiometric(@Path("id") id: String, @Body body: BiometricEnrollmentRequest): BiometricEnrollmentResponse

    @POST("admin/autorizacoes") suspend fun createAuthorization(@Body body: CreateAuthorizationRequest): AuthorizationCreatedResponse
    @POST("admin/autorizacoes/cancelar") suspend fun cancelAuthorization(
        @Body body: CancelAuthorizationRequest,
    ): CancelAuthorizationResponse
}

class AdminRepository(
    private val api: AdminApi,
    private val sessionStore: SecureAdminSessionStore,
) {
    @Volatile private var usersCache: List<AdminUser>? = null
    @Volatile private var rulesCache: List<AdminCoffeeRule>? = null
    @Volatile private var devicesCache: List<AdminDevice>? = null
    @Volatile private var collaboratorsCache: List<Colaborador>? = null
    @Volatile private var summaryCache: AdminOperationalSummary? = null

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
            usersCache = api.users().usuarios
        } catch (error: Throwable) {
            if (isAuthFailure(error)) sessionStore.clear()
            throw error
        }
    }

    suspend fun signOut() {
        runCatching { api.signOut() }
        sessionStore.clear()
        clearCaches()
    }

    suspend fun users(): List<AdminUser> = usersCache ?: api.users().usuarios.also { usersCache = it }

    suspend fun createUser(nome: String, email: String, senha: String, perfil: String) {
        ensureSuccess(api.createUser(CreateAdminUserRequest(nome, email, senha, perfil)))
        usersCache = null
        summaryCache = null
    }

    suspend fun setActive(userId: String, active: Boolean) {
        if (active) api.enableUser(userId) else api.disableUser(userId)
        usersCache = null
        summaryCache = null
    }

    suspend fun deleteUser(userId: String) {
        api.deleteUser(userId)
        usersCache = null
        summaryCache = null
    }

    suspend fun resetPassword(userId: String, newPassword: String) {
        api.resetPassword(userId, ChangePasswordRequest(newPassword))
    }

    suspend fun changeProfile(userId: String, profile: String) {
        api.changeProfile(userId, ChangeProfileRequest(profile))
        usersCache = null
        summaryCache = null
    }

    suspend fun coffeeRules(): List<AdminCoffeeRule> = rulesCache ?: api.coffeeRules().regras.also { rulesCache = it }

    suspend fun updateCoffeeRule(period: String, start: String, end: String, limitMinutes: Int, active: Boolean): AdminCoffeeRule {
        val updated = api.updateCoffeeRule(period, UpdateCoffeeRuleRequest(start, end, limitMinutes, active)).regra
        rulesCache = null
        return updated
    }

    suspend fun createDevice(name: String, pin: String?, idempotencyKey: String): DeviceCreatedResponse {
        val normalizedPin = pin?.trim()?.ifBlank { null }
        val created = api.createDevice(idempotencyKey, CreateDeviceRequest(name.trim(), normalizedPin))
        devicesCache = null
        summaryCache = null
        return created
    }

    suspend fun devices(): List<AdminDevice> = devicesCache ?: api.devices().dispositivos
        .map(AdminDeviceWire::toDomain)
        .also { devicesCache = it }

    suspend fun updateDevicePin(deviceId: String, pin: String): UpdateDevicePinResponse {
        val updated = api.updateDevicePin(deviceId, UpdateDevicePinRequest(pin.trim()))
        devicesCache = null
        summaryCache = null
        return updated
    }

    suspend fun renameDevice(deviceId: String, name: String) {
        ensureSuccess(api.renameDevice(deviceId, RenameDeviceRequest(name.trim())))
        devicesCache = null
    }

    suspend fun deactivateDevice(deviceId: String): DeviceLifecycleResponse {
        val result = api.deactivateDevice(deviceId)
        devicesCache = null
        summaryCache = null
        return result
    }

    suspend fun rotateDeviceToken(deviceId: String): DeviceTokenRotationResponse {
        val result = api.rotateDeviceToken(deviceId)
        devicesCache = null
        summaryCache = null
        return result
    }

    suspend fun deleteDevice(deviceId: String): DeviceDeleteResponse {
        val result = api.deleteDevice(deviceId)
        devicesCache = null
        summaryCache = null
        return result
    }

    suspend fun audit(limit: Int = 100, action: String? = null) = api.audit(limit, action).eventos
    suspend fun operationalSummary(): AdminOperationalSummary =
        summaryCache ?: api.operationalSummary().resumo.also { summaryCache = it }
    suspend fun health() = api.health()
    suspend fun appStatus() = api.appStatus()

    // A lista de colaboradores é operacional e pode mudar por endpoints de
    // biometria/gestão que pertencem a outro repositório. Por isso não retornamos
    // uma cópia potencialmente obsoleta daqui: sempre buscamos o estado atual do
    // servidor e apenas guardamos a última leitura para diagnóstico/compatibilidade.
    suspend fun collaborators(): List<Colaborador> =
        api.collaborators().colaboradores.also { collaboratorsCache = it }

    suspend fun createCollaborator(name: String, sector: String?, shift: String?): Colaborador {
        val created = api.createCollaborator(
            CreateCollaboratorRequest(
                nome = name.trim(),
                setor = sector?.trim()?.ifBlank { null },
                turno = shift?.trim()?.ifBlank { null },
            ),
        )
        collaboratorsCache = null
        summaryCache = null
        return created
    }

    suspend fun updateCollaborator(collaboratorId: String, name: String, sector: String?, shift: String?): Colaborador {
        val updated = api.updateCollaborator(
            collaboratorId,
            UpdateCollaboratorRequest(
                nome = name.trim(),
                setor = sector?.trim()?.ifBlank { null },
                turno = shift?.trim()?.ifBlank { null },
            ),
        )
        collaboratorsCache = null
        return updated
    }

    suspend fun uploadAvatar(collaboratorId: String, webp: ByteArray): AvatarMutationResponse {
        require(webp.isNotEmpty()) { "Avatar vazio." }
        val result = api.uploadAvatar(collaboratorId, webp.toRequestBody("image/webp".toMediaType()))
        collaboratorsCache = null
        PontoAvatarRuntime.avatarUpdated(collaboratorId, result.avatarUrl)
        return result
    }

    suspend fun deleteAvatar(collaboratorId: String): AvatarMutationResponse {
        val result = api.deleteAvatar(collaboratorId)
        collaboratorsCache = null
        PontoAvatarRuntime.avatarUpdated(collaboratorId, null)
        return result
    }

    suspend fun saveBiometric(
        collaboratorId: String,
        embedding: FloatArray,
        model: String,
        modelVersion: String,
        samples: List<FloatArray> = emptyList(),
    ): BiometricEnrollmentResponse {
        val result = api.saveBiometric(
            collaboratorId,
            BiometricEnrollmentRequest(
                embedding = embedding.toList(),
                modelo = model,
                versaoModelo = modelVersion,
                amostras = samples.takeIf { it.isNotEmpty() }?.map { it.toList() },
            ),
        )
        collaboratorsCache = null
        summaryCache = null
        return result
    }

    suspend fun createAuthorization(collaboratorId: String, reason: String) = api.createAuthorization(
        CreateAuthorizationRequest(collaboratorId, reason.trim()),
    )

    suspend fun cancelAuthorization(collaboratorId: String) =
        api.cancelAuthorization(CancelAuthorizationRequest(collaboratorId))

    fun hasSession() = sessionStore.hasToken()
    fun clearSession() {
        sessionStore.clear()
        clearCaches()
    }

    private fun clearCaches() {
        usersCache = null
        rulesCache = null
        devicesCache = null
        collaboratorsCache = null
        summaryCache = null
    }

    private fun ensureSuccess(response: Response<*>) {
        if (!response.isSuccessful) throw HttpException(response)
    }

    companion object {
        fun isAuthFailure(error: Throwable): Boolean =
            error is HttpException && (error.code() == 401 || error.code() == 403)

        fun message(error: Throwable): String {
            if (error is HttpException) {
                val body = runCatching { error.response()?.errorBody()?.string() }.getOrNull()
                val json = runCatching { body?.let(::JSONObject) }.getOrNull()
                val apiMessage = json?.optString("erro")?.takeIf { it.isNotBlank() }
                val requestId = json?.optString("requestId")?.takeIf { it.isNotBlank() }
                if (apiMessage != null) {
                    return if (requestId != null) "$apiMessage · ID $requestId" else apiMessage
                }
                return when (error.code()) {
                    401 -> "E-mail ou senha inválidos."
                    403 -> "Acesso não autorizado."
                    409 -> "O cadastro já foi usado ou expirou. Feche e inicie um novo cadastro."
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
        val appVersion = safeHttpHeaderValue(BuildConfig.VERSION_NAME, 80)
        val authInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder().apply {
                sessionStore.read()?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") }
                if (appVersion.isNotBlank()) header("X-App-Version", appVersion)
            }.build()
            chain.proceed(request)
        }
        val client = OkHttpClient.Builder().addInterceptor(authInterceptor).build()
        val retrofit = Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).client(client).addConverterFactory(GsonConverterFactory.create()).build()
        return AdminRepository(retrofit.create(AdminApi::class.java), sessionStore)
    }
}
