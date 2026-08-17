package com.pontocafe.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.GroupWork
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pontocafe.app.AdminReliabilityViewModel
import com.pontocafe.app.AdminViewModel
import com.pontocafe.app.data.Colaborador
import com.pontocafe.app.domain.CsvCollaboratorParser
import com.pontocafe.app.domain.CsvImportPreview

private enum class PeopleViewFilterV4 {
    TEAM,
    PENDING_FACE,
    ACCESS,
}

@Composable
fun AdminPeopleScreenV4(
    viewModel: AdminViewModel,
    reliabilityViewModel: AdminReliabilityViewModel,
) {
    val context = LocalContext.current
    val state = viewModel.state
    val reliabilityState = reliabilityViewModel.state

    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(PeopleViewFilterV4.TEAM) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Colaborador?>(null) }
    var deletingBiometric by remember { mutableStateOf<Colaborador?>(null) }
    var deletingCollaborator by remember { mutableStateOf<Colaborador?>(null) }
    var importPreview by remember { mutableStateOf<CsvImportPreview?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBulkDialog by remember { mutableStateOf(false) }

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: error("Não foi possível abrir o arquivo.")
            }.onSuccess { text ->
                importPreview = CsvCollaboratorParser.parse(text)
            }.onFailure { error ->
                importPreview = CsvImportPreview(
                    valid = emptyList(),
                    errors = listOf(error.message ?: "Não foi possível ler o CSV."),
                )
            }
        }
    }

    editing?.let { collaborator ->
        EditCollaboratorDialogV4(
            collaborator = collaborator,
            loading = state.carregando,
            onDismiss = { editing = null },
            onSave = { name, sector, shift ->
                viewModel.editarColaborador(collaborator, name, sector, shift)
                editing = null
            },
        )
    }

    deletingBiometric?.let { collaborator ->
        DeleteBiometricDialogV4(
            collaborator = collaborator,
            loading = reliabilityState.loading,
            onDismiss = { if (!reliabilityState.loading) deletingBiometric = null },
            onConfirm = {
                reliabilityViewModel.deleteBiometric(collaborator.id)
                deletingBiometric = null
                expandedId = null
            },
        )
    }

    deletingCollaborator?.let { collaborator ->
        DeleteCollaboratorDialogV4(
            collaborator = collaborator,
            loading = reliabilityState.loading,
            onDismiss = { if (!reliabilityState.loading) deletingCollaborator = null },
            onConfirm = {
                reliabilityViewModel.deleteCollaborator(collaborator.id)
                deletingCollaborator = null
                expandedId = null
            },
        )
    }

    importPreview?.let { preview ->
        ImportPreviewDialogV4(
            preview = preview,
            loading = reliabilityState.loading,
            onDismiss = { importPreview = null },
            onConfirm = {
                reliabilityViewModel.importCollaborators(preview.valid)
                importPreview = null
            },
        )
    }

    if (showBulkDialog) {
        BulkEditDialogV4(
            selectedCount = selectedIds.size,
            loading = reliabilityState.loading,
            onDismiss = { showBulkDialog = false },
            onApply = { sector, shift, deactivate ->
                reliabilityViewModel.updateBulk(
                    ids = selectedIds.toList(),
                    sector = sector,
                    shift = shift,
                    active = if (deactivate) false else null,
                )
                selectedIds = emptySet()
                selectionMode = false
                showBulkDialog = false
            },
        )
    }

    val allCollaborators = state.colaboradores.sortedBy { it.nome.lowercase() }
    val pendingFaces = allCollaborators.count { !it.rostoCadastrado }
    val readyFaces = allCollaborators.size - pendingFaces
    val query = search.trim()

    val collaborators = allCollaborators
        .asSequence()
        .filter { collaborator ->
            query.isBlank() ||
                collaborator.nome.contains(query, ignoreCase = true) ||
                collaborator.setor.orEmpty().contains(query, ignoreCase = true) ||
                collaborator.turno.orEmpty().contains(query, ignoreCase = true)
        }
        .filter { collaborator ->
            filter != PeopleViewFilterV4.PENDING_FACE || !collaborator.rostoCadastrado
        }
        .sortedWith(compareBy<Colaborador>({ it.rostoCadastrado }, { it.nome.lowercase() }))
        .toList()

    val accounts = state.usuarios
        .asSequence()
        .filter { user ->
            query.isBlank() ||
                user.nome.contains(query, ignoreCase = true) ||
                user.email.contains(query, ignoreCase = true) ||
                user.perfil.contains(query, ignoreCase = true)
        }
        .sortedBy { it.nome.lowercase() }
        .toList()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxWidth < 390.dp
        val pagePadding = when {
            maxWidth < 360.dp -> 12.dp
            maxWidth < 600.dp -> 16.dp
            else -> 20.dp
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            contentPadding = PaddingValues(
                start = pagePadding,
                end = pagePadding,
                top = PontoCafeSpacing.md,
                bottom = if (selectionMode) 128.dp else 100.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
        ) {
            item("header") {
                PontoCafeScreenHeader(
                    title = "Pessoas",
                    eyebrow = "Equipe, biometria e acessos",
                )
            }

            item("summary") {
                PeopleSummaryV4(
                    collaborators = allCollaborators.size,
                    readyFaces = readyFaces,
                    pendingFaces = pendingFaces,
                    accessAccounts = state.usuarios.size,
                )
            }

            item("feedback") {
                Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                    AdminFeedback(viewModel)
                    ReliabilityFeedback(reliabilityViewModel)
                }
            }

            item("primary-actions") {
                if (compact) {
                    Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                        Button(
                            onClick = viewModel::abrirNovoColaborador,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Novo colaborador", modifier = Modifier.padding(start = 7.dp))
                        }
                        OutlinedButton(
                            onClick = viewModel::abrirNovaConta,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Icon(Icons.Default.Badge, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Novo acesso", modifier = Modifier.padding(start = 7.dp))
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
                    ) {
                        Button(
                            onClick = viewModel::abrirNovoColaborador,
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Colaborador", modifier = Modifier.padding(start = 7.dp))
                        }
                        OutlinedButton(
                            onClick = viewModel::abrirNovaConta,
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Icon(Icons.Default.Badge, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Acesso", modifier = Modifier.padding(start = 7.dp))
                        }
                    }
                }
            }

            item("tools") {
                if (compact) {
                    Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                        OutlinedButton(
                            onClick = { fileLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain")) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Importar CSV", modifier = Modifier.padding(start = 7.dp))
                        }
                        OutlinedButton(
                            onClick = {
                                selectionMode = !selectionMode
                                filter = PeopleViewFilterV4.TEAM
                                expandedId = null
                                if (!selectionMode) selectedIds = emptySet()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Icon(Icons.Default.GroupWork, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(
                                if (selectionMode) "Cancelar seleção" else "Selecionar pessoas",
                                modifier = Modifier.padding(start = 7.dp),
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
                    ) {
                        OutlinedButton(
                            onClick = { fileLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain")) },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Importar", modifier = Modifier.padding(start = 7.dp))
                        }
                        OutlinedButton(
                            onClick = {
                                selectionMode = !selectionMode
                                filter = PeopleViewFilterV4.TEAM
                                expandedId = null
                                if (!selectionMode) selectedIds = emptySet()
                            },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Icon(Icons.Default.GroupWork, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(if (selectionMode) "Cancelar" else "Selecionar", modifier = Modifier.padding(start = 7.dp))
                        }
                    }
                }
            }

            item("search") {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (search.isNotBlank()) {
                            IconButton(onClick = { search = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Limpar busca")
                            }
                        }
                    },
                    label = {
                        Text(if (filter == PeopleViewFilterV4.ACCESS) "Buscar acesso" else "Buscar colaborador")
                    },
                    placeholder = {
                        Text(if (filter == PeopleViewFilterV4.ACCESS) "Nome, e-mail ou perfil" else "Nome, setor ou turno")
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                )
            }

            item("filters") {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
                    contentPadding = PaddingValues(end = PontoCafeSpacing.xs),
                ) {
                    items(PeopleViewFilterV4.entries, key = { it.name }) { item ->
                        val label = when (item) {
                            PeopleViewFilterV4.TEAM -> "Equipe ${allCollaborators.size}"
                            PeopleViewFilterV4.PENDING_FACE -> "Pendentes $pendingFaces"
                            PeopleViewFilterV4.ACCESS -> "Acessos ${state.usuarios.size}"
                        }
                        FilterChip(
                            selected = filter == item,
                            onClick = {
                                if (!selectionMode || item != PeopleViewFilterV4.ACCESS) {
                                    filter = item
                                    expandedId = null
                                }
                            },
                            enabled = !selectionMode || item != PeopleViewFilterV4.ACCESS,
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
            }

            if (filter != PeopleViewFilterV4.ACCESS) {
                item("results-heading") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (filter == PeopleViewFilterV4.PENDING_FACE) "Biometrias pendentes" else "Equipe",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                if (selectionMode) {
                                    "Toque nas pessoas que deseja alterar em lote."
                                } else {
                                    "Toque em uma pessoa para abrir as ações. O nome completo permanece visível."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                            Text(
                                collaborators.size.toString(),
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }

                if (collaborators.isEmpty()) {
                    item("empty") {
                        PcEmptyState(
                            title = if (query.isBlank()) "Nenhuma pessoa nesta visão" else "Nenhum resultado",
                            supportingText = if (query.isBlank()) {
                                "Não há colaboradores que correspondam ao filtro selecionado."
                            } else {
                                "Tente outro nome, setor ou turno."
                            },
                            icon = Icons.Default.People,
                        )
                    }
                } else {
                    items(collaborators, key = { "person-v4-${it.id}" }) { collaborator ->
                        val expanded = expandedId == collaborator.id
                        CollaboratorCardV4(
                            collaborator = collaborator,
                            loading = state.carregando || reliabilityState.loading,
                            selectionMode = selectionMode,
                            selected = collaborator.id in selectedIds,
                            expanded = expanded,
                            onClick = {
                                if (selectionMode) {
                                    selectedIds = if (collaborator.id in selectedIds) {
                                        selectedIds - collaborator.id
                                    } else {
                                        selectedIds + collaborator.id
                                    }
                                } else {
                                    expandedId = if (expanded) null else collaborator.id
                                }
                            },
                            onSelected = { selected ->
                                selectedIds = if (selected) selectedIds + collaborator.id else selectedIds - collaborator.id
                            },
                            onHistory = { reliabilityViewModel.openHistory(collaborator.id) },
                            onEdit = { editing = collaborator },
                            onBiometric = { viewModel.cadastrarOuAtualizarRosto(collaborator) },
                            onDeleteBiometric = { deletingBiometric = collaborator },
                            onDeleteCollaborator = { deletingCollaborator = collaborator },
                        )
                    }
                }
            } else {
                item("access-heading") {
                    SectionTitle(
                        title = "Contas de acesso",
                        subtitle = "Administrador e Supervisor com acesso às áreas protegidas.",
                    )
                }

                if (accounts.isEmpty()) {
                    item("empty-access") {
                        PcEmptyState(
                            title = "Nenhum acesso encontrado",
                            supportingText = "Tente outro nome, e-mail ou perfil.",
                            icon = Icons.Default.Badge,
                        )
                    }
                } else {
                    items(accounts, key = { "access-v4-${it.id}" }) { user ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { viewModel.selecionarUsuario(user) },
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        ) {
                            AccountSummaryRow(
                                name = user.nome,
                                email = user.email,
                                profile = user.perfil,
                                active = user.ativo,
                                modifier = Modifier.padding(PontoCafeSpacing.md),
                            )
                        }
                    }
                }
            }
        }

        if (selectionMode) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = pagePadding, vertical = PontoCafeSpacing.sm),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${selectedIds.size} selecionado(s)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Alterar setor, turno ou status",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = { showBulkDialog = true },
                        enabled = selectedIds.isNotEmpty(),
                    ) {
                        Text("Alterar")
                    }
                }
            }
        }
    }
}

@Composable
private fun PeopleSummaryV4(
    collaborators: Int,
    readyFaces: Int,
    pendingFaces: Int,
    accessAccounts: Int,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val stacked = maxWidth < 520.dp
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                Row(horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                    PcMetricTile(
                        value = collaborators.toString(),
                        label = "Equipe",
                        icon = Icons.Default.People,
                        modifier = Modifier.weight(1f),
                    )
                    PcMetricTile(
                        value = readyFaces.toString(),
                        label = "Com rosto",
                        icon = Icons.Default.CheckCircle,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                    PcMetricTile(
                        value = pendingFaces.toString(),
                        label = "Pendentes",
                        icon = if (pendingFaces > 0) Icons.Default.Warning else Icons.Default.CheckCircle,
                        modifier = Modifier.weight(1f),
                        attention = pendingFaces > 0,
                    )
                    PcMetricTile(
                        value = accessAccounts.toString(),
                        label = "Acessos",
                        icon = Icons.Default.Badge,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                PcMetricTile(collaborators.toString(), "Equipe", Icons.Default.People, Modifier.weight(1f))
                PcMetricTile(readyFaces.toString(), "Com rosto", Icons.Default.CheckCircle, Modifier.weight(1f))
                PcMetricTile(
                    pendingFaces.toString(),
                    "Pendentes",
                    if (pendingFaces > 0) Icons.Default.Warning else Icons.Default.CheckCircle,
                    Modifier.weight(1f),
                    attention = pendingFaces > 0,
                )
                PcMetricTile(accessAccounts.toString(), "Acessos", Icons.Default.Badge, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CollaboratorCardV4(
    collaborator: Colaborador,
    loading: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    onSelected: (Boolean) -> Unit,
    onHistory: () -> Unit,
    onEdit: () -> Unit,
    onBiometric: () -> Unit,
    onDeleteBiometric: () -> Unit,
    onDeleteCollaborator: () -> Unit,
) {
    val semantic = LocalPontoCafeSemanticColors.current
    val borderColor = when {
        selected || expanded -> MaterialTheme.colorScheme.primary.copy(alpha = 0.46f)
        !collaborator.rostoCadastrado -> semantic.warning.copy(alpha = 0.24f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(PontoCafeSpacing.md),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                if (selectionMode) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = onSelected,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }

                InitialAvatar(
                    name = collaborator.nome,
                    modifier = Modifier.align(Alignment.Top),
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // Sem maxLines/ellipsis: o nome completo é requisito desta tela.
                    Text(
                        text = collaborator.nome,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Text(
                        text = listOfNotNull(collaborator.setor, collaborator.turno)
                            .filter { it.isNotBlank() }
                            .joinToString(" · ")
                            .ifBlank { "Sem setor/turno" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (!selectionMode) {
                        StatusPill(
                            text = if (collaborator.rostoCadastrado) "Pronto" else "Pendente",
                            tone = if (collaborator.rostoCadastrado) PontoCafeTone.SUCCESS else PontoCafeTone.WARNING,
                        )
                    }
                }

                if (!selectionMode) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Recolher ações" else "Ver ações",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.CenterVertically),
                    )
                }
            }

            if (!selectionMode && !collaborator.rostoCadastrado) {
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

            AnimatedVisibility(visible = expanded && !selectionMode) {
                Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
                    ) {
                        OutlinedButton(
                            onClick = onHistory,
                            modifier = Modifier.weight(1f),
                            enabled = !loading,
                        ) {
                            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(17.dp))
                            Text("Histórico", modifier = Modifier.padding(start = 5.dp))
                        }
                        OutlinedButton(
                            onClick = onEdit,
                            modifier = Modifier.weight(1f),
                            enabled = !loading,
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(17.dp))
                            Text("Editar", modifier = Modifier.padding(start = 5.dp))
                        }
                    }

                    if (collaborator.rostoCadastrado) {
                        OutlinedButton(
                            onClick = onBiometric,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !loading,
                        ) {
                            Icon(Icons.Default.Face, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Atualizar rosto", modifier = Modifier.padding(start = 7.dp))
                        }

                        OutlinedButton(
                            onClick = onDeleteBiometric,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !loading,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.40f)),
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Excluir rosto", modifier = Modifier.padding(start = 7.dp))
                        }
                    }

                    OutlinedButton(
                        onClick = onDeleteCollaborator,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.62f)),
                    ) {
                        Icon(Icons.Default.PersonRemove, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Excluir colaborador", modifier = Modifier.padding(start = 7.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DeleteCollaboratorDialogV4(
    collaborator: Colaborador,
    loading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("Excluir colaborador?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                Text(
                    "${collaborator.nome} será removido da equipe ativa e não poderá mais utilizar o Ponto Café.",
                )
                Text(
                    "Todos os registros faciais serão apagados definitivamente. Autorizações pendentes serão canceladas.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PcStateBanner(
                    title = "Histórico preservado",
                    supportingText = "Pausas concluídas e auditoria permanecem disponíveis para rastreabilidade.",
                    tone = PontoCafeTone.INFO,
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        "Se houver uma pausa aberta, a exclusão será bloqueada até o retorno ser registrado.",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !loading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Icon(Icons.Default.PersonRemove, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(if (loading) "Excluindo..." else "Excluir colaborador", modifier = Modifier.padding(start = 7.dp))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) { Text("Cancelar") }
        },
    )
}

@Composable
private fun DeleteBiometricDialogV4(
    collaborator: Colaborador,
    loading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("Excluir biometria facial?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                Text("Todos os registros de rosto de ${collaborator.nome} serão apagados definitivamente.")
                Text(
                    "O colaborador e seu histórico permanecem. Para usar o Ponto novamente, será necessário cadastrar o rosto.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !loading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(if (loading) "Excluindo..." else "Excluir rosto", modifier = Modifier.padding(start = 7.dp))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) { Text("Cancelar") }
        },
    )
}

@Composable
private fun ImportPreviewDialogV4(
    preview: CsvImportPreview,
    loading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Importar colaboradores") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                Text("${preview.valid.size} registro(s) válido(s) encontrados.")
                if (preview.errors.isNotEmpty()) {
                    Text("Avisos", style = MaterialTheme.typography.titleSmall)
                    preview.errors.take(6).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    if (preview.errors.size > 6) {
                        Text("… e mais ${preview.errors.size - 6} aviso(s).")
                    }
                }
                Text("Formato recomendado: Nome;Setor;Turno", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = preview.valid.isNotEmpty() && !loading) {
                Text("Importar ${preview.valid.size}")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) { Text("Cancelar") }
        },
    )
}

@Composable
private fun BulkEditDialogV4(
    selectedCount: Int,
    loading: Boolean,
    onDismiss: () -> Unit,
    onApply: (sector: String?, shift: String?, deactivate: Boolean) -> Unit,
) {
    var sector by remember { mutableStateOf("") }
    var shift by remember { mutableStateOf("") }
    var changeSector by remember { mutableStateOf(false) }
    var changeShift by remember { mutableStateOf(false) }
    var deactivate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar $selectedCount colaborador(es)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = changeSector, onCheckedChange = { changeSector = it })
                    Text("Alterar setor")
                }
                if (changeSector) {
                    OutlinedTextField(
                        value = sector,
                        onValueChange = { sector = it.take(80) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Novo setor") },
                        singleLine = true,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = changeShift, onCheckedChange = { changeShift = it })
                    Text("Alterar turno")
                }
                if (changeShift) {
                    OutlinedTextField(
                        value = shift,
                        onValueChange = { shift = it.take(40) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Novo turno") },
                        singleLine = true,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = deactivate, onCheckedChange = { deactivate = it })
                    Text("Desativar colaboradores selecionados")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApply(
                        sector.takeIf { changeSector }?.trim(),
                        shift.takeIf { changeShift }?.trim(),
                        deactivate,
                    )
                },
                enabled = !loading && (changeSector || changeShift || deactivate),
            ) {
                Text(if (loading) "Aplicando..." else "Aplicar alterações")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) { Text("Cancelar") }
        },
    )
}

@Composable
private fun EditCollaboratorDialogV4(
    collaborator: Colaborador,
    loading: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, sector: String, shift: String) -> Unit,
) {
    var name by remember(collaborator.id) { mutableStateOf(collaborator.nome) }
    var sector by remember(collaborator.id) { mutableStateOf(collaborator.setor.orEmpty()) }
    var shift by remember(collaborator.id) { mutableStateOf(collaborator.turno.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar colaborador") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(160) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nome") },
                    singleLine = false,
                    maxLines = 3,
                )
                OutlinedTextField(
                    value = sector,
                    onValueChange = { sector = it.take(120) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Setor") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = shift,
                    onValueChange = { shift = it.take(80) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Turno") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name.trim(), sector.trim(), shift.trim()) },
                enabled = !loading && name.trim().length >= 2,
            ) {
                Text(if (loading) "Salvando..." else "Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) { Text("Cancelar") }
        },
    )
}