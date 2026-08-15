package com.pontocafe.app

import com.pontocafe.app.data.AdminRepository
import com.pontocafe.app.data.DeviceCreatedResponse

/**
 * Compatibilidade temporária para o fluxo antigo do AdminViewModel.
 *
 * A interface 0.6.x usa AdminDeviceViewModel e exige PIN por dispositivo.
 * Mantemos esta assinatura apenas para que código legado ainda compilado no
 * módulo não possa reintroduzir silenciosamente dispositivos sem PIN.
 */
@Deprecated(
    message = "Fluxo legado sem PIN. Use AdminDeviceViewModel.criarDispositivo(nome, pin).",
    level = DeprecationLevel.WARNING,
)
suspend fun AdminRepository.createDevice(name: String): DeviceCreatedResponse {
    error("O cadastro de dispositivo exige um PIN individual. Use a tela Gestão > Dispositivos.")
}
