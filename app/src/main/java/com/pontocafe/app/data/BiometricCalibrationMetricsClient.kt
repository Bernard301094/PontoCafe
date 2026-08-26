package com.pontocafe.app.data

import com.pontocafe.app.BuildConfig
import okhttp3.Interceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET


data class BiometricCalibrationMetrics(
    val amostras: Int,
    val aprovadas: Int,
    val falseRejectRate: Double?,
    val top1Accuracy: Double?,
    val falseAcceptRate: Double?,
    val comparacoesImpostor: Int,
    val falsosAceitesImpostor: Int,
    val scoreMedio: Double?,
    val margemMedia: Double?,
    val limiar: Double,
    val margemMinima: Double,
    val observacao: String,
)

interface BiometricCalibrationMetricsApi {
    @GET("gestao/biometria/calibracao/resumo")
    suspend fun summary(): BiometricCalibrationMetrics
}

class BiometricCalibrationMetricsRepository(
    private val api: BiometricCalibrationMetricsApi,
) {
    suspend fun summary() = api.summary()
}

object BiometricCalibrationMetricsApiClient {
    fun create(sessionStore: SecureAdminSessionStore): BiometricCalibrationMetricsRepository {
        val interceptor = Interceptor { chain ->
            val request = chain.request().newBuilder().apply {
                sessionStore.read()?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") }
                header("X-App-Version", BuildConfig.VERSION_NAME)
            }.build()
            chain.proceed(request)
        }
        val retrofit = Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(PontoHttpClients.baseBuilder().addInterceptor(interceptor).build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return BiometricCalibrationMetricsRepository(retrofit.create(BiometricCalibrationMetricsApi::class.java))
    }
}
