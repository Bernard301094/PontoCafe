package com.pontocafe.app.data

import android.content.Context
import android.os.Build
import com.pontocafe.app.BuildConfig
import java.io.IOException
import okhttp3.Interceptor
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


data class Colaborador(
    val id: String,
    val nome: String,
    val setor: String?,
    val turno: String?,
    val ativo: Boolean = true,
    /** Preenchidos só pelas rotas de gestão; o quiosque não os recebe. */
    val emPausa: Boolean = false,
    val codigoAtivo: Boolean = false,
    /**
     * A pessoa já fechou a pausa deste período hoje, então não pode tomar outro
     * café até o próximo período.
     *
     * Chega apenas pela rota de gestão. O quiosque não recebe este campo porque
     * lá o servidor já remove essas pessoas da lista — menos informação a sair
     * do que um sinalizador que o aparelho teria de filtrar.
     */
    val pausaPeriodoConcluida: Boolean = false,
    @Deprecated("Matrícula não é mais utilizada pelo Ponto Café")
    val matricula: String? = null,
)

data class ColaboradoresResponse(val colaboradores: List<Colaborador>)

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
    val carenciaSegundos: Int = 60,
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
    val tamanhoCodigoAcesso: Int = 6,
    /** Janela para apresentar o código na SAÍDA. O retorno não tem prazo. */
    val codigoValidadeSegundos: Int = 120,
    val carenciaSegundos: Int = 60,
)

data class PausaAbertaResumo(
    val id: String,
    val periodo: String,
    val inicioEm: String,
    val inicioLocal: String,
    val limiteSegundos: Int,
    val carenciaSegundos: Int,
    val tempoDecorridoSegundos: Int,
    val retornoAteLocal: String,
)

/**
 * Estado do colaborador escolhido na lista do quiosque.
 *
 * `acaoEsperada` existe só para o ecrã dizer a frase certa ("digite o código
 * para sair" vs "digite o mesmo código para voltar"). Quem decide de verdade é
 * o servidor no momento do registro — este valor pode ficar desatualizado e
 * isso não causa erro nenhum.
 */
data class ColaboradorPausaResponse(
    val colaborador: Colaborador,
    val acaoEsperada: String,
    val tamanhoCodigo: Int = 6,
    val periodosUsadosHoje: List<String> = emptyList(),
    val pausaAberta: PausaAbertaResumo? = null,
)

data class RegistrarPontoRequest(
    val operacaoId: String,
    val colaboradorId: String,
    val codigo: String,
)

data class InicioPausaResponse(
    val id: String,
    val periodo: String,
    val limiteSegundos: Int,
    val carenciaSegundos: Int,
    val foraHorario: Boolean,
    val inicioEm: String,
    val inicioLocal: String,
    val contagemInicioEm: String,
    val contagemInicioLocal: String,
    val retornoAteLocal: String,
)

data class RetornoPausaResponse(
    val id: String,
    val inicioLocal: String,
    val fimEm: String,
    val fimLocal: String,
    val duracaoSegundos: Int,
    val tempoContadoSegundos: Int,
    val limiteSegundos: Int,
    val carenciaSegundos: Int,
    val excedeuLimite: Boolean,
)

data class RegistroPontoResponse(
    val status: String,
    val colaborador: Colaborador? = null,
    val inicio: InicioPausaResponse? = null,
    val retorno: RetornoPausaResponse? = null,
)

data class PontoOperationReconcileRequest(
    val operacaoId: String,
    val colaboradorId: String,
)

data class PontoOperationReconcileResponse(
    val encontrada: Boolean,
    val status: String? = null,
    val colaborador: Colaborador? = null,
    val inicio: InicioPausaResponse? = null,
    val retorno: RetornoPausaResponse? = null,
)

data class OfflineSyncRequest(val eventos: List<OfflinePontoEvent>)
data class OfflineSyncResult(
    val eventId: String,
    val status: String,
    val pausaId: String? = null,
    val tipo: String? = null,
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
    @GET("ponto/colaboradores/{id}/pausa") suspend fun estadoDaPausa(@Path("id") id: String): ColaboradorPausaResponse
    @GET("ponto/horario") suspend fun horario(): HorarioCafeResponse
    @GET("health") suspend fun health(): SystemHealthResponse
    @GET("app-status") suspend fun appStatus(): AppStatusResponse
    @POST("ponto/pausas/registrar") suspend fun registrarPonto(@Body body: RegistrarPontoRequest): Response<RegistroPontoResponse>
    @POST("ponto/operacoes/reconciliar") suspend fun reconciliarOperacao(@Body body: PontoOperationReconcileRequest): PontoOperationReconcileResponse
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
    suspend fun estadoDaPausa(colaboradorId: String): ColaboradorPausaResponse = api.estadoDaPausa(colaboradorId)
    suspend fun consultarHorario(): HorarioCafeResponse = api.horario()
    suspend fun health(): SystemHealthResponse = api.health()
    suspend fun appStatus(): AppStatusResponse = api.appStatus()

    /**
     * Envia o par (pessoa, código) e devolve o que o servidor decidiu: saída ou
     * retorno.
     *
     * Antes de criar uma operação nova, reconcilia qualquer UUID que tenha ficado
     * incerto — uma resposta perdida depois do COMMIT tem de reaparecer como o
     * mesmo comprovante, nunca como uma segunda batida.
     */
    suspend fun registrar(colaboradorId: String, codigo: String): RegistroPontoResponse {
        reconciliarOperacaoPendente(colaboradorId)?.let { reconciliada ->
            return RegistroPontoResponse(
                status = reconciliada.status ?: if (reconciliada.inicio != null) "INICIO" else "RETORNO",
                colaborador = reconciliada.colaborador,
                inicio = reconciliada.inicio,
                retorno = reconciliada.retorno,
            )
        }

        val operationId = operationJournal.prepareCode(colaboradorId, codigo)
        operationJournal.markUncertain(operationId)

        val response = api.registrarPonto(
            RegistrarPontoRequest(
                operacaoId = operationId,
                colaboradorId = colaboradorId,
                codigo = codigo,
            ),
        )

        if (!response.isSuccessful) {
            // Um 4xx é uma decisão final do servidor: nada foi gravado, então o
            // UUID pode ser libertado. Um 5xx deixa o resultado em aberto e o
            // diário mantém a marca de incerteza para a próxima tentativa.
            if (response.code() < 500) operationJournal.complete(operationId)
            throw HttpException(response)
        }

        return response.body()
            ?: throw IOException("O servidor confirmou a requisição sem retornar o resultado do Ponto.")
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
        operationJournal.complete(operationId)
        return response
    }

    suspend fun sincronizarOffline(eventos: List<OfflinePontoEvent>): OfflineSyncResponse =
        api.sincronizarOffline(OfflineSyncRequest(eventos))

    companion object {
        /**
         * Used only where the request itself is a protected device-auth probe
         * (currently /ponto/horario). Business 403 responses are intentionally
         * excluded so rules such as "Código inválido" can never revoke a
         * device session.
         */
        fun isAuthFailure(error: Throwable): Boolean =
            error is HttpException && error.code() == 401

        fun isDevicePinNotConfigured(error: Throwable): Boolean =
            error is HttpException && error.code() == 409

        fun isTemporaryFailure(error: Throwable): Boolean =
            error is IOException || (error is HttpException && error.code() >= 500)

        /** Código do erro de negócio devolvido pelo Worker, quando existe. */
        fun codigoErro(error: Throwable): String? {
            if (error !is HttpException) return null
            val body = runCatching { error.response()?.errorBody()?.string() }.getOrNull() ?: return null
            return runCatching { JSONObject(body).optString("codigo").takeIf { it.isNotBlank() } }.getOrNull()
        }

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

internal fun safeHttpHeaderValue(value: String, maxLength: Int): String {
    if (maxLength <= 0) return ""
    val safe = buildString(value.length) {
        value.trim().forEach { char ->
            append(if (char.code in 0x20..0x7e) char else '-')
        }
    }
    return safe
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(maxLength)
}

object ApiClient {
    fun create(context: Context, tokenStore: SecureDeviceTokenStore): PontoCafeRepository {
        val deviceModel = safeHttpHeaderValue(
            listOf(Build.MANUFACTURER, Build.MODEL)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(" "),
            120,
        )
        val androidVersion = safeHttpHeaderValue(
            buildString {
                val release = Build.VERSION.RELEASE?.trim().orEmpty()
                if (release.isNotBlank()) append(release)
                if (isNotEmpty()) append(" - ")
                append("API ${Build.VERSION.SDK_INT}")
            },
            40,
        )
        val appVersion = safeHttpHeaderValue(BuildConfig.VERSION_NAME, 80)

        val tokenInterceptor = Interceptor { chain ->
            val token = tokenStore.read()
            val request = chain.request().newBuilder().apply {
                if (!token.isNullOrBlank()) header("X-Device-Token", token)
                if (appVersion.isNotBlank()) header("X-App-Version", appVersion)
                if (deviceModel.isNotBlank()) header("X-Device-Model", deviceModel)
                if (androidVersion.isNotBlank()) header("X-Android-Version", androidVersion)
            }.build()
            chain.proceed(request)
        }
        val okHttp = PontoHttpClients.baseBuilder()
            .addInterceptor(tokenInterceptor)
            .addInterceptor(DeviceAuthResponseInterceptor())
            .build()
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
