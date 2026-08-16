package com.pontocafe.app.data

/** Compatibilidade tipada para a chamada legada que ainda passa o perfil como String. */
suspend fun AdminApi.changeProfile(userId: String, profile: String): SimpleAdminResponse =
    changeProfile(userId, ChangeProfileRequest(profile))
