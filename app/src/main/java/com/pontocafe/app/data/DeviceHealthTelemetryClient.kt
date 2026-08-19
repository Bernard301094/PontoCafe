package com.pontocafe.app.data

import com.pontocafe.app.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST


data class DeviceHealthTelemetryRequest(
    val appVersion: String = BuildConfig.VERSION_NAME,
    val crashCount: Int,
    val lastCrashMillis: Long,
    val lastCrashType: String?,
    val lastCrashLocation: String?,
    val stallCount: Int,
    val lastStallMillis: Long,
    val lastStallDurationMillis: Long,
    val deviceModel: String?,
    val androidVersion: String?,
)

data class DeviceHealthTelemetryResponse(
    val ok: Boolean,
    val requestId: String?,
)

private interface DeviceHealthTelemetryApi {
    @POST("ponto/telemetria/saude")
    suspend fun send(@Body body: DeviceHealthTelemetryRequest): DeviceHealthTelemetryResponse
}

object DeviceHealthTelemetryClient {
    suspend fun send(
        tokenStore: SecureDeviceTokenStore,
        snapshot: AppHealthSnapshot,
        deviceModel: String?,
        androidVersion: String?,
    ): DeviceHealthTelemetryResponse {
        val token = tokenStore.read()?.takeIf { it.isNotBlank() }
            ?: error("Dispositivo ainda não ativado.")
        val interceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .header("X-Device-Token", token)
                .header("X-App-Version", BuildConfig.VERSION_NAME)
                .build()
            chain.proceed(request)
        }
        val api = Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(OkHttpClient.Builder().addInterceptor(interceptor).build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DeviceHealthTelemetryApi::class.java)

        return api.send(
            DeviceHealthTelemetryRequest(
                crashCount = snapshot.crashCount,
                lastCrashMillis = snapshot.lastCrashMillis,
                lastCrashType = snapshot.lastCrashType,
                lastCrashLocation = snapshot.lastCrashLocation,
                stallCount = snapshot.stallCount,
                lastStallMillis = snapshot.lastStallMillis,
                lastStallDurationMillis = snapshot.lastStallDurationMillis,
                deviceModel = deviceModel?.take(120),
                androidVersion = androidVersion?.take(40),
            ),
        )
    }
}
