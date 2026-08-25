package com.pontocafe.app.haptics

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Test

class PontoHapticsTest {

    @Test
    fun `a partir do Android 10 os eventos importantes usam efeitos predefinidos`() {
        assertEquals(PontoHapticEffectKind.PREDEFINED_TICK, selectSuccessEffectKind(Build.VERSION_CODES.Q))
        assertEquals(PontoHapticEffectKind.PREDEFINED_DOUBLE_CLICK, selectWarningEffectKind(Build.VERSION_CODES.Q))
        assertEquals(PontoHapticEffectKind.PREDEFINED_TICK, selectSuccessEffectKind(Build.VERSION_CODES.Q + 5))
    }

    @Test
    fun `abaixo do Android 10 o fallback e um pulso curto, nunca uma falha`() {
        assertEquals(PontoHapticEffectKind.ONE_SHOT_SHORT, selectSuccessEffectKind(Build.VERSION_CODES.O))
        assertEquals(PontoHapticEffectKind.ONE_SHOT_LONG, selectWarningEffectKind(Build.VERSION_CODES.O))
    }

    @Test
    fun `no minSdk suportado o resultado continua definido`() {
        // minSdk = 26 (Android O); a seleção nunca deve lançar exceção nem
        // retornar um estado indefinido no piso da faixa suportada.
        assertEquals(PontoHapticEffectKind.ONE_SHOT_SHORT, selectSuccessEffectKind(26))
        assertEquals(PontoHapticEffectKind.ONE_SHOT_LONG, selectWarningEffectKind(26))
    }
}
