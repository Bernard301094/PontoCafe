package com.pontocafe.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.GroupWork
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pontocafe.app.AdminReliabilityViewModel
import com.pontocafe.app.AdminViewModel
import com.pontocafe.app.data.AdminApiClient
import com.pontocafe.app.data.Colaborador
import com.pontocafe.app.data.SecureAdminSessionStore
import com.pontocafe.app.domain.CsvCollaboratorParser
import com.pontocafe.app.domain.CsvImportPreview
import kotlinx.coroutines.launch

private enum class AdminPeopleSection { COLLABORATORS, ACCESS }

private enum class AccessProfileFilter(val label: String) {
    ALL("Todos"),
    ADMIN("Admin"),
    SUPERVISOR("Supervisor"),
}

@Composable
fun AdminPeopleScreenV4(
    viewModel: AdminViewModel,
    reliabilityViewModel: AdminReliabilityViewModel,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = viewModel.state
    val reliabilityState = reliabilityViewModel.state
    val listState = rememberLazyListState()
    val adminSessionStore = remember(context) { SecureAdminSessionStore(context.applicationContext, "admin") }
    val avatarRepository = remember(adminSessionStore) { AdminApiClient.create(adminSessionStore) }
    val activeAccount = remember(adminSessionStore) { adminSessionStore.activeAccount() }
    val adminDisplayName = activeAccount?.name?.takeIf { it.isNotBlank() } ?: "Administrador"

    var search by rememberSaveable { mutableStateOf("") }
    var section by rememberSaveable { mutableStateOf(AdminPeopleSection.COLLABORATORS) }
    var faceFilter by rememberSaveable { mutableStateOf(PeopleFaceFilter.ALL) }
    var peopleSort by rememberSaveable { mutableStateOf(PeopleSort.PRIORITY) }
    var accessFilter by rememberSaveable { mutableStateOf(AccessProfileFilter.ALL) }
    var selectedPersonId by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Colaborador?>(null) }
    var deletingBiometric by remember { mutableStateOf<Colaborador?>(null) }
    var deletingCollaborator by remember { mutableStateOf<Colaborador?>(null) }
    var importPreview by remember { mutableStateOf<CsvImportPreview?>(null) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBulkDialog by remember { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var sectorFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var shiftFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var avatarTarget by remember { mutableStateOf<Colaborador?>(null) }
    var avatarBusyId by remember { mutableStateOf<String?>(null) }
    var avatarError by remember { mutableStateOf<String?>(null) }
    var avatarMessage by remember { mutableStateOf<String?>(null) }
    var showAccountSheet by remember { mutableStateOf(false) }
    var showToolsMenu by remember { mutableStateOf(false) }

    avatarTarget?.let { target ->
        CollaboratorAvatarSourceDialog(
            collaboratorName = target.nome,
            onDismiss = { avatarTarget = null },
            onImageReady = { optimized ->
                avatarBusyId = target.id
                avatarError = null
                avatarMessage = null
                scope.launch {
                    runCatching {
                        avatarRepository.uploadAvatar(target.id, optimized)
                        optimized.size
                    }.onSuccess { bytes ->
                        avatarBusyId = null
                        avatarTarget = null
                        avatarMessage = "Avatar de ${target.nome} otimizado para ${String.format("%.1f", bytes / 1024.0)} KB."
                        viewModel.abrirColaboradores()
                    }.onFailure { error ->
                        avatarBusyId = null
                        avatarError = error.message ?: "Não foi possível salvar o avatar."
                    }
                }
            },
            onError = { message -> avatarError = message },
        )
    }

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: error("Não foi possível abrir o arquivo.")
            }.onSuccess {
                importPreview = CsvCollaboratorParser.parse(it)
            }.onFailure { error ->
                importPreview = CsvImportPreview(
                    emptyList(),
                    listOf(error.message ?: "Não foi possível ler o CSV."),
                )
            }
        }
    }

    if (showAccountSheet) {
        PcAccountProfileSheet(
            account = activeAccount,
            fallbackName = adminDisplayName,
            profileLabel = "Administrador",
            onDismiss = { showAccountSheet = false },
            onLogout = {
                showAccountSheet = false
                viewModel.logout()
            },
        )
    }

    editing?.let { collaborator ->
        EditCollaboratorDialogV4(
            collaborator = collaborator,
            loading = state.carregando,
            onDismiss = { editing = null },
        ) { name, sector, shift ->
            viewModel.editarColaborador(collaborator, name, sector, shift)
            editing = null
        }
    }

    deletingBiometric?.let { collaborator ->
        DeleteBiometricDialogV4(
            collaborator = collaborator,
            loading = reliabilityState.loading,
            onDismiss = { if (!reliabilityState.loading) deletingBiometric = null },
            onConfirm = {
                reliabilityViewModel.deleteBiometric(collaborator.id)
                deletingBiometric = null
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
                selectedPersonId = null
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
        ) { sector, shift, deactivate ->
            reliabilityViewModel.updateBulk(
                selectedIds.toList(),
                sector,
                shift,
                if (deactivate) false else null,
            )
            selectedIds = emptySet()
            selectionMode = false
            showBulkDialog = false
        }
    }

    val allCollaborators = state.colaboradores.sortedBy { it.nome.lowercase() }
    val pendingFaces = allCollaborators.count { !it.rostoCadastrado }
    val query = search.trim()

    val sectors = allCollaborators
        .mapNotNull { it.setor?.trim()?.takeIf(String::isNotBlank) }
        .distinct()
        .sortedBy { it.lowercase() }

    val shifts = allCollaborators
        .mapNotNull { it.turno?.trim()?.takeIf(String::isNotBlank) }
        .distinct()
        .sortedBy { it.lowercase() }

    val filteredCollaborators = allCollaborators.asSequence()
        .filter {
            query.isBlank() ||
                it.nome.contains(query, true) ||
                it.setor.orEmpty().contains(query, true) ||
                it.turno.orEmpty().contains(query, true)
        }
        .filter { faceFilter != PeopleFaceFilter.PENDING || !it.rostoCadastrado }
        .filter { sectorFilter == null || it.setor.orEmpty().equals(sectorFilter, ignoreCase = true) }
        .filter { shiftFilter == null || it.turno.orEmpty().equals(shiftFilter, ignoreCase = true) }
        .toList()
    val collaborators = when (peopleSort) {
        PeopleSort.PRIORITY -> filteredCollaborators.sortedWith(
            compareBy<Colaborador>({ it.rostoCadastrado }, { it.nome.lowercase() }),
        )
        PeopleSort.NAME -> filteredCollaborators.sortedBy { it.nome.lowercase() }
        PeopleSort.SECTOR -> filteredCollaborators.sortedWith(
            compareBy<Colaborador>({ it.setor.orEmpty().lowercase() }, { it.nome.lowercase() }),
        )
    }

    val accounts = state.usuarios.asSequence()
        .filter {
            query.isBlank() ||
                it.nome.contains(query, true) ||
                it.email.contains(query, true) ||
                it.perfil.contains(query, true)
        }
        .filter { user ->
            when (accessFilter) {
                AccessProfileFilter.ALL -> true
                AccessProfileFilter.ADMIN -> user.perfil.contains("ADMIN", ignoreCase = true)
                AccessProfileFilter.SUPERVISOR -> user.perfil.contains("SUPERVISOR", ignoreCase = true)
            }
        }
        .sortedBy { it.nome.lowercase() }
        .toList()

    val selectedPerson = allCollaborators.firstOrNull { it.id == selectedPersonId }
    val activeExtraFilters = listOfNotNull(sectorFilter, shiftFilter).size

    if (showFilters) {
        PeopleFilterSheet(
            sectors = sectors,
            shifts = shifts,
            currentSector = sectorFilter,
            currentShift = shiftFilter,
            currentSort = peopleSort,
            onDismiss = { showFilters = false },
            onApply = { sector, shift, sort ->
                sectorFilter = sector
                shiftFilter = shift
                peopleSort = sort
                showFilters = false
            },
        )
    }

    fun openAvatar(person: Colaborador) {
        avatarError = null
        avatarMessage = null
        avatarTarget = person
    }

    fun removeAvatar(person: Colaborador) {
        avatarBusyId = person.id
        avatarError = null
        avatarMessage = null
        scope.launch {
            runCatching { avatarRepository.deleteAvatar(person.id) }
                .onSuccess {
                    avatarBusyId = null
                    avatarMessage = "Avatar de ${person.nome} removido."
                    viewModel.abrirColaboradores()
                }
                .onFailure { error ->
                    avatarBusyId = null
                    avatarError = error.message ?: "Não foi possível remover o avatar."
                }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        val windowClass = pontoCafeWindowSizeClass(maxWidth)
        val compactHeight = maxHeight < 480.dp
        val expandedLayout = windowClass == PontoCafeWindowSizeClass.EXPANDED &&
            !compactHeight &&
            LocalDensity.current.fontScale < 1.6f
        val pagePadding = when (windowClass) {
            PontoCafeWindowSizeClass.COMPACT -> if (maxWidth < 360.dp) 12.dp else 16.dp
            PontoCafeWindowSizeClass.MEDIUM,
            PontoCafeWindowSizeClass.EXPANDED -> 20.dp
        }

        if (!expandedLayout && selectedPerson != null && !selectionMode && section == AdminPeopleSection.COLLABORATORS) {
            PersonActionBottomSheet(
                person = selectedPerson,
                loading = state.carregando || reliabilityState.loading || avatarBusyId == selectedPerson.id,
                onDismiss = { selectedPersonId = null },
                onBiometric = {
                    selectedPersonId = null
                    viewModel.cadastrarOuAtualizarRosto(selectedPerson)
                },
                onAvatar = {
                    selectedPersonId = null
                    openAvatar(selectedPerson)
                },
                onHistory = {
                    selectedPersonId = null
                    reliabilityViewModel.openHistory(selectedPerson.id)
                },
                onEdit = {
                    selectedPersonId = null
                    editing = selectedPerson
                },
                onDeleteAvatar = if (selectedPerson.avatarUrl.isNullOrBlank()) null else {
                    {
                        selectedPersonId = null
                        removeAvatar(selectedPerson)
                    }
                },
                onDeleteFace = if (!selectedPerson.rostoCadastrado) null else {
                    {
                        selectedPersonId = null
                        deletingBiometric = selectedPerson
                    }
                },
                onDeleteCollaborator = {
                    selectedPersonId = null
                    deletingCollaborator = selectedPerson
                },
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = pagePadding),
                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                PcAreaTopBar(
                    title = "Pessoas",
                    eyebrow = "Administrador",
                    account = activeAccount,
                    fallbackName = adminDisplayName,
                    onProfileClick = { showAccountSheet = true },
                    onBackToPonto = onClose,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
                ) {
                    if (!compactHeight) {
                        PeopleCompactSummary(
                            total = allCollaborators.size,
                            pending = pendingFaces,
                            accessCount = state.usuarios.size,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    if (section == AdminPeopleSection.COLLABORATORS) {
                        Box {
                            IconButton(onClick = { showToolsMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Mais ações")
                            }
                            DropdownMenu(
                                expanded = showToolsMenu,
                                onDismissRequest = { showToolsMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Importar CSV") },
                                    onClick = {
                                        showToolsMenu = false
                                        fileLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain"))
                                    },
                                    leadingIcon = { Icon(Icons.Default.FileOpen, contentDescription = null) },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (selectionMode) "Cancelar seleção" else "Selecionar pessoas") },
                                    onClick = {
                                        showToolsMenu = false
                                        selectionMode = !selectionMode
                                        section = AdminPeopleSection.COLLABORATORS
                                        selectedPersonId = null
                                        if (!selectionMode) selectedIds = emptySet()
                                    },
                                    leadingIcon = { Icon(Icons.Default.GroupWork, contentDescription = null) },
                                )
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                    AdminFeedback(viewModel)
                    ReliabilityFeedback(reliabilityViewModel)
                    PcFeedbackBanner(
                        message = avatarError,
                        tone = PontoCafeTone.DANGER,
                        onDismiss = { avatarError = null },
                    )
                    PcFeedbackBanner(
                        message = avatarMessage,
                        tone = PontoCafeTone.SUCCESS,
                        onDismiss = { avatarMessage = null },
                        autoDismissMillis = 4_000L,
                    )
                }

                if (selectionMode) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = PontoCafeSpacing.sm, vertical = PontoCafeSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${selectedIds.size} de ${collaborators.size} selecionado(s)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "Selecione pessoas para edição em lote.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(
                                onClick = {
                                    selectedIds = if (selectedIds.size == collaborators.size && collaborators.isNotEmpty()) {
                                        emptySet()
                                    } else {
                                        collaborators.mapTo(linkedSetOf()) { it.id }
                                    }
                                },
                            ) {
                                Text(if (selectedIds.size == collaborators.size && collaborators.isNotEmpty()) "Limpar" else "Todos")
                            }
                            TextButton(
                                onClick = {
                                    selectionMode = false
                                    selectedIds = emptySet()
                                },
                            ) {
                                Text("Cancelar")
                            }
                        }
                    }
                } else {
                    PeopleSectionSwitch(
                        collaboratorSelected = section == AdminPeopleSection.COLLABORATORS,
                        collaboratorCount = allCollaborators.size,
                        accessCount = state.usuarios.size,
                        onCollaborators = {
                            section = AdminPeopleSection.COLLABORATORS
                            search = ""
                            selectedPersonId = null
                        },
                        onAccess = {
                            section = AdminPeopleSection.ACCESS
                            search = ""
                            selectedPersonId = null
                        },
                    )
                }

                PeopleSearchField(
                    value = search,
                    onValueChange = { search = it },
                    accessMode = section == AdminPeopleSection.ACCESS,
                )

                if (section == AdminPeopleSection.COLLABORATORS) {
                    PeopleFaceFilterRow(
                        selected = faceFilter,
                        total = allCollaborators.size,
                        pending = pendingFaces,
                        activeExtraFilters = activeExtraFilters,
                        sort = peopleSort,
                        onSelected = { faceFilter = it },
                        onOpenFilters = { showFilters = true },
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                        items(AccessProfileFilter.entries, key = { it.name }) { item ->
                            FilterChip(
                                selected = accessFilter == item,
                                onClick = { accessFilter = item },
                                label = { Text(item.label) },
                            )
                        }
                    }
                }

                if (section == AdminPeopleSection.COLLABORATORS) {
                    if (expandedLayout) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.weight(.48f),
                                contentPadding = PaddingValues(bottom = 96.dp),
                                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
                            ) {
                                if (collaborators.isEmpty()) {
                                    item("empty") {
                                        PcEmptyState(
                                            title = if (query.isBlank()) "Nenhuma pessoa nesta visão" else "Nenhum resultado",
                                            supportingText = if (query.isBlank()) {
                                                "Não há colaboradores que correspondam aos filtros selecionados."
                                            } else {
                                                "Tente outro nome, setor ou turno."
                                            },
                                            icon = Icons.Default.People,
                                        )
                                    }
                                } else {
                                    items(collaborators, key = { "person-v5-${it.id}" }) { collaborator ->
                                        val avatarLoading = avatarBusyId == collaborator.id
                                        PeoplePersonCard(
                                            person = collaborator,
                                            selected = if (selectionMode) {
                                                collaborator.id in selectedIds
                                            } else {
                                                collaborator.id == selectedPersonId
                                            },
                                            selectionMode = selectionMode,
                                            loading = state.carregando || reliabilityState.loading || avatarLoading,
                                            onClick = {
                                                if (selectionMode) {
                                                    selectedIds = if (collaborator.id in selectedIds) {
                                                        selectedIds - collaborator.id
                                                    } else {
                                                        selectedIds + collaborator.id
                                                    }
                                                } else {
                                                    selectedPersonId = collaborator.id
                                                }
                                            },
                                            onSelected = { checked ->
                                                selectedIds = if (checked) selectedIds + collaborator.id else selectedIds - collaborator.id
                                            },
                                            onBiometric = { viewModel.cadastrarOuAtualizarRosto(collaborator) },
                                        )
                                    }
                                }
                            }

                            PersonDetailPanel(
                                person = selectedPerson,
                                loading = state.carregando || reliabilityState.loading || (selectedPerson != null && avatarBusyId == selectedPerson.id),
                                onBiometric = viewModel::cadastrarOuAtualizarRosto,
                                onAvatar = ::openAvatar,
                                onHistory = { reliabilityViewModel.openHistory(it.id) },
                                onEdit = { editing = it },
                                onDeleteAvatar = ::removeAvatar,
                                onDeleteFace = { deletingBiometric = it },
                                onDeleteCollaborator = { deletingCollaborator = it },
                                modifier = Modifier.weight(.52f),
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(bottom = if (selectionMode) 112.dp else 96.dp),
                            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
                        ) {
                            if (collaborators.isEmpty()) {
                                item("empty") {
                                    PcEmptyState(
                                        title = if (query.isBlank()) "Nenhuma pessoa nesta visão" else "Nenhum resultado",
                                        supportingText = if (query.isBlank()) {
                                            "Não há colaboradores que correspondam aos filtros selecionados."
                                        } else {
                                            "Tente outro nome, setor ou turno."
                                        },
                                        icon = Icons.Default.People,
                                    )
                                }
                            } else {
                                items(collaborators, key = { "person-v5-${it.id}" }) { collaborator ->
                                    val avatarLoading = avatarBusyId == collaborator.id
                                    PeoplePersonCard(
                                        person = collaborator,
                                        selected = collaborator.id in selectedIds,
                                        selectionMode = selectionMode,
                                        loading = state.carregando || reliabilityState.loading || avatarLoading,
                                        onClick = {
                                            if (selectionMode) {
                                                selectedIds = if (collaborator.id in selectedIds) {
                                                    selectedIds - collaborator.id
                                                } else {
                                                    selectedIds + collaborator.id
                                                }
                                            } else {
                                                selectedPersonId = collaborator.id
                                            }
                                        },
                                        onSelected = { checked ->
                                            selectedIds = if (checked) selectedIds + collaborator.id else selectedIds - collaborator.id
                                        },
                                        onBiometric = { viewModel.cadastrarOuAtualizarRosto(collaborator) },
                                    )
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
                    ) {
                        if (accounts.isEmpty()) {
                            item("empty-access") {
                                PcEmptyState(
                                    "Nenhum acesso encontrado",
                                    "Tente outro nome, e-mail ou perfil.",
                                    Icons.Default.Badge,
                                )
                            }
                        } else {
                            items(accounts, key = { "access-v5-${it.id}" }) { user ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { viewModel.selecionarUsuario(user) },
                                    shape = MaterialTheme.shapes.large,
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    ),
                                    elevation = CardDefaults.cardElevation(0.dp),
                                ) {
                                    AccountSummaryRow(
                                        user.nome,
                                        user.email,
                                        user.perfil,
                                        user.ativo,
                                        Modifier.padding(PontoCafeSpacing.md),
                                    )
                                }
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
                            Text("Editar ${selectedIds.size}")
                        }
                    }
                }
            } else {
                ExtendedFloatingActionButton(
                    onClick = if (section == AdminPeopleSection.COLLABORATORS) {
                        viewModel::abrirNovoColaborador
                    } else {
                        viewModel::abrirNovaConta
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = pagePadding, bottom = PontoCafeSpacing.md),
                    icon = {
                        Icon(
                            if (section == AdminPeopleSection.COLLABORATORS) Icons.Default.Add else Icons.Default.Badge,
                            contentDescription = null,
                        )
                    },
                    text = {
                        Text(if (section == AdminPeopleSection.COLLABORATORS) "Novo colaborador" else "Novo acesso")
                    },
                )
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
            PcDialogBody {
                Text("${collaborator.nome} será removido da equipe ativa e não poderá mais utilizar o Ponto Café.")
                Text(
                    "Todos os registros faciais serão apagados definitivamente. Autorizações pendentes serão canceladas.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PcStateBanner(
                    "Histórico preservado",
                    "Pausas concluídas e auditoria permanecem disponíveis para rastreabilidade.",
                    PontoCafeTone.INFO,
                )
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        "Se houver uma pausa aberta, a exclusão será bloqueada até o retorno ser registrado.",
                        Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
        confirmButton = {
            PcDangerButton(
                text = "Excluir colaborador",
                onClick = onConfirm,
                enabled = !loading,
                loading = loading,
            )
        },
        dismissButton = { TextButton(onDismiss, enabled = !loading) { Text("Cancelar") } },
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
            PcDialogBody {
                Text("Todos os registros de rosto de ${collaborator.nome} serão apagados definitivamente.")
                Text(
                    "O colaborador e seu histórico permanecem. Para usar o Ponto novamente, será necessário cadastrar o rosto.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            PcDangerButton(
                text = "Excluir rosto",
                onClick = onConfirm,
                enabled = !loading,
                loading = loading,
            )
        },
        dismissButton = { TextButton(onDismiss, enabled = !loading) { Text("Cancelar") } },
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
            PcDialogBody {
                Text("${preview.valid.size} registro(s) válido(s) encontrados.")
                if (preview.errors.isNotEmpty()) {
                    Text("Avisos", style = MaterialTheme.typography.titleSmall)
                    preview.errors.take(6).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    if (preview.errors.size > 6) Text("… e mais ${preview.errors.size - 6} aviso(s).")
                }
                Text("Formato recomendado: Nome;Setor;Turno", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            PcPrimaryButton(
                text = "Importar ${preview.valid.size}",
                onClick = onConfirm,
                enabled = preview.valid.isNotEmpty(),
                loading = loading,
            )
        },
        dismissButton = { TextButton(onDismiss, enabled = !loading) { Text("Cancelar") } },
    )
}

@Composable
private fun BulkEditDialogV4(
    selectedCount: Int,
    loading: Boolean,
    onDismiss: () -> Unit,
    onApply: (String?, String?, Boolean) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var sector by remember { mutableStateOf("") }
    var shift by remember { mutableStateOf("") }
    var changeSector by remember { mutableStateOf(false) }
    var changeShift by remember { mutableStateOf(false) }
    var deactivate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar $selectedCount colaborador(es)") },
        text = {
            PcDialogBody {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(changeSector, { changeSector = it })
                    Text("Alterar setor")
                }
                if (changeSector) {
                    OutlinedTextField(
                        sector,
                        { sector = it.take(80) },
                        Modifier.fillMaxWidth(),
                        label = { Text("Novo setor") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) },
                        ),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(changeShift, { changeShift = it })
                    Text("Alterar turno")
                }
                if (changeShift) {
                    OutlinedTextField(
                        shift,
                        { shift = it.take(40) },
                        Modifier.fillMaxWidth(),
                        label = { Text("Novo turno") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(deactivate, { deactivate = it })
                    Text("Desativar colaboradores selecionados")
                }
            }
        },
        confirmButton = {
            PcPrimaryButton(
                text = "Aplicar alterações",
                onClick = {
                    onApply(
                        sector.takeIf { changeSector }?.trim(),
                        shift.takeIf { changeShift }?.trim(),
                        deactivate,
                    )
                },
                enabled = changeSector || changeShift || deactivate,
                loading = loading,
            )
        },
        dismissButton = { TextButton(onDismiss, enabled = !loading) { Text("Cancelar") } },
    )
}

@Composable
private fun EditCollaboratorDialogV4(
    collaborator: Colaborador,
    loading: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var name by remember(collaborator.id) { mutableStateOf(collaborator.nome) }
    var sector by remember(collaborator.id) { mutableStateOf(collaborator.setor.orEmpty()) }
    var shift by remember(collaborator.id) { mutableStateOf(collaborator.turno.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar colaborador") },
        text = {
            PcDialogBody {
                OutlinedTextField(
                    name,
                    { name = it.take(160) },
                    Modifier.fillMaxWidth(),
                    label = { Text("Nome") },
                    singleLine = false,
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) },
                    ),
                )
                OutlinedTextField(
                    sector,
                    { sector = it.take(120) },
                    Modifier.fillMaxWidth(),
                    label = { Text("Setor") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) },
                    ),
                )
                OutlinedTextField(
                    shift,
                    { shift = it.take(80) },
                    Modifier.fillMaxWidth(),
                    label = { Text("Turno") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        if (!loading && name.trim().length >= 2) {
                            onSave(name.trim(), sector.trim(), shift.trim())
                        }
                    }),
                )
            }
        },
        confirmButton = {
            PcPrimaryButton(
                text = "Salvar",
                onClick = { onSave(name.trim(), sector.trim(), shift.trim()) },
                enabled = name.trim().length >= 2,
                loading = loading,
            )
        },
        dismissButton = { TextButton(onDismiss, enabled = !loading) { Text("Cancelar") } },
    )
}
