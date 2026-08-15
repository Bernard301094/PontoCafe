package com.pontocafe.app

import com.pontocafe.app.data.AdminRepository
import com.pontocafe.app.data.DeviceCreatedResponse

/**
 * Compatibilidade temporária com o AdminViewModel antigo.
 * O fluxo visual de criação de dispositivos foi movido para "Dispositivos e PIN",
 * onde o Administrador informa explicitamente o PIN de cada aparelho.
 *
 * Esta sobrecarga não cria dispositivos e não contém PIN padrão; existe apenas para
 * manter a assinatura antiga tipada até a remoção definitiva do código legado.
 */
suspend fun AdminRepository.createDevice(name: String): DeviceCreatedResponse =
    error("Cadastre o dispositivo pela tela Dispositivos e PIN.")
