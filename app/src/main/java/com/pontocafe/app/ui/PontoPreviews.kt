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
import androidx.compose.foundation.layout.Row
import com.pontocafe.app.data.Colaborador
import com.pontocafe.app.data.PausaSupervisor
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

// --- Dados de exemplo -------------------------------------------------------
//
// Nomes longos de propósito. Um preview com "Ana Silva" passa em qualquer
// largura e não prova nada; o que corta o layout no A55 é o nome composto de
// quatro partes, que é o que existe na folha de pagamento real.

private fun pessoaExemplo(
    nome: String = "Maria Aparecida de Souza Nascimento",
    setor: String? = "Produção · Linha 3",
    turno: String? = "Manhã",
    emPausa: Boolean = false,
    codigoAtivo: Boolean = false,
) = Colaborador(
    id = nome,
    nome = nome,
    setor = setor,
    turno = turno,
    emPausa = emPausa,
    codigoAtivo = codigoAtivo,
)

private fun pausaExemplo(
    nome: String = "Maria Aparecida de Souza Nascimento",
    decorridoSegundos: Int = 300,
) = PausaSupervisor(
    id = nome,
    periodo = "MANHA",
    inicioLocal = "08:12",
    limiteSegundos = 900,
    carenciaSegundos = 60,
    foraHorario = false,
    tempoSegundos = decorridoSegundos,
    colaboradorId = nome,
    nome = nome,
    setor = "Produção · Linha 3",
    clienteAtualizadoEmMillis = System.currentTimeMillis(),
)

@Preview(name = "Lista de pessoas · A55", device = PREVIEW_PHONE, showBackground = true)
@Preview(name = "Lista de pessoas · quiosque", device = PREVIEW_KIOSK, showBackground = true)
@Composable
private fun PreviewPeopleList() {
    PontoPreviewSurface {
        Column(
            modifier = Modifier.padding(PontoCafeSpacing.md),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
        ) {
            // Os três estados que a linha assume. O primeiro é o único que
            // mostra o botão "Gerar": os outros dois não podem receber código.
            PeoplePersonCard(
                person = pessoaExemplo(),
                selected = false,
                selectionMode = false,
                loading = false,
                onClick = {},
                onSelected = {},
                onGerarCodigo = {},
            )
            PeoplePersonCard(
                person = pessoaExemplo(nome = "João Carlos Ferreira", emPausa = true),
                selected = false,
                selectionMode = false,
                loading = false,
                onClick = {},
                onSelected = {},
                onGerarCodigo = {},
            )
            PeoplePersonCard(
                person = pessoaExemplo(nome = "Ana Beatriz Rodrigues Lima", codigoAtivo = true),
                selected = true,
                selectionMode = false,
                loading = false,
                onClick = {},
                onSelected = {},
                onGerarCodigo = {},
            )
            PeoplePersonCard(
                person = pessoaExemplo(nome = "Pessoa Sem Setor Definido", setor = null, turno = null),
                selected = false,
                selectionMode = true,
                loading = false,
                onClick = {},
                onSelected = {},
                onGerarCodigo = {},
            )
        }
    }
}

@Preview(name = "Seletor de pessoa · A55", device = PREVIEW_PHONE, showBackground = true)
@Preview(name = "Seletor de pessoa · quiosque", device = PREVIEW_KIOSK, showBackground = true)
@Composable
private fun PreviewCollaboratorPicker() {
    PontoPreviewSurface {
        Column(
            modifier = Modifier.padding(PontoCafeSpacing.md),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            PcCollaboratorPickerField(
                selecionado = null,
                placeholder = "Selecione seu nome",
                onClick = {},
                grande = true,
            )
            PcCollaboratorPickerField(
                selecionado = pessoaExemplo(),
                placeholder = "Selecione seu nome",
                onClick = {},
                grande = true,
            )
            PcCollaboratorPickerField(
                selecionado = null,
                placeholder = "Escolher pessoa e gerar código",
                onClick = {},
                enabled = false,
            )
        }
    }
}

@Preview(name = "Pausas ao vivo · A55", device = PREVIEW_PHONE, showBackground = true)
@Preview(name = "Pausas ao vivo · quiosque", device = PREVIEW_KIOSK, showBackground = true)
@Composable
private fun PreviewOperationalPauses() {
    PontoPreviewSurface {
        OperationalClockProvider {
            Column(
                modifier = Modifier.padding(PontoCafeSpacing.md),
                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
            ) {
                // Dentro do limite, perto dele, e estourado: as três cores do
                // arco e do cartão, lado a lado.
                OperationalPauseCompactCard(
                    item = OperationalPauseItem(pausaExemplo(decorridoSegundos = 120)),
                    onClick = {},
                )
                OperationalPauseCompactCard(
                    item = OperationalPauseItem(
                        pausaExemplo(nome = "João Carlos Ferreira", decorridoSegundos = 920),
                    ),
                    onClick = {},
                )
                OperationalPauseCompactCard(
                    item = OperationalPauseItem(
                        pausaExemplo(nome = "Ana Beatriz Rodrigues Lima", decorridoSegundos = 1400),
                    ),
                    onClick = {},
                    onCloseManually = {},
                )
            }
        }
    }
}

@Preview(name = "Força de senha · A55", device = PREVIEW_PHONE, showBackground = true)
@Preview(name = "Força de senha · quiosque", device = PREVIEW_KIOSK, showBackground = true)
@Composable
private fun PreviewPasswordStrength() {
    PontoPreviewSurface {
        Column(
            modifier = Modifier.padding(PontoCafeSpacing.md),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.lg),
        ) {
            // Os três degraus da barra: fraca, média e forte.
            PontoPasswordStrength(
                listOf(
                    PontoPasswordRule("Pelo menos 10 caracteres", true),
                    PontoPasswordRule("Uma letra maiúscula", false),
                    PontoPasswordRule("Um número", false),
                    PontoPasswordRule("Um símbolo", false),
                ),
            )
            PontoPasswordStrength(
                listOf(
                    PontoPasswordRule("Pelo menos 10 caracteres", true),
                    PontoPasswordRule("Uma letra maiúscula", true),
                    PontoPasswordRule("Um número", true),
                    PontoPasswordRule("Um símbolo", false),
                ),
            )
            PontoPasswordStrength(
                listOf(
                    PontoPasswordRule("Pelo menos 10 caracteres", true),
                    PontoPasswordRule("Uma letra maiúscula", true),
                    PontoPasswordRule("Um número", true),
                    PontoPasswordRule("Um símbolo", true),
                ),
            )
        }
    }
}

@Preview(name = "Métricas · A55", device = PREVIEW_PHONE, showBackground = true)
@Preview(name = "Métricas · quiosque", device = PREVIEW_KIOSK, showBackground = true)
@Composable
private fun PreviewMetrics() {
    PontoPreviewSurface {
        Column(
            modifier = Modifier.padding(PontoCafeSpacing.md),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            PcHeroCard(
                title = "Sistema pronto para operar",
                supportingText = "Banco 287 ms · 4 dispositivo(s) · 1 desatualizado(s)",
                icon = Icons.Default.Coffee,
                tone = PontoCafeTone.SUCCESS,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                PcMetricTile(
                    value = "96",
                    label = "Colaboradores",
                    icon = Icons.Default.Coffee,
                    modifier = Modifier.weight(1f),
                )
                PcMetricTile(
                    value = "3",
                    label = "Acima do limite",
                    icon = Icons.Default.Coffee,
                    attention = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
