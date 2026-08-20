package com.pontocafe.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pontocafe.app.data.Colaborador

internal enum class PeopleFaceFilter { ALL, PENDING }

@Composable
internal fun PeopleCompactSummary(
    total: Int,
    pending: Int,
    accessCount: Int? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "$total colaboradores",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "$pending ${if (pending == 1) "rosto pendente" else "rostos pendentes"}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (pending > 0) LocalPontoCafeSemanticColors.current.warning else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (accessCount != null) {
                Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "$accessCount acessos",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun PeopleSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    accessMode: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotBlank()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Limpar busca")
                }
            }
        },
        label = { Text(if (accessMode) "Buscar acesso" else "Buscar colaborador") },
        placeholder = { Text(if (accessMode) "Nome, e-mail ou perfil" else "Nome, setor ou turno") },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
    )
}

@Composable
internal fun PeopleFaceFilterRow(
    selected: PeopleFaceFilter,
    total: Int,
    pending: Int,
    activeExtraFilters: Int,
    onSelected: (PeopleFaceFilter) -> Unit,
    onOpenFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
        contentPadding = PaddingValues(end = PontoCafeSpacing.xs),
    ) {
        item {
            FilterChip(
                selected = selected == PeopleFaceFilter.ALL,
                onClick = { onSelected(PeopleFaceFilter.ALL) },
                label = { Text("Todos $total") },
            )
        }
        item {
            FilterChip(
                selected = selected == PeopleFaceFilter.PENDING,
                onClick = { onSelected(PeopleFaceFilter.PENDING) },
                label = { Text("Pendentes $pending") },
            )
        }
        item {
            FilterChip(
                selected = activeExtraFilters > 0,
                onClick = onOpenFilters,
                leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(17.dp)) },
                label = {
                    Text(
                        if (activeExtraFilters > 0) "Filtros $activeExtraFilters" else "Filtros",
                    )
                },
            )
        }
    }
}

@Composable
internal fun PeopleSectionSwitch(
    collaboratorSelected: Boolean,
    collaboratorCount: Int,
    accessCount: Int,
    onCollaborators: () -> Unit,
    onAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
    ) {
        FilterChip(
            selected = collaboratorSelected,
            onClick = onCollaborators,
            label = { Text("Colaboradores $collaboratorCount") },
            modifier = Modifier.weight(1f),
        )
        FilterChip(
            selected = !collaboratorSelected,
            onClick = onAccess,
            label = { Text("Acessos $accessCount") },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun PeoplePersonCard(
    person: Colaborador,
    selected: Boolean,
    selectionMode: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
    onSelected: (Boolean) -> Unit,
    onBiometric: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val semantic = LocalPontoCafeSemanticColors.current
    val borderColor = when {
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = .55f)
        !person.rostoCadastrado -> semantic.warning.copy(alpha = .28f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = .42f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                if (selectionMode) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = onSelected,
                    )
                }

                CollaboratorAvatar(
                    name = person.nome,
                    avatarUrl = person.avatarUrl,
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = person.nome,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOfNotNull(person.setor, person.turno)
                            .filter { it.isNotBlank() }
                            .joinToString(" · ")
                            .ifBlank { "Sem setor/turno" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    StatusPill(
                        text = if (person.rostoCadastrado) "Biometria pronta" else "Rosto pendente",
                        tone = if (person.rostoCadastrado) PontoCafeTone.SUCCESS else PontoCafeTone.WARNING,
                    )
                }

                if (!selectionMode) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "Abrir ${person.nome}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!selectionMode && !person.rostoCadastrado) {
                Button(
                    onClick = onBiometric,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(Icons.Default.Face, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Cadastrar rosto", modifier = Modifier.padding(start = 7.dp))
                }
            }
        }
    }
}

@Composable
private fun PersonActionContent(
    person: Colaborador,
    loading: Boolean,
    onBiometric: () -> Unit,
    onAvatar: () -> Unit,
    onHistory: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onDeleteAvatar: (() -> Unit)?,
    onDeleteFace: (() -> Unit)?,
    onDeleteCollaborator: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var showMore by remember(person.id) { mutableStateOf(false) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            CollaboratorAvatar(person.nome, person.avatarUrl)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    person.nome,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(person.setor, person.turno)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                        .ifBlank { "Sem setor/turno" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        StatusPill(
            text = if (person.rostoCadastrado) "Biometria pronta" else "Rosto pendente",
            tone = if (person.rostoCadastrado) PontoCafeTone.SUCCESS else PontoCafeTone.WARNING,
        )

        Button(
            onClick = onBiometric,
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading,
        ) {
            Icon(Icons.Default.Face, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                if (person.rostoCadastrado) "Atualizar biometria" else "Cadastrar biometria",
                modifier = Modifier.padding(start = 7.dp),
            )
        }

        HorizontalDivider()

        onHistory?.let { callback ->
            OutlinedButton(
                onClick = callback,
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading,
            ) {
                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Histórico", modifier = Modifier.padding(start = 7.dp))
            }
        }

        onEdit?.let { callback ->
            OutlinedButton(
                onClick = callback,
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading,
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Editar colaborador", modifier = Modifier.padding(start = 7.dp))
            }
        }

        OutlinedButton(
            onClick = onAvatar,
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading,
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                if (person.avatarUrl.isNullOrBlank()) "Definir avatar" else "Alterar avatar",
                modifier = Modifier.padding(start = 7.dp),
            )
        }

        if (onDeleteAvatar != null || onDeleteFace != null || onDeleteCollaborator != null) {
            TextButton(
                onClick = { showMore = !showMore },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.MoreHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(if (showMore) "Ocultar opções" else "Mais opções", modifier = Modifier.padding(start = 7.dp))
            }
        }

        AnimatedVisibility(showMore) {
            Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                if (!person.avatarUrl.isNullOrBlank() && onDeleteAvatar != null) {
                    OutlinedButton(
                        onClick = onDeleteAvatar,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading,
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Remover avatar", modifier = Modifier.padding(start = 7.dp))
                    }
                }

                if (person.rostoCadastrado && onDeleteFace != null) {
                    OutlinedButton(
                        onClick = onDeleteFace,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = .40f)),
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Excluir biometria", modifier = Modifier.padding(start = 7.dp))
                    }
                }

                if (onDeleteCollaborator != null) {
                    OutlinedButton(
                        onClick = onDeleteCollaborator,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = .62f)),
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Excluir colaborador", modifier = Modifier.padding(start = 7.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun PersonActionBottomSheet(
    person: Colaborador,
    loading: Boolean,
    onDismiss: () -> Unit,
    onBiometric: () -> Unit,
    onAvatar: () -> Unit,
    onHistory: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDeleteAvatar: (() -> Unit)? = null,
    onDeleteFace: (() -> Unit)? = null,
    onDeleteCollaborator: (() -> Unit)? = null,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        PersonActionContent(
            person = person,
            loading = loading,
            onBiometric = onBiometric,
            onAvatar = onAvatar,
            onHistory = onHistory,
            onEdit = onEdit,
            onDeleteAvatar = onDeleteAvatar,
            onDeleteFace = onDeleteFace,
            onDeleteCollaborator = onDeleteCollaborator,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        )
    }
}

@Composable
internal fun PersonDetailPanel(
    person: Colaborador?,
    loading: Boolean,
    onBiometric: (Colaborador) -> Unit,
    onAvatar: (Colaborador) -> Unit,
    onHistory: ((Colaborador) -> Unit)? = null,
    onEdit: ((Colaborador) -> Unit)? = null,
    onDeleteAvatar: ((Colaborador) -> Unit)? = null,
    onDeleteFace: ((Colaborador) -> Unit)? = null,
    onDeleteCollaborator: ((Colaborador) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        if (person == null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Selecione uma pessoa",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Os dados e ações aparecem aqui sem tirar você da lista.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            PersonActionContent(
                person = person,
                loading = loading,
                onBiometric = { onBiometric(person) },
                onAvatar = { onAvatar(person) },
                onHistory = onHistory?.let { callback -> { callback(person) } },
                onEdit = onEdit?.let { callback -> { callback(person) } },
                onDeleteAvatar = onDeleteAvatar?.let { callback -> { callback(person) } },
                onDeleteFace = onDeleteFace?.let { callback -> { callback(person) } },
                onDeleteCollaborator = onDeleteCollaborator?.let { callback -> { callback(person) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .padding(20.dp),
            )
        }
    }
}

@Composable
internal fun PeopleFilterSheet(
    sectors: List<String>,
    shifts: List<String>,
    currentSector: String?,
    currentShift: String?,
    onDismiss: () -> Unit,
    onApply: (String?, String?) -> Unit,
) {
    var sector by remember(currentSector) { mutableStateOf(currentSector) }
    var shift by remember(currentShift) { mutableStateOf(currentShift) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
        ) {
            Text("Filtrar pessoas", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

            Text("Setor", style = MaterialTheme.typography.titleSmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                item {
                    FilterChip(
                        selected = sector == null,
                        onClick = { sector = null },
                        label = { Text("Todos") },
                    )
                }
                items(sectors, key = { "sector-$it" }) { value ->
                    FilterChip(
                        selected = sector == value,
                        onClick = { sector = value },
                        label = { Text(value) },
                    )
                }
            }

            Text("Turno", style = MaterialTheme.typography.titleSmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                item {
                    FilterChip(
                        selected = shift == null,
                        onClick = { shift = null },
                        label = { Text("Todos") },
                    )
                }
                items(shifts, key = { "shift-$it" }) { value ->
                    FilterChip(
                        selected = shift == value,
                        onClick = { shift = value },
                        label = { Text(value) },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                OutlinedButton(
                    onClick = {
                        sector = null
                        shift = null
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Limpar")
                }
                Button(
                    onClick = { onApply(sector, shift) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Aplicar")
                }
            }
        }
    }
}
