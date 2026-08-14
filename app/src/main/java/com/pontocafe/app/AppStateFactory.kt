package com.pontocafe.app

import com.pontocafe.app.camera.FaceEmbeddingEngine
import com.pontocafe.app.data.PontoCafeRepository
import com.pontocafe.app.data.SecureDeviceTokenStore

fun createPontoCafeViewModel(
    repository: PontoCafeRepository,
    tokenStore: SecureDeviceTokenStore,
    embeddingEngine: FaceEmbeddingEngine,
): PontoCafeViewModel = PontoCafeViewModel(
    repository = repository,
    tokenStore = tokenStore,
    embeddingEngine = embeddingEngine,
)
