package com.pontocafe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Fotografia das peças partilhadas das áreas de Administrador e Supervisor.
 *
 * As telas de gestão recebem ViewModels que não se constroem num teste, por
 * isso esta imagem monta uma tela típica com os mesmos componentes que elas
 * usam -- o cabeçalho, as tarjetas, os botões e as pílulas. Se o estilo do
 * painel chegou a eles, chegou às seis telas que os usam.
 *
 *   ./gradlew :app:testDebugUnitTest --tests "*GestaoScreenshotTest" -Proborazzi.record=true
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class GestaoScreenshotTest {

    @Test
    fun componentesDaGestao() {
        captureRoboImage("build/outputs/roborazzi/gestao-componentes.png") {
            PontoCafeTheme {
                PcHeroPage(
                    heroContent = {
                        PcHeroZoneTopBar(
                            title = "Operação",
                            eyebrow = "Supervisor",
                            account = null,
                            fallbackName = "Marina Costa",
                            onProfileClick = {},
                            onBackToPonto = {},
                        )
                        Text(
                            "Dados ao vivo atualizados",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    sheetContent = {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            PcActionTile(
                                title = "Equipe ao vivo",
                                supportingText = "Quem está em pausa e há quanto tempo",
                                icon = Icons.Default.Groups,
                                onClick = {},
                            )
                            PcActionTile(
                                title = "Códigos de café",
                                supportingText = "Emitir os códigos do dia",
                                icon = Icons.Default.Key,
                                onClick = {},
                            )
                            PcSectionSurface {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Camila Rocha", style = MaterialTheme.typography.titleMedium)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        StatusPill("Em pausa", PontoCafeTone.WARNING)
                                        StatusPill("Código ativo", PontoCafeTone.SUCCESS)
                                    }
                                }
                            }
                            PcPrimaryButton(text = "Emitir códigos do dia", onClick = {}, modifier = Modifier.fillMaxWidth())
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PcSecondaryButton(text = "Atualizar", onClick = {}, modifier = Modifier.weight(1f))
                                PcTonalButton(text = "Filtrar", onClick = {}, modifier = Modifier.weight(1f))
                            }
                            PcPrimaryButton(text = "Desabilitado", onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth())
                        }
                    },
                )
            }
        }
    }
}
