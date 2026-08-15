package com.pontocafe.app

import com.pontocafe.app.data.AdminRepository

/**
 * Compatibilidade temporária com o fluxo antigo do AdminViewModel.
 * Novos dispositivos devem ser criados pela tela Dispositivos e PIN,
 * onde o Administrador escolhe explicitamente o PIN inicial.
 */
suspend fun AdminRepository.createDevice(name: String) = createDevice(name, "10227910")
