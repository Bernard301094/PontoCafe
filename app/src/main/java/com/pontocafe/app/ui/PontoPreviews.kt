package com.pontocafe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Os dois aparelhos que o Ponto Café realmente encontra em produção.
 *
 * Não são tamanhos genéricos de catálogo: o A55 é o telefone do Supervisor e o
 * segundo é o tablet montado na parede do quiosque. Uma tela que fica bem nos
 * dois fica bem no meio; o contrário não é verdade, e era exatamente no A55 em
 * retrato que os cortes de texto apareciam.
 *
 * Use os dois em cada `@Preview` de tela. Um preview só de telefone deixa
 * passar layouts que esticam feio a 1200dp; um só de tablet deixa passar texto
 * cortado a 411dp.
 */
const val PREVIEW_PHONE = "spec:width=411dp,height=914dp,dpi=420"
const val PREVIEW_PHONE_LANDSCAPE = "spec:width=914dp,height=411dp,dpi=420"
const val PREVIEW_KIOSK = "spec:width=1280dp,height=800dp,dpi=240"

/**
 * Envolve o conteúdo no tema real do app.
 *
 * Sem isto, o preview desenha com as cores padrão do Material e mente sobre o
 * resultado — o Ponto Café define paleta, formas e tipografia próprias, e é
 * justamente aí que os problemas de contraste aparecem.
 */
@Composable
fun PontoPreviewSurface(content: @Composable () -> Unit) {
    PontoCafeTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            content()
        }
    }
}

@Preview(name = "Botões · A55 retrato", device = PREVIEW_PHONE, showBackground = true)
@Preview(name = "Botões · quiosque", device = PREVIEW_KIOSK, showBackground = true)
@Composable
private fun PreviewPontoButtons() {
    PontoPreviewSurface {
        Column(
            modifier = Modifier.padding(PontoCafeSpacing.md),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            // O alvo tocável cresce sozinho de 48dp no telefone para 64dp no
            // quiosque; os dois previews lado a lado mostram a diferença.
            Text("Alvo tocável: ${pontoTouchTarget()}", style = MaterialTheme.typography.labelLarge)
            PcPrimaryButton(text = "Gerar código", onClick = {}, icon = Icons.Default.Coffee)
            PcPrimaryButton(text = "Gerando…", onClick = {}, loading = true)
            PcSecondaryButton(text = "Cancelar", onClick = {})
            PcDangerButton(text = "Excluir colaborador", onClick = {})
            PcPrimaryButton(text = "Indisponível", onClick = {}, enabled = false)
        }
    }
}

@Preview(name = "Avisos · A55 retrato", device = PREVIEW_PHONE, showBackground = true)
@Preview(name = "Avisos · quiosque", device = PREVIEW_KIOSK, showBackground = true)
@Composable
private fun PreviewPontoFeedback() {
    PontoPreviewSurface {
        Column(
            modifier = Modifier.padding(PontoCafeSpacing.md),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            PcStateBanner(
                title = "Configuração pendente",
                supportingText = "2 dispositivo(s) sem PIN configurado",
                tone = PontoCafeTone.WARNING,
            )
            PcStateBanner(
                title = "Tudo pronto para operar",
                supportingText = "Não há pendências de dispositivo ou supervisão.",
                tone = PontoCafeTone.SUCCESS,
            )
            PcFeedbackBanner(
                message = "O servidor está numa versão anterior a este aplicativo.",
                tone = PontoCafeTone.DANGER,
                onDismiss = {},
            )
            PcEmptyState(
                title = "Nenhum código vivo",
                supportingText = "Escolha uma pessoa acima para emitir o primeiro.",
                icon = Icons.Default.Coffee,
            )
        }
    }
}

@Preview(name = "Estados de pessoa · A55", device = PREVIEW_PHONE, showBackground = true)
@Preview(name = "Estados de pessoa · quiosque", device = PREVIEW_KIOSK, showBackground = true)
@Composable
private fun PreviewPontoStatus() {
    PontoPreviewSurface {
        Column(
            modifier = Modifier.padding(PontoCafeSpacing.md),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            StatusPill(text = "Em pausa", tone = PontoCafeTone.WARNING)
            StatusPill(text = "Código ativo", tone = PontoCafeTone.SUCCESS)
            StatusPill(text = "Disponível", tone = PontoCafeTone.NEUTRAL)
            AccessCodeChip("K3F-9QP")
            PcCompactAction(
                text = "Gerar",
                icon = Icons.Default.Coffee,
                onClick = {},
                contentDescription = "Gerar código",
            )
            InitialAvatar(name = "Ana Rodrigues", avatarSize = 56.dp)
        }
    }
}
