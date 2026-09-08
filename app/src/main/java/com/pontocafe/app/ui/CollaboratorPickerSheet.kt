package com.pontocafe.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pontocafe.app.data.Colaborador

/**
 * Escolha de pessoa por seletor, e não por lista aberta.
 *
 * Despejar noventa e seis nomes na tela obrigava a rolar antes de qualquer
 * coisa e enterrava o resto do ecrã. Aqui a tela mostra um campo fechado; o
 * toque abre uma folha com a busca já em foco e a lista inteira abaixo dela.
 * Quem sabe o próprio nome digita três letras e acabou; quem não sabe, rola.
 *
 * [selecionado] é o nome que fica escrito no campo fechado depois da escolha —
 * nulo mostra [placeholder].
 */
@Composable
internal fun PcCollaboratorPickerField(
    selecionado: Colaborador?,
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    grande: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (grande) 68.dp else 56.dp)
            .semantics {
                contentDescription = selecionado?.let { "Pessoa escolhida: ${it.nome}. Tocar para trocar" }
                    ?: placeholder
            },
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            if (selecionado != null) {
                MaterialTheme.colorScheme.primary.copy(alpha = .5f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = PontoCafeSpacing.sm, vertical = PontoCafeSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            if (selecionado != null) {
                InitialAvatar(name = selecionado.nome, avatarSize = if (grande) 44.dp else 36.dp)
            } else {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(if (grande) 12.dp else 9.dp)
                            .size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    selecionado?.nome ?: placeholder,
                    style = if (grande) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    fontWeight = if (selecionado != null) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selecionado != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (selecionado != null) {
                    Text(
                        colaboradorDetalhe(selecionado),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A folha que o seletor abre: busca em foco, lista abaixo.
 *
 * A busca vive aqui dentro e não na tela por trás — fora da folha ela ficaria
 * a filtrar uma lista que ninguém está a ver.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PcCollaboratorPickerSheet(
    pessoas: List<Colaborador>,
    titulo: String,
    onDismiss: () -> Unit,
    onEscolher: (Colaborador) -> Unit,
    vazioTitulo: String,
    vazioTexto: String,
    modifier: Modifier = Modifier,
    onInteracao: (String) -> Unit = {},
) {
    var busca by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Quem abre o seletor quase sempre vem digitar. Pedir mais um toque no campo
    // antes de poder escrever é o atrito que faz a pessoa rolar a lista inteira.
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    val termo = busca.trim()
    val filtradas = remember(termo, pessoas) {
        if (termo.isEmpty()) {
            pessoas
        } else {
            pessoas.filter { pessoa ->
                pessoa.nome.contains(termo, ignoreCase = true) ||
                    pessoa.setor.orEmpty().contains(termo, ignoreCase = true) ||
                    pessoa.turno.orEmpty().contains(termo, ignoreCase = true)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = PontoCafeSpacing.md),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    titulo,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Fechar")
                }
            }

            OutlinedTextField(
                value = busca,
                onValueChange = {
                    busca = it
                    // Cada tecla é uma interação real. No quiosque isto reinicia
                    // o repouso de dois minutos -- procurar o próprio nome numa
                    // lista longa demora, e a tela de descanso não pode subir
                    // por cima de quem está a escrever.
                    onInteracao(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                placeholder = { Text("Buscar por nome ou setor") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (busca.isNotBlank()) {
                        IconButton(onClick = { busca = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Limpar busca")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Search,
                ),
            )

            Text(
                if (termo.isEmpty()) {
                    "${filtradas.size} disponíveis"
                } else {
                    "${filtradas.size} resultado(s)"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()

            if (filtradas.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    PcEmptyState(
                        title = if (pessoas.isEmpty()) vazioTitulo else "Nenhum nome corresponde à busca",
                        supportingText = if (pessoas.isEmpty()) {
                            vazioTexto
                        } else {
                            "Confira a escrita ou apague a busca para ver a lista inteira."
                        },
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xxs),
                    contentPadding = PaddingValues(bottom = PontoCafeSpacing.lg),
                ) {
                    items(filtradas, key = { it.id }) { pessoa ->
                        PcCollaboratorPickerRow(pessoa = pessoa, onClick = { onEscolher(pessoa) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PcCollaboratorPickerRow(pessoa: Colaborador, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics { contentDescription = "Selecionar ${pessoa.nome}" },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = PontoCafeSpacing.sm, vertical = PontoCafeSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            InitialAvatar(name = pessoa.nome, avatarSize = 42.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    pessoa.nome,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    colaboradorDetalhe(pessoa),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
