package com.pontocafe.app.data

import android.content.Context
import com.pontocafe.app.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.json.JSONObject
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query


data class Colaborador(
    val id: String,
    val matricula: String?,
    val nome: String,
    val setor: String?,
    val turno: String?,
)

data class ColaboradoresResponse(val colaboradores: List<Colaborador>)

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

data class FaceCatalogResponse(
    val atualizado: Boolean,
    val versao: String,
    val modelo: String,
    val versaoModelo: String,
    val limiar: Double,
    val margem: Double,
    val templates: List<CachedFaceTemplate>,
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

data class VerificarBiometriaRequest(
    val colaboradorId: String,
    val embedding: List<Float>,
)

data class VerificarBiometriaResponse(
    val reconhecido: Boolean,
    val score: Double,
    val verificacaoToken: String?,
    val expiraEmSegundos: Int?,
)

data class IniciarPausaRequest(
    val colaboradorId: String,
    val verificacaoToken: String,
    val periodo: String? = null,
    val codigoAutorizacao: String? = null,
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

interface PontoCafeApi {
    @GET("ponto/colaboradores")
    suspend fun colaboradores(@Query("q") busca: String = ""): ColaboradoresResponse

    @GET("ponto/horario")
    suspend fun horario(): HorarioCafeResponse

    @GET("ponto/biometria/catalogo")
    suspend fun catalogoBiometrico(
        @Query("modelo") modelo: String,
        @Query("versaoModelo") versaoModelo: String,
        @Query("versaoAtual") versaoAtual: String? = null,
    ): FaceCatalogResponse

    @POST("ponto/biometria/confirmar-local")
    suspend fun confirmarBiometriaLocal(
        @Body body: ConfirmarBiometriaLocalRequest,
    ): IdentificarBiometriaResponse

    @POST("ponto/biometria/identificar")
    suspend fun identificarBiometria(@Body body: IdentificarBiometriaRequest): IdentificarBiometriaResponse

    @POST("ponto/biometria/verificar")
    suspend fun verificarBiometria(@Body body: VerificarBiometriaRequest): VerificarBiometriaResponse

    @POST("ponto/pausas/iniciar")
    suspend fun iniciarPausa(@Body body: IniciarPausaRequest): IniciarPausaResponse

    @POST("ponto/pausas/finalizar")
    suspend fun finalizarPausa(@Body body: FinalizarPausaRequest): FinalizarPausaResponse
}

class PontoCafeRepository(
    private val api: PontoCafeApi,
) {
    suspend fun listarColaboradores(busca: String = "") = api.colaboradores(busca).colaboradores

    suspend fun consultarHorario(): HorarioCafeResponse = api.horario()

    suspend fun sincronizarCatalogo(
        modelo: String,
        versaoModelo: String,
        versaoAtual: String? = null,
    ): FaceCatalogResponse = api.catalogoBiometrico(modelo, versaoModelo, versaoAtual)

    suspend fun confirmarIdentidadeLocal(
        colaboradorId: String,
        embedding: FloatArray,
        modelo: String,
        versaoModelo: String,
    ): IdentificarBiometriaResponse = api.confirmarBiometriaLocal(
        ConfirmarBiometriaLocalRequest(
            colaboradorId = colaboradorId,
            embedding = embedding.toList(),
            modelo = modelo,
            versaoModelo = versaoModelo,
        ),
    )

    suspend fun identificar(embedding: FloatArray): IdentificarBiometriaResponse =
        api.identificarBiometria(IdentificarBiometriaRequest(embedding.toList()))

    suspend fun verificar(colaboradorId: String, embedding: FloatArray): VerificarBiometriaResponse =
        api.verificarBiometria(VerificarBiometriaRequest(colaboradorId, embedding.toList()))

    suspend fun iniciar(
        colaboradorId: String,
        verificacaoToken: String,
        periodo: String? = null,
        codigoAutorizacao: String? = null,
    ): IniciarPausaResponse = api.iniciarPausa(
        IniciarPausaRequest(
            colaboradorId = colaboradorId,
            verificacaoToken = verificacaoToken,
            periodo = periodo,
            codigoAutorizacao = codigoAutorizacao,
        ),
    )

    suspend fun finalizar(colaboradorId: String, verificacaoToken: String): FinalizarPausaResponse =
        api.finalizarPausa(FinalizarPausaRequest(colaboradorId, verificacaoToken))

    companion object {
        fun mensagemErro(error: Throwable): String {
            if (error is HttpException) {
                val body = runCatching { error.response()?.errorBody()?.string() }.getOrNull()
                val apiMessage = runCatching { body?.let { JSONObject(it).optString("erro") } }.getOrNull()
                if (!apiMessage.isNullOrBlank()) return apiMessage
                return "Falha na comunicação com o servidor (${error.code()})."
            }
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
            }.build()
            chain.proceed(request)
        }

        val okHttp = OkHttpClient.Builder()
            .addInterceptor(tokenInterceptor)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return PontoCafeRepository(retrofit.create(PontoCafeApi::class.java))
    }
}
