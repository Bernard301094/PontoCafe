package com.pontocafe.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import com.pontocafe.app.PontoCafeUiState
import com.pontocafe.app.data.Colaborador
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** O totem de parede: passo à esquerda, painel operacional à direita. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp-mdpi")
class TotemTabletScreenshotTest {

    @Test
    fun totemDeParede() {
        val state = PontoCafeUiState(
            deviceConfigured = true,
            qrHabilitado = true,
            colaboradores = listOf(
                Colaborador(id = "a1", nome = "Ana Martins", setor = "Balcão", turno = "A"),
                Colaborador(id = "b2", nome = "Bruno Silveira", setor = "Cozinha", turno = "B"),
            ),
        )
        captureRoboImage("build/outputs/roborazzi/totem-tablet.png") {
            PontoCafeTheme {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    KioskTopBar(
                        offline = false,
                        pendingEvents = 0,
                        hasAdminSession = false,
                        hasSupervisorSession = false,
                        onAdmin = {},
                        onSupervisor = {},
                        onAccess = {},
                    )
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                        KioskStepContent(
                            state = state,
                            actions = KioskActions.Inertes,
                            compactHeight = false,
                            onInteracao = {},
                            modifier = Modifier.weight(.6f).fillMaxHeight(),
                        )
                        KioskOperationalPanel(
                            state = state,
                            modifier = Modifier.weight(.4f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}
