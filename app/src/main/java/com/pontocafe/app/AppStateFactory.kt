package com.pontocafe.app

import android.content.Context
import com.pontocafe.app.camera.LiteRtFaceEmbeddingEngine
import com.pontocafe.app.data.PontoCafeRepository
import com.pontocafe.app.data.SecureDeviceTokenStore

fun createPontoCafeViewModel(
    context: Context,
    repository: PontoCafeRepository,
    tokenStore: SecureDeviceTokenStore,
): PontoCafeViewModel = PontoCafeViewModel(
    repository = repository,
    tokenStore = tokenStore,
    embeddingEngine = LiteRtFaceEmbeddingEngine(context.applicationContext),
)
