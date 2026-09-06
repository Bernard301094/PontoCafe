package com.pontocafe.app

import com.pontocafe.app.data.PontoCafeRepository
import com.pontocafe.app.data.SecureDeviceTokenStore
import com.pontocafe.app.data.SecurePontoOfflineStore

fun createPontoCafeViewModel(
    repository: PontoCafeRepository,
    tokenStore: SecureDeviceTokenStore,
    offlineStore: SecurePontoOfflineStore,
): PontoCafeViewModel = PontoCafeViewModel(
    repository = repository,
    tokenStore = tokenStore,
    offlineStore = offlineStore,
)
