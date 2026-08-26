package com.pontocafe.app.data

import java.util.concurrent.TimeUnit
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient

/**
 * Every backend-facing repository (kiosk, admin, supervisor, reliability,
 * telemetry, calibration) used to build its own OkHttpClient with no timeout
 * configuration and no shared connection pool, so each area of the app paid
 * its own TCP/TLS handshake to the same host and a stalled call could hang
 * for OkHttp's 10s default before the kiosk fell back to offline mode.
 * Building every client's OkHttpClient off this shared base lets them reuse
 * warm connections to the API host and fail fast enough for the kiosk to
 * degrade gracefully instead of freezing on a bad network.
 */
object PontoHttpClients {
    private val connectionPool = ConnectionPool(5, 5, TimeUnit.MINUTES)
    private val dispatcher = Dispatcher().apply { maxRequestsPerHost = 8 }

    fun baseBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .connectionPool(connectionPool)
        .dispatcher(dispatcher)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
}
