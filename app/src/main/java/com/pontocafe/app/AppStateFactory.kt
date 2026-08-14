package com.pontocafe.app

import com.pontocafe.app.camera.FaceEmbeddingEngine
import com.pontocafe.app.data.PontoCafeRepository
import com.pontocafe.app.data.SecureDeviceTokenStore
import com.pontocafe.app.data.SecureFaceCatalogStore

fun createPontoCafeViewModel(
    repository: PontoCafeRepository,
    tokenStore: SecureDeviceTokenStore,
    faceCatalogStore: SecureFaceCatalogStore,
    embeddingEngine: FaceEmbeddingEngine,
): PontoCafeViewModel = PontoCafeViewModel(
    repository = repository,
    tokenStore = tokenStore,
    faceCatalogStore = faceCatalogStore,
    embeddingEngine = embeddingEngine,
)
