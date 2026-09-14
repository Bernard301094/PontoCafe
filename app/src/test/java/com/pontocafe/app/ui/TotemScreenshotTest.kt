package com.pontocafe.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import com.pontocafe.app.ComprovantePonto
import com.pontocafe.app.PontoCafeUiState
import com.pontocafe.app.PontoStep
import com.pontocafe.app.TipoComprovantePonto
import com.pontocafe.app.data.Colaborador
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Fotografias do totem, na JVM.
 *
 * Existem porque o visual do app foi mudado durante semanas sem ninguém o ver:
 * não há emulador nem aparelho na máquina de desenvolvimento, e "compila" não
 * diz como uma tela fica. Cada teste desenha um passo do totem com dados de
 * exemplo, no tamanho de um Galaxy A55, e grava um PNG.
 *
 * Só gravam com `-Proborazzi.record=true`; num `test` normal apenas desenham,
 * o que já apanha uma tela que rebenta ao compor.
 *
 *   ./gradlew :app:testDebugUnitTest --tests "*TotemScreenshotTest" -Proborazzi.record=true
 *
 * Os PNG saem em app/build/outputs/roborazzi/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class TotemScreenshotTest {

    private val pessoas = listOf(
        Colaborador(id = "a1", nome = "Ana Martins", setor = "Balcão", turno = "A"),
        Colaborador(id = "b2", nome = "Bruno Silveira", setor = "Cozinha", turno = "B"),
        Colaborador(id = "c3", nome = "Camila Rocha", setor = "Balcão", turno = "A"),
        Colaborador(id = "d4", nome = "Diego Almeida", setor = "Estoque", turno = "B"),
        Colaborador(id = "e5", nome = "Elisa Fontes", setor = "Caixa", turno = "A"),
    )

    private val base = PontoCafeUiState(
        deviceConfigured = true,
        colaboradores = pessoas,
        qrHabilitado = true,
    )

    @Test
    fun passo1_escolherPessoa() = foto("totem-1-pessoa", base)

    @Test
    fun passo2_digitarCodigo() = foto(
        "totem-2-codigo",
        base.copy(
            passo = PontoStep.DIGITAR_CODIGO,
            selecionado = pessoas[2],
            acaoEsperada = "SAIDA",
            codigo = "A7K2",
        ),
    )

    @Test
    fun passo3_comprovante() = foto(
        "totem-3-comprovante",
        base.copy(
            passo = PontoStep.COMPROVANTE,
            selecionado = pessoas[2],
            comprovante = ComprovantePonto(
                tipo = TipoComprovantePonto.INICIO,
                nome = "Camila Rocha",
                horarioRegistrado = "10:15",
                retornoAte = "10:31",
                contagemComecaAs = "10:16",
                limiteSegundos = 900,
                carenciaSegundos = 60,
            ),
        ),
    )

    @Test
    fun passo2_codigoRecusado() = foto(
        "totem-2b-codigo-recusado",
        base.copy(
            passo = PontoStep.DIGITAR_CODIGO,
            selecionado = pessoas[2],
            acaoEsperada = "SAIDA",
            codigo = "A7K2M9",
            erro = "Código inválido para este colaborador. Confira o nome selecionado.",
        ),
    )

    @Test
    fun passo2_retornoComPausaAberta() = foto(
        "totem-2c-retorno",
        base.copy(
            passo = PontoStep.DIGITAR_CODIGO,
            selecionado = pessoas[1],
            acaoEsperada = "RETORNO",
            codigo = "",
            pausaAberta = com.pontocafe.app.data.PausaAbertaResumo(
                id = "p1",
                periodo = "MANHA",
                inicioEm = "2026-09-14T10:15:00-03:00",
                inicioLocal = "10:15",
                limiteSegundos = 900,
                carenciaSegundos = 60,
                tempoDecorridoSegundos = 420,
                retornoAteLocal = "10:31",
            ),
        ),
    )

    @Test
    fun ativacaoDoAparelho() {
        captureRoboImage("build/outputs/roborazzi/totem-0-ativar.png") {
            PontoCafeTheme {
                DeviceSetupContent(
                    carregando = false,
                    erro = null,
                    mensagem = null,
                    onAtivar = {},
                    onAdminClick = {},
                    onSupervisorClick = {},
                    tokenInicial = "aB3xK9",
                )
            }
        }
    }

    private fun foto(nome: String, state: PontoCafeUiState) {
        captureRoboImage("build/outputs/roborazzi/$nome.png") {
            Totem(state)
        }
    }

    @Composable
    private fun Totem(state: PontoCafeUiState) {
        PontoCafeTheme {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                KioskTopBar(
                    offline = false,
                    pendingEvents = 0,
                    hasAdminSession = false,
                    hasSupervisorSession = false,
                    onAdmin = {},
                    onSupervisor = {},
                    onAccess = {},
                )
                KioskStepContent(
                    state = state,
                    actions = KioskActions.Inertes,
                    compactHeight = false,
                    onInteracao = {},
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        }
    }
}
