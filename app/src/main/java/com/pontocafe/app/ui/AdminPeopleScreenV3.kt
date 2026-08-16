package com.pontocafe.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.GroupWork
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.People
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
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pontocafe.app.AdminReliabilityViewModel
import com.pontocafe.app.AdminViewModel
import com.pontocafe.app.data.Colaborador
import com.pontocafe.app.domain.CsvCollaboratorParser
import com.pontocafe.app.domain.CsvImportPreview

private enum class PeopleViewFilter(val label: String) {
    TEAM("Equipe"),
    PENDING_FACE("Rosto pendente"),
    ACCESS("Acessos"),
}

@Composable
fun AdminPeopleScreenV3(
    viewModel: AdminViewModel,
    reliabilityViewModel: AdminReliabilityViewModel,
) {
    val context = LocalContext.current
    val state = viewModel.state
    val reliabilityState = reliabilityViewModel.state

    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(PeopleViewFilter.TEAM) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Colaborador?>(null) }
    var importPreview by remember { mutableStateOf<CsvImportPreview?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBulkDialog by remember { mutableStateOf(false) }

    val biometricSuccess = state.mensagem?.takeIf { message ->
        message.startsWith("Rosto de ") && message.contains("cadastrado com 5 amostras")
    }
    val biometricSuccessName = biometricSuccess
        ?.removePrefix("Rosto de ")
        ?.substringBefore(" cadastrado")
        ?.trim()
        ?.ifBlank { null }
    val successSnackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(biometricSuccess) {
        if (biometricSuccess != null) {
            successSnackbarHostState.currentSnackbarData?.dismiss()
            successSnackbarHostState.showSnackbar(
                message = biometricSuccessName?.let { "✓ Rosto de $it registrado com sucesso." }
                    ?: "✓ Rosto registrado com sucesso.",
                actionLabel = "OK",
                duration = SnackbarDuration.Short,
            )
            viewModel.limparFeedback()
        }
    }

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: error("Não foi possível abrir o arquivo.")
            }.onSuccess { text ->
                importPreview = CsvCollaboratorParser.parse(text)
            }.onFailure { error ->
                importPreview = CsvImportPreview(
                    emptyList(),
                    listOf(error.message ?: "Não foi possível ler o CSV."),
                )
            }
        }
    }

    editing?.let { collaborator ->
        EditCollaboratorDialogV3(
            collaborator = collaborator,
            loading = state.carregando,
            onDismiss = { editing = null },
            onSave = { name, sector, shift ->
                viewModel.editarColaborador(collaborator, name, sector, shift)
                editing = null
            },
        )
    }

    importPreview?.let { preview ->
        ImportPreviewDialogV3(
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
        BulkEditDialogV3(
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

    val query = search.trim()
    val allCollaborators = state.colaboradores.sortedBy { it.nome.lowercase() }
    val pendingFaces = allCollaborators.count { !it.rostoCadastrado }
    val readyFaces = allCollaborators.size - pendingFaces

    val collaborators = allCollaborators
        .asSequence()
        .filter { collaborator ->
            query.isBlank() ||
                collaborator.nome.contains(query, ignoreCase = true) ||
                collaborator.setor.orEmpty().contains(query, ignoreCase = true) ||
                collaborator.turno.orEmpty().contains(query, ignoreCase = true)
        }
        .filter { collaborator -> filter != PeopleViewFilter.PENDING_FACE || !collaborator.rostoCadastrado }
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

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            contentPadding = PaddingValues(
                start = PontoCafeSpacing.lg,
                end = PontoCafeSpacing.lg,
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
                PeopleSummaryStrip(
                    collaborators = allCollaborators.size,
                    readyFaces = readyFaces,
                    pendingFaces = pendingFaces,
                    accessAccounts = state.usuarios.size,
                )
            }

            item("feedback") {
                Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                    if (biometricSuccess == null) AdminFeedback(viewModel)
                    ReliabilityFeedback(reliabilityViewModel)
                }
            }

            item("primary-actions") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
                ) {
                    Button(
                        onClick = viewModel::abrirNovoColaborador,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Colaborador", modifier = Modifier.padding(start = 6.dp))
                    }
                    OutlinedButton(
                        onClick = viewModel::abrirNovaConta,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, PontoCafePremium.borderSoft),
                    ) {
                        Icon(Icons.Default.Badge, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Acesso", modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }

            item("tools") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
                ) {
                    OutlinedButton(
                        onClick = { fileLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain")) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, PontoCafePremium.borderSoft),
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Importar", modifier = Modifier.padding(start = 6.dp))
                    }
                    OutlinedButton(
                        onClick = {
                            selectionMode = !selectionMode
                            filter = PeopleViewFilter.TEAM
                            expandedId = null
                            if (!selectionMode) selectedIds = emptySet()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, PontoCafePremium.borderSoft),
                    ) {
                        Icon(Icons.Default.GroupWork, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(if (selectionMode) "Cancelar" else "Selecionar", modifier = Modifier.padding(start = 6.dp))
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
                    label = { Text(if (filter == PeopleViewFilter.ACCESS) "Buscar acesso" else "Buscar colaborador") },
                    placeholder = {
                        Text(if (filter == PeopleViewFilter.ACCESS) "Nome, e-mail ou perfil" else "Nome, setor ou turno")
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                )
            }

            item("filters") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
                ) {
                    PeopleViewFilter.entries.forEach { item ->
                        FilterChip(
                            selected = filter == item,
                            onClick = {
                                if (!selectionMode || item != PeopleViewFilter.ACCESS) {
                                    filter = item
                                    expandedId = null
                                }
                            },
                            enabled = !selectionMode || item != PeopleViewFilter.ACCESS,
                            label = {
                                Text(
                                    when (item) {
                                        PeopleViewFilter.TEAM -> "Equipe ${allCollaborators.size}"
                                        PeopleViewFilter.PENDING_FACE -> "Pendentes $pendingFaces"
                                        PeopleViewFilter.ACCESS -> "Acessos ${state.usuarios.size}"
                                    },
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
            }

            if (filter != PeopleViewFilter.ACCESS) {
                item("results-heading") {
                    ResultsHeading(
                        title = if (filter == PeopleViewFilter.PENDING_FACE) "Biometrias pendentes" else "Equipe",
                        count = collaborators.size,
                        helper = if (selectionMode) {
                            "Toque nas pessoas que deseja alterar em lote."
                        } else if (filter == PeopleViewFilter.PENDING_FACE) {
                            "Priorize estes cadastros para liberar o uso do Ponto."
                        } else {
                            "Toque em uma pessoa para ver mais ações."
                        },
                    )
                }

                if (collaborators.isEmpty()) {
                    item("empty-collaborators") {
                        PeopleEmptyState(
                            title = if (query.isNotBlank()) "Nenhum resultado" else "Nenhuma pessoa nesta visão",
                            text = if (query.isNotBlank()) {
                                "Tente buscar por outro nome, setor ou turno."
                            } else {
                                "Não há colaboradores que correspondam ao filtro selecionado."
                            },
                        )
                    }
                } else {
                    items(collaborators, key = { "person-v3-${it.id}" }) { collaborator ->
                        val expanded = expandedId == collaborator.id
                        CollaboratorRowV3(
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
                        )
                    }
                }
            } else {
                item("access-heading") {
                    ResultsHeading(
                        title = "Contas de acesso",
                        count = accounts.size,
                        helper = "Administrador e Supervisor com acesso às áreas protegidas.",
                    )
                }

                if (accounts.isEmpty()) {
                    item("empty-access") {
                        PeopleEmptyState(
                            title = "Nenhum acesso encontrado",
                            text = "Tente outro nome, e-mail ou perfil.",
                        )
                    }
                } else {
                    items(accounts, key = { "access-v3-${it.id}" }) { user ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { viewModel.selecionarUsuario(user) },
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = PontoCafePremium.glassStrong),
                            border = BorderStroke(1.dp, PontoCafePremium.borderSoft),
                            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
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
                    .padding(horizontal = PontoCafeSpacing.lg, vertical = PontoCafeSpacing.sm),
                shape = RoundedCornerShape(24.dp),
                color = PontoCafePremium.glassStrong,
                border = BorderStroke(1.dp, PontoCafePremium.border),
                shadowElevation = 14.dp,
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
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text("Alterar")
                    }
                }
            }
        }

        SnackbarHost(
            hostState = successSnackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(
                    horizontal = PontoCafeSpacing.md,
                    vertical = if (selectionMode) 92.dp else PontoCafeSpacing.sm,
                ),
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                actionColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(20.dp),
            )
        }
    }
}

@Composable
private fun PeopleSummaryStrip(
    collaborators: Int,
    readyFaces: Int,
    pendingFaces: Int,
    accessAccounts: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
    ) {
        PeopleSummaryItem(
            value = collaborators.toString(),
            label = "Equipe",
            icon = Icons.Default.People,
            modifier = Modifier.weight(1f),
        )
        PeopleSummaryItem(
            value = readyFaces.toString(),
            label = "Com rosto",
            icon = Icons.Default.CheckCircle,
            modifier = Modifier.weight(1f),
        )
        PeopleSummaryItem(
            value = pendingFaces.toString(),
            label = "Pendentes",
            icon = if (pendingFaces > 0) Icons.Default.Warning else Icons.Default.CheckCircle,
            modifier = Modifier.weight(1f),
            attention = pendingFaces > 0,
        )
        PeopleSummaryItem(
            value = accessAccounts.toString(),
            label = "Acessos",
            icon = Icons.Default.Badge,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PeopleSummaryItem(
    value: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    attention: Boolean = false,
) {
    val semantic = LocalPontoCafeSemanticColors.current
    val content = if (attention) semantic.warning else MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = if (attention) semantic.warningContainer else PontoCafePremium.glassStrong,
        border = BorderStroke(
            1.dp,
            if (attention) semantic.warning.copy(alpha = 0.25f) else PontoCafePremium.borderSoft,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                color = content,
                fontWeight = FontWeight.Bold,
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ResultsHeading(
    title: String,
    count: Int,
    helper: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                helper,
                modifier = Modifier.padding(top = 3.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Text(
                count.toString(),
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun CollaboratorRowV3(
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
) {
    val borderColor = when {
        selected || expanded -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        !collaborator.rostoCadastrado -> LocalPontoCafeSemanticColors.current.warning.copy(alpha = 0.24f)
        else -> PontoCafePremium.borderSoft
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
            else PontoCafePremium.glassStrong,
        ),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (expanded) 6.dp else 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                if (selectionMode) {
                    Checkbox(checked = selected, onCheckedChange = onSelected)
                }
                InitialAvatar(collaborator.nome)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        collaborator.nome,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(collaborator.setor, collaborator.turno)
                            .filter { it.isNotBlank() }
                            .joinToString(" · ")
                            .ifBlank { "Sem setor/turno" },
                        modifier = Modifier.padding(top = 3.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!selectionMode) {
                    StatusPill(
                        text = if (collaborator.rostoCadastrado) "Pronto" else "Pendente",
                        tone = if (collaborator.rostoCadastrado) PontoCafeTone.SUCCESS else PontoCafeTone.WARNING,
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Recolher" else "Ver ações",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!selectionMode && !collaborator.rostoCadastrado) {
                Button(
                    onClick = onBiometric,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
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
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, PontoCafePremium.borderSoft),
                        ) {
                            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(17.dp))
                            Text("Histórico", modifier = Modifier.padding(start = 5.dp))
                        }
                        OutlinedButton(
                            onClick = onEdit,
                            modifier = Modifier.weight(1f),
                            enabled = !loading,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, PontoCafePremium.borderSoft),
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
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                        ) {
                            Icon(Icons.Default.Face, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Atualizar rosto", modifier = Modifier.padding(start = 7.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeopleEmptyState(
    title: String,
    text: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = PontoCafePremium.glassStrong,
        border = BorderStroke(1.dp, PontoCafePremium.borderSoft),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(30.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                title,
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text,
                modifier = Modifier.padding(top = 5.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ImportPreviewDialogV3(
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
                Text(
                    "Formato recomendado: Nome;Setor;Turno",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
private fun BulkEditDialogV3(
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
private fun EditCollaboratorDialogV3(
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
                    onValueChange = { name = it.take(140) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nome") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = sector,
                    onValueChange = { sector = it.take(80) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Setor") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = shift,
                    onValueChange = { shift = it.take(40) },
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
