package com.pontocafe.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pontocafe.app.data.AccessCodeCreatedResponse
import com.pontocafe.app.data.AccessCodeItem
import com.pontocafe.app.data.Colaborador
import kotlinx.coroutines.delay

/**
 * Emissão do código de acesso ao café.
 *
 * Uma tela, usada por Admin e Supervisor: procurar a pessoa, tocar em GERAR, e
 * ditar os seis caracteres que aparecem em letras grandes. A lista abaixo mostra
 * quem já tem passe vivo e em que estado — é onde o Supervisor confere quem
 * ainda não voltou.
 */
@Composable
fun AccessCodeScreen(
    colaboradores: List<Colaborador>,
    codigosAtivos: List<AccessCodeItem>,
    codigoEmitido: AccessCodeCreatedResponse?,
    carregando: Boolean,
    erro: String?,
    onGerar: (Colaborador, String?) -> Unit,
    onCancelar: (Colaborador) -> Unit,
    onFechar: () -> Unit,
    onAtualizar: () -> Unit,
) {
    var busca by remember { mutableStateOf("") }
    var alvo by remember { mutableStateOf<Colaborador?>(null) }

    // A lista de códigos vivos envelhece sozinha (expira, alguém sai, alguém
    // volta). Sem este refresh o Supervisor ficaria a olhar para um estado
    // antigo enquanto decide se emite outro.
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000L)
            onAtualizar()
        }
    }

    val porColaborador = remember(codigosAtivos) { codigosAtivos.associateBy { it.colaboradorId } }
    val termo = busca.trim()
    val filtrados = remember(termo, colaboradores) {
        if (termo.isEmpty()) {
            colaboradores
        } else {
            colaboradores.filter {
                it.nome.contains(termo, ignoreCase = true) ||
                    it.setor.orEmpty().contains(termo, ignoreCase = true)
            }
        }
    }

    alvo?.let { colaborador ->
        IssueCodeDialog(
            colaborador = colaborador,
            carregando = carregando,
            onDismiss = { alvo = null },
            onConfirm = { motivo ->
                alvo = null
                onGerar(colaborador, motivo)
            },
        )
    }

    codigoEmitido?.let { emitido ->
        IssuedCodeDialog(codigo = emitido, onDismiss = onFechar)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = PontoCafeSpacing.md),
        verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
    ) {
        Text(
            "Códigos de café",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(top = PontoCafeSpacing.sm)
                .semantics { heading() },
        )
        Text(
            "Gere um código para quem vai tomar café. A mesma pessoa usa ele para sair e para voltar.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        erro?.let {
            PcStateBanner(
                title = "Não foi possível concluir",
                supportingText = it,
                tone = PontoCafeTone.DANGER,
            )
        }

        OutlinedTextField(
            value = busca,
            onValueChange = { busca = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Buscar pessoa") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Search,
            ),
        )

        if (filtrados.isEmpty()) {
            PcEmptyState(
                title = "Nenhuma pessoa encontrada",
                supportingText = if (colaboradores.isEmpty()) {
                    "Cadastre os colaboradores antes de gerar códigos."
                } else {
                    "Confira a escrita ou limpe a busca."
                },
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
                contentPadding = PaddingValues(bottom = PontoCafeSpacing.lg),
            ) {
                items(filtrados, key = { it.id }) { pessoa ->
                    AccessCodeRow(
                        colaborador = pessoa,
                        codigo = porColaborador[pessoa.id],
                        carregando = carregando,
                        onGerar = { alvo = pessoa },
                        onCancelar = { onCancelar(pessoa) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AccessCodeRow(
    colaborador: Colaborador,
    codigo: AccessCodeItem?,
    carregando: Boolean,
    onGerar: () -> Unit,
    onCancelar: () -> Unit,
) {
    val emPausa = codigo?.estado == "EM_PAUSA"
    val aguardando = codigo?.estado == "AGUARDANDO_SAIDA"

    PcSectionSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InitialAvatar(name = colaborador.nome, avatarSize = 40.dp)
                Spacer(Modifier.size(PontoCafeSpacing.xs))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        colaborador.nome,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    val detalhe = listOfNotNull(
                        colaborador.setor?.takeIf { it.isNotBlank() },
                        colaborador.turno?.takeIf { it.isNotBlank() }?.let { "Turno $it" },
                    ).joinToString(" · ")
                    if (detalhe.isNotBlank()) {
                        Text(
                            detalhe,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                StatusPill(
                    text = when {
                        emPausa -> "Em pausa"
                        aguardando -> "Aguardando saída"
                        codigo != null -> "Código expirado"
                        else -> "Sem código"
                    },
                    tone = when {
                        emPausa -> PontoCafeTone.INFO
                        aguardando -> PontoCafeTone.SUCCESS
                        codigo != null -> PontoCafeTone.WARNING
                        else -> PontoCafeTone.NEUTRAL
                    },
                )
            }

            if (codigo != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
                ) {
                    CodeChip(codigo.codigoFormatado)
                    Text(
                        when {
                            emPausa -> "Válido para o retorno"
                            aguardando -> "Expira em ${codigo.expiraEmSegundos / 60} min"
                            else -> "Já não vale para sair"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                PcPrimaryButton(
                    text = if (codigo == null || !aguardando) "Gerar código" else "Gerar outro",
                    onClick = onGerar,
                    // Emitir enquanto a pessoa está fora criaria um segundo código
                    // vivo e deixaria em aberto qual deles fecha a pausa. O
                    // servidor recusa; a interface nem chega a oferecer.
                    enabled = !carregando && !emPausa,
                    icon = Icons.Default.Coffee,
                    modifier = Modifier.weight(1f),
                )
                if (aguardando) {
                    PcSecondaryButton(
                        text = "Cancelar",
                        onClick = onCancelar,
                        enabled = !carregando,
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeChip(codigoFormatado: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Text(
            codigoFormatado,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = PontoCafeSpacing.xs, vertical = PontoCafeSpacing.xxs),
        )
    }
}

@Composable
private fun IssueCodeDialog(
    colaborador: Colaborador,
    carregando: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var motivo by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!carregando) onDismiss() },
        title = { Text("Gerar código para ${colaborador.nome}") },
        text = {
            PcDialogBody {
                Text(
                    "O código vale uma vez na saída e uma vez no retorno, e só serve para esta pessoa.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = motivo,
                    onValueChange = { motivo = it.take(300) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Motivo (opcional)") },
                    supportingText = { Text("Preencha só quando houver algo a registrar.") },
                    singleLine = false,
                    minLines = 2,
                    enabled = !carregando,
                )
            }
        },
        confirmButton = {
            PcPrimaryButton(
                text = "Gerar",
                onClick = { onConfirm(motivo.trim().ifBlank { null }) },
                enabled = !carregando,
                loading = carregando,
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !carregando) { Text("Cancelar") }
        },
    )
}

/**
 * O código em letras grandes.
 *
 * Este diálogo existe porque o Supervisor vai LER o código em voz alta para
 * alguém do outro lado do balcão. Um chip discreto na lista não serve para isso.
 */
@Composable
private fun IssuedCodeDialog(
    codigo: AccessCodeCreatedResponse,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Código de ${codigo.colaboradorNome}") },
        text = {
            PcDialogBody {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(16.dp),
                        )
                        .border(
                            2.dp,
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(16.dp),
                        )
                        .semantics {
                            contentDescription =
                                "Código ${codigo.codigo.toCharArray().joinToString(", ")}"
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        codigo.codigoFormatado,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 44.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Text(
                    "Vale por ${codigo.expiraEmSegundos / 60} minutos para a SAÍDA. Depois de sair, " +
                        "o mesmo código continua válido para o RETORNO, sem prazo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (codigo.carenciaSegundos > 0) {
                    Text(
                        "A contagem do café começa ${codigo.carenciaSegundos / 60} minuto(s) depois do registro da saída.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { clipboard.setText(AnnotatedString(codigo.codigo)) }
                        .padding(vertical = PontoCafeSpacing.xxs),
                ) {
                    IconButton(onClick = { clipboard.setText(AnnotatedString(codigo.codigo)) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copiar código")
                    }
                    Text("Copiar código", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            PcPrimaryButton(text = "Pronto", onClick = onDismiss)
        },
    )
}
