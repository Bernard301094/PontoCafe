package com.pontocafe.app.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.Interceptor
import org.json.JSONObject

internal const val DEVICE_AUTH_INVALID_CODE = "DEVICE_AUTH_INVALID"

/**
 * Process-scoped signal emitted only when the Worker authoritatively rejects the
 * device credential. Temporary connectivity failures and business-rule 4xx
 * responses never publish into this bus.
 */
object DeviceAuthInvalidationBus {
    private val mutableEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val events = mutableEvents.asSharedFlow()

    internal fun emit() {
        mutableEvents.tryEmit(Unit)
    }
}

/**
 * Observes Worker responses without consuming the Retrofit body. A device is
 * revoked only for the explicit machine-readable 401 contract.
 */
internal class DeviceAuthResponseInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val response = chain.proceed(chain.request())
        if (response.code != 401) return response

        val payload = runCatching {
            val body = response.peekBody(MAX_ERROR_BODY_BYTES).string()
            JSONObject(body)
        }.getOrNull() ?: return response

        if (payload.optString("codigo") == DEVICE_AUTH_INVALID_CODE) {
            DeviceAuthInvalidationBus.emit()
        }
        return response
    }

    companion object {
        private const val MAX_ERROR_BODY_BYTES = 8L * 1024L
    }
}
