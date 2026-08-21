package com.pontocafe.app.data

import android.content.Context
import com.pontocafe.app.BuildConfig
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
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
import retrofit2.http.Query


data class Colaborador(
    val id: String,
    val nome: String,
    val setor: String?,
    val turno: String?,
    val rostoCadastrado: Boolean = false,
    val avatarUrl: String? = null,
    @Deprecated("Matrícula não é mais utilizada pelo Ponto Café")
    val matricula: String? = null,
)

data class ColaboradoresResponse(val colaboradores: List<Colaborador>)

data class AvatarCatalogItem(
    val colaboradorId: String,
    val avatarUrl: String,
)
data class AvatarCatalogResponse(val avatares: List<AvatarCatalogItem>)

data class DeviceActivationRequest(val token: String)
data class DeviceActivationResponse(val token: String)
data class DeviceUnlockRequest(val pin: String, val area: String)
data class DeviceUnlockResponse(
    val ok: Boolean,
    val area: String,
)

data class RegraCafe(
    val periodo: String,
    val inicio: String,
    val fim: String,
    val limiteSegundos: Int,
)

data class HorarioCafeResponse(
    val dentroHorario: Boolean,
    val periodoAtual: String?,
    val limiteSegundos: Int?,
    val agoraLocal: String?,
    val regras: List<RegraCafe>,
)

data class SystemHealthResponse(
    val status: String,
    val banco: String,
    val servidor: String?,
)

data class AppStatusResponse(
    val apiVersion: String,
    val latestAndroidVersion: String,
    val minimumAndroidVersion: String,
    val timezone: String,
    val offlineMaxEventAgeHours: Int,
)

data class FaceCatalogResponse(
    val atualizado: Boolean,
    val versao: String,
    val modelo: String,
    val versaoModelo: String,
    val limiar: Double,
    val margem: Double,
    val templates: List<CachedFaceTemplate>,
    val templatesRejeitados: Int = 0,
)

data class ConfirmarBiometriaLocalRequest(
    val colaboradorId: String,
    val embedding: List<Float>,
    val modelo: String,
    val versaoModelo: String,
)

data class IdentificarBiometriaRequest(val embedding: List<Float>)

data class PausaAbertaResumo(
    val id: String,
    val periodo: String,
    val inicioEm: String,
    val inicioLocal: String,
    val limiteSegundos: Int,
    val tempoDecorridoSegundos: Int,
)

data class IdentificarBiometriaResponse(
    val reconhecido: Boolean,
    val motivo: String? = null,
    val mensagem: String? = null,
    val score: Double? = null,
    val verificacaoToken: String? = null,
    val expiraEmSegundos: Int? = null,
    val colaborador: Colaborador? = null,
    val acaoSugerida: String? = null,
    val pausaAberta: PausaAbertaResumo? = null,
    val dentroHorario: Boolean? = null,
    val periodoAtual: String? = null,
    val limiteSegundos: Int? = null,
)

data class VerificarBiometriaRequest(val colaboradorId: String, val embedding: List<Float>)
data class VerificarBiometriaResponse(
    val reconhecido: Boolean,
    val score: Double,
    val verificacaoToken: String?,
    val expiraEmSegundos: Int?,
)

data class IniciarPausaRequest(
    val operacaoId: String,
    val colaboradorId: String,
    val verificacaoToken: String,
)

data class IniciarPausaResponse(
    val id: String,
    val periodo: String,
    val limiteSegundos: Int,
    val foraHorario: Boolean,
    val inicioEm: String,
    val inicioLocal: String,
    val retornoAteLocal: String,
)

data class FinalizarPausaRequest(
    val operacaoId: String,
    val colaboradorId: String,
    val verificacaoToken: String,
)

data class FinalizarPausaResponse(
    val id: String,
    val inicioLocal: String,
    val fimEm: String,
    val fimLocal: String,
    val duracaoSegundos: Int,
    val limiteSegundos: Int,
    val excedeuLimite: Boolean,
)

data class RegistroRapidoRequest(
    val operacaoId: String,
    val colaboradorId: String,
    val embedding: List<Float>,
    val modelo: String,
    val versaoModelo: String,
)

data class RegistroRapidoResponse(
    val status: String,
    val score: Double? = null,
    val colaborador: Colaborador? = null,
    val inicio: IniciarPausaResponse? = null,
    val retorno: FinalizarPausaResponse? = null,
    val motivo: String? = null,
    val mensagem: String? = null,
)

data class PontoOperationReconcileRequest(
    val operacaoId: String,
    val colaboradorId: String,
)

data class PontoOperationReconcileResponse(
    val encontrada: Boolean,
    val tipo: String? = null,
    val colaborador: Colaborador? = null,
    val inicio: IniciarPausaResponse? = null,
    val retorno: FinalizarPausaResponse? = null,
)

data class OfflineSyncRequest(val eventos: List<OfflinePontoEvent>)
data class OfflineSyncResult(
    val eventId: String,
    val status: String,
    val pausaId: String? = null,
    val mensagem: String? = null,
)
data class OfflineSyncResponse(
    val resultados: List<OfflineSyncResult>,
    val processados: List<String>,
    val pendentesComErro: List<String>,
)

interface PontoCafeApi {
    @POST("setup/device-activation") suspend fun activateDevice(@Body body: DeviceActivationRequest): DeviceActivationResponse
    @POST("ponto/device/unlock") suspend fun unlockDevice(@Body body: DeviceUnlockRequest): DeviceUnlockResponse
    @GET("ponto/colaboradores") suspend fun colaboradores(@Query("q") busca: String = ""): ColaboradoresResponse
    @GET("ponto/avatares") suspend fun avatarCatalog(): AvatarCatalogResponse
    @GET("ponto/horario") suspend fun horario(): HorarioCafeResponse
    @GET("health") suspend fun health(): SystemHealthResponse
    @GET("app-status") suspend fun appStatus(): AppStatusResponse
    @GET("ponto/biometria/catalogo") suspend fun catalogoBiometrico(
        @Query("modelo") modelo: String,
        @Query("versaoModelo") versaoModelo: String,
        @Query("versaoAtual") versaoAtual: String? = null,
    ): FaceCatalogResponse
    @POST("ponto/biometria/confirmar-local") suspend fun confirmarBiometriaLocal(@Body body: ConfirmarBiometriaLocalRequest): IdentificarBiometriaResponse
    @POST("ponto/biometria/identificar") suspend fun identificarBiometria(@Body body: IdentificarBiometriaRequest): IdentificarBiometriaResponse
    @POST("ponto/biometria/verificar") suspend fun verificarBiometria(@Body body: VerificarBiometriaRequest): VerificarBiometriaResponse
    @POST("ponto/registro-rapido") suspend fun registroRapido(@Body body: RegistroRapidoRequest): Response<RegistroRapidoResponse>
    @POST("ponto/operacoes/reconciliar") suspend fun reconciliarOperacao(@Body body: PontoOperationReconcileRequest): PontoOperationReconcileResponse
    @POST("ponto/pausas/iniciar") suspend fun iniciarPausa(@Body body: IniciarPausaRequest): Response<IniciarPausaResponse>
    @POST("ponto/pausas/finalizar") suspend fun finalizarPausa(@Body body: FinalizarPausaRequest): Response<FinalizarPausaResponse>
    @POST("ponto/offline/sincronizar") suspend fun sincronizarOffline(@Body body: OfflineSyncRequest): OfflineSyncResponse
}

class PontoCafeRepository(
    private val api: PontoCafeApi,
    private val operationJournal: PontoOperationJournal,
) {
    suspend fun activateDevice(token: String): String = api.activateDevice(DeviceActivationRequest(token)).token
    suspend fun validarPinSaida(pin: String, area: String): DeviceUnlockResponse =
        api.unlockDevice(DeviceUnlockRequest(pin.trim(), area))
    suspend fun listarColaboradores(busca: String = "") = api.colaboradores(busca).colaboradores
    suspend fun avatarCatalog(): Map<String, String> =
        api.avatarCatalog().avatares.associate { it.colaboradorId to it.avatarUrl }
    suspend fun consultarHorario(): HorarioCafeResponse = api.horario()
    suspend fun health(): SystemHealthResponse = api.health()
    suspend fun appStatus(): AppStatusResponse = api.appStatus()

    suspend fun sincronizarCatalogo(
        modelo: String,
        versaoModelo: String,
        versaoAtual: String? = null,
    ): FaceCatalogResponse {
        val response = api.catalogoBiometrico(modelo, versaoModelo, versaoAtual)
        if (!response.atualizado || response.templates.isEmpty()) return response

        val avatars = try {
            withTimeoutOrNull(AVATAR_CATALOG_TIMEOUT_MS) { avatarCatalog() }.orEmpty()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            emptyMap()
        }
        if (avatars.isEmpty()) return response

        return response.copy(
            templates = response.templates.map { template ->
                template.copy(
                    colaborador = template.colaborador.copy(
                        avatarUrl = avatars[template.colaborador.id],
                    ),
                )
            },
        )
    }

    suspend fun confirmarIdentidadeLocal(
        colaboradorId: String,
        embedding: FloatArray,
        modelo: String,
        versaoModelo: String,
    ): IdentificarBiometriaResponse {
        // Se uma mutação anterior ficou incerta, não podemos consultar o estado
        // atual e reinterpretá-lo como uma nova ação. O ViewModel já trata
        // IOException como caminho offline; a fila reutiliza o mesmo operationId
        // e o Worker reconcilia o COMMIT original antes de qualquer nova mutação.
        if (operationJournal.isUncertain(colaboradorId)) {
            throw IOException("O resultado do registro anterior ainda precisa ser reconciliado com o servidor.")
        }
        return api.confirmarBiometriaLocal(
            ConfirmarBiometriaLocalRequest(colaboradorId, embedding.toList(), modelo, versaoModelo),
        )
    }

    suspend fun identificar(embedding: FloatArray): IdentificarBiometriaResponse =
        api.identificarBiometria(IdentificarBiometriaRequest(embedding.toList()))

    suspend fun verificar(colaboradorId: String, embedding: FloatArray): VerificarBiometriaResponse =
        api.verificarBiometria(VerificarBiometriaRequest(colaboradorId, embedding.toList()))

    suspend fun registrarRapido(
        colaboradorId: String,
        embedding: FloatArray,
        modelo: String,
        versaoModelo: String,
    ): RegistroRapidoResponse? {
        try {
            // O caminho rápido consegue reconstruir exatamente o comprovante
            // autoritativo. Por isso tenta reconciliação antes de criar uma nova
            // operação. Casos não rápidos seguem para a fila offline, que usa o
            // mesmo UUID e também reconcilia no servidor.
            reconciliarOperacaoPendente(colaboradorId)?.let { reconciliada ->
                return when {
                    reconciliada.inicio != null -> RegistroRapidoResponse(
                        status = "INICIO",
                        colaborador = reconciliada.colaborador,
                        inicio = reconciliada.inicio,
                    )
                    reconciliada.retorno != null -> RegistroRapidoResponse(
                        status = "RETORNO",
                        colaborador = reconciliada.colaborador,
                        retorno = reconciliada.retorno,
                    )
                    else -> null
                }
            }
        } catch (error: Throwable) {
            if (isTemporaryFailure(error)) return null
            throw error
        }

        val operationId = operationJournal.prepare(colaboradorId, embedding)
        operationJournal.markUncertain(operationId)

        val response = try {
            api.registroRapido(
                RegistroRapidoRequest(
                    operacaoId = operationId,
                    colaboradorId = colaboradorId,
                    embedding = embedding.toList(),
                    modelo = modelo,
                    versaoModelo = versaoModelo,
                ),
            )
        } catch (_: IOException) {
            return null
        }

        if (response.code() == 404 || response.code() == 405 || response.code() == 501) {
            operationJournal.complete(operationId)
            return null
        }
        if (response.code() >= 500) {
            return null
        }
        if (!response.isSuccessful) {
            operationJournal.complete(operationId)
            throw HttpException(response)
        }

        val result = response.body() ?: return null
        when (result.status) {
            "INICIO", "RETORNO" -> Unit
            else -> operationJournal.complete(operationId)
        }
        return result
    }

    private suspend fun reconciliarOperacaoPendente(
        colaboradorId: String,
    ): PontoOperationReconcileResponse? {
        val operationId = operationJournal.pendingUncertainOperationId(colaboradorId) ?: return null
        val response = api.reconciliarOperacao(
            PontoOperationReconcileRequest(
                operacaoId = operationId,
                colaboradorId = colaboradorId,
            ),
        )
        if (!response.encontrada) {
            // O advisory lock no servidor garante que já não existe transação
            // concorrente com este UUID. Sem linha idempotente, não houve COMMIT.
            operationJournal.complete(operationId)
            return null
        }
        if (response.inicio == null && response.retorno == null) {
            throw IOException("O servidor encontrou a operação, mas não retornou um resultado reconciliável.")
        }
        return response
    }

    suspend fun iniciar(
        colaboradorId: String,
        verificacaoToken: String,
    ): IniciarPausaResponse = executarMutacaoConfirmada(
        colaboradorId = colaboradorId,
        acao = "INICIAR",
    ) { operationId ->
        api.iniciarPausa(
            IniciarPausaRequest(
                operacaoId = operationId,
                colaboradorId = colaboradorId,
                verificacaoToken = verificacaoToken,
            ),
        )
    }

    suspend fun finalizar(
        colaboradorId: String,
        verificacaoToken: String,
    ): FinalizarPausaResponse = executarMutacaoConfirmada(
        colaboradorId = colaboradorId,
        acao = "FINALIZAR",
    ) { operationId ->
        api.finalizarPausa(
            FinalizarPausaRequest(
                operacaoId = operationId,
                colaboradorId = colaboradorId,
                verificacaoToken = verificacaoToken,
            ),
        )
    }

    private suspend fun <T> executarMutacaoConfirmada(
        colaboradorId: String,
        acao: String,
        request: suspend (operationId: String) -> Response<T>,
    ): T {
        val operationId = operationJournal.prepareAction(colaboradorId, acao)
        operationJournal.markUncertain(operationId)

        val response = request(operationId)
        if (!response.isSuccessful) {
            if (response.code() < 500) {
                operationJournal.complete(operationId)
            }
            throw HttpException(response)
        }

        return response.body()
            ?: throw IOException("O servidor confirmou a requisição sem retornar o resultado do Ponto.")
    }

    suspend fun sincronizarOffline(eventos: List<OfflinePontoEvent>): OfflineSyncResponse =
        api.sincronizarOffline(OfflineSyncRequest(eventos))

    companion object {
        private const val AVATAR_CATALOG_TIMEOUT_MS = 1_000L

        fun isAuthFailure(error: Throwable): Boolean =
            error is HttpException && (error.code() == 401 || error.code() == 403)

        fun isTemporaryFailure(error: Throwable): Boolean =
            error is IOException || (error is HttpException && error.code() >= 500)

        fun mensagemErro(error: Throwable): String {
            if (error is HttpException) {
                val body = runCatching { error.response()?.errorBody()?.string() }.getOrNull()
                val apiMessage = runCatching { body?.let { JSONObject(it).optString("erro") } }.getOrNull()
                if (!apiMessage.isNullOrBlank()) return apiMessage
                return "Falha na comunicação com o servidor (${error.code()})."
            }
            if (error is IOException) return "Sem conexão com o servidor. Verifique a internet."
            return error.message ?: "Não foi possível concluir a operação."
        }
    }
}

object ApiClient {
    fun create(context: Context, tokenStore: SecureDeviceTokenStore): PontoCafeRepository {
        val tokenInterceptor = Interceptor { chain ->
            val token = tokenStore.read()
            val request = chain.request().newBuilder().apply {
                if (!token.isNullOrBlank()) header("X-Device-Token", token)
                header("X-App-Version", BuildConfig.VERSION_NAME)
            }.build()
            chain.proceed(request)
        }
        val okHttp = OkHttpClient.Builder().addInterceptor(tokenInterceptor).build()
        val retrofit = Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return PontoCafeRepository(
            api = retrofit.create(PontoCafeApi::class.java),
            operationJournal = PontoOperationJournal(context.applicationContext),
        )
    }
}
