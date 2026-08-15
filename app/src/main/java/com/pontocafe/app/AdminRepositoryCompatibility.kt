package com.pontocafe.app

import com.pontocafe.app.data.AdminRepository

/**
 * Mantém somente a assinatura antiga para que o AdminViewModel legado compile.
 * O fluxo visual antigo foi removido; novos dispositivos são criados exclusivamente
 * em Dispositivos e PIN, onde o Administrador informa o PIN explicitamente.
 */
suspend fun AdminRepository.createDevice(name: String): Nothing =
    error("Cadastre o dispositivo pela tela Dispositivos e PIN.")
