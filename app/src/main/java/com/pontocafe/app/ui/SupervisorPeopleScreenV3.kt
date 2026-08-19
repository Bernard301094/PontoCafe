package com.pontocafe.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pontocafe.app.SupervisorViewModel
import com.pontocafe.app.avatar.AvatarImageOptimizer
import com.pontocafe.app.data.Colaborador
import com.pontocafe.app.data.SecureAdminSessionStore
import com.pontocafe.app.data.SupervisorApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SupervisorPeopleFilterV3 { ALL, PENDING }

@Composable
fun SupervisorPeopleScreenV3(
    viewModel: SupervisorViewModel,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = viewModel.state
    val listState = rememberLazyListState()
    val sessionStore = remember(context, state.sessaoAdministrativa) {
        SecureAdminSessionStore(
            context.applicationContext,
            if (state.sessaoAdministrativa) "admin" else "supervisor",
        )
    }
    val avatarRepository = remember(sessionStore) { SupervisorApiClient.create(sessionStore) }
    val activeAccount = remember(sessionStore, state.sessaoAdministrativa) { sessionStore.activeAccount() }
    val accountProfileLabel = if (state.sessaoAdministrativa) "Administrador" else "Supervisor"
    val accountFallbackName = activeAccount?.name?.takeIf { it.isNotBlank() } ?: accountProfileLabel

    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(SupervisorPeopleFilterV3.ALL) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var deleteFace by remember { mutableStateOf<Colaborador?>(null) }
    var deleteCollaborator by remember { mutableStateOf<Colaborador?>(null) }
    var avatarTarget by remember { mutableStateOf<Colaborador?>(null) }
    var avatarBusyId by remember { mutableStateOf<String?>(null) }
    var avatarError by remember { mutableStateOf<String?>(null) }
    var avatarMessage by remember { mutableStateOf<String?>(null) }
    var showAccountSheet by remember { mutableStateOf(false) }

    val avatarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val target = avatarTarget
        avatarTarget = null
        if (uri != null && target != null) {
            avatarBusyId = target.id
            avatarError = null
            avatarMessage = null
            scope.launch {
                runCatching {
                    val optimized = withContext(Dispatchers.IO) {
                        AvatarImageOptimizer.optimize(context.applicationContext, uri)
                    }
                    avatarRepository.uploadAvatar(target.id, optimized)
                    optimized.size
                }.onSuccess { bytes ->
                    avatarBusyId = null
                    avatarMessage = "Avatar de ${target.nome} otimizado para ${String.format("%.1f", bytes / 1024.0)} KB."
                    viewModel.abrirColaboradores()
                }.onFailure { error ->
                    avatarBusyId = null
                    avatarError = error.message ?: "Não foi possível salvar o avatar."
                }
            }
        }
    }

    if (showAccountSheet) {
        PcAccountProfileSheet(
            account = activeAccount,
            fallbackName = accountFallbackName,
            profileLabel = accountProfileLabel,
            onDismiss = { showAccountSheet = false },
            onLogout = if (state.sessaoAdministrativa) {
                null
            } else {
                {
                    showAccountSheet = false
                    viewModel.sair()
                }
            },
        )
    }

    deleteFace?.let { collaborator ->
        AlertDialog(
            onDismissRequest = { if (!state.carregando) deleteFace = null },
            title = { Text("Excluir biometria facial?") },
            text = {
                Text("O rosto de ${collaborator.nome} será removido. O colaborador continuará ativo e poderá cadastrar a biometria novamente.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        deleteFace = null
                        expandedId = null
                        viewModel.excluirRosto(collaborator)
                    },
                    enabled = !state.carregando,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Excluir rosto") }
            },
            dismissButton = {
                TextButton(onClick = { deleteFace = null }, enabled = !state.carregando) { Text("Cancelar") }
            },
        )
    }

    deleteCollaborator?.let { collaborator ->
        AlertDialog(
            onDismissRequest = { if (!state.carregando) deleteCollaborator = null },
            title = { Text("Excluir colaborador?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                    Text("${collaborator.nome} deixará de aparecer imediatamente entre os colaboradores ativos.")
                    PcStateBanner(
                        title = "Histórico preservado",
                        supportingText = "Pausas e auditoria anteriores continuam disponíveis. A biometria será excluída.",
                        tone = PontoCafeTone.INFO,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        deleteCollaborator = null
                        expandedId = null
                        viewModel.excluirColaborador(collaborator)
                    },
                    enabled = !state.carregando,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Excluir colaborador") }
            },
            dismissButton = {
                TextButton(onClick = { deleteCollaborator = null }, enabled = !state.carregando) { Text("Cancelar") }
            },
        )
    }

    val all = state.colaboradores.sortedBy { it.nome.lowercase() }
    val pending = all.count { !it.rostoCadastrado }
    val visible = all.asSequence()
        .filter { filter == SupervisorPeopleFilterV3.ALL || !it.rostoCadastrado }
        .filter {
            val query = search.trim()
            query.isBlank() ||
                it.nome.contains(query, true) ||
                it.setor.orEmpty().contains(query, true) ||
                it.turno.orEmpty().contains(query, true)
        }
        .sortedWith(compareBy<Colaborador>({ it.rostoCadastrado }, { it.nome.lowercase() }))
        .toList()

    PontoCafeResponsivePage(maxContentWidth = 900.dp) { responsive ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(
                    start = responsive.pagePadding,
                    end = responsive.pagePadding,
                    top = PontoCafeSpacing.md,
                    bottom = 104.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
            ) {
                item("header") {
                    PcAreaTopBar(
                        title = "Pessoas",
                        eyebrow = accountProfileLabel,
                        account = activeAccount,
                        fallbackName = accountFallbackName,
                        onProfileClick = { showAccountSheet = true },
                        onBackToPonto = onClose,
                    )
                }

                item("summary") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                    ) {
                        PcMetricTile(
                            value = all.size.toString(),
                            label = "Colaboradores",
                            icon = Icons.Default.People,
                            modifier = Modifier.weight(1f),
                        )
                        PcMetricTile(
                            value = pending.toString(),
                            label = "Rostos pendentes",
                            icon = Icons.Default.Face,
                            modifier = Modifier.weight(1f),
                            attention = pending > 0,
                        )
                    }
                }

                item("feedback") {
                    Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                        state.mensagem?.let { message ->
                            PcStateBanner(
                                title = "Alteração concluída",
                                supportingText = message,
                                tone = PontoCafeTone.SUCCESS,
                            )
                        }
                        state.erro?.let { error ->
                            PcStateBanner(
                                title = "Não foi possível concluir",
                                supportingText = error,
                                tone = PontoCafeTone.DANGER,
                            )
                        }
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
                }

                item("new") {
                    PcPrimaryButton(
                        text = "Novo colaborador",
                        icon = Icons.Default.Add,
                        onClick = viewModel::abrirNovoColaborador,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.carregando,
                    )
                }

                item("search") {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Buscar colaborador") },
                        placeholder = { Text("Nome, setor ou turno") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (search.isNotBlank()) {
                                IconButton(onClick = { search = "" }) {
                                    Text("×", style = MaterialTheme.typography.titleLarge)
                                }
                            }
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                    )
                }

                item("filters") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                        item {
                            FilterChip(
                                selected = filter == SupervisorPeopleFilterV3.ALL,
                                onClick = { filter = SupervisorPeopleFilterV3.ALL },
                                label = { Text("Todos ${all.size}") },
                            )
                        }
                        item {
                            FilterChip(
                                selected = filter == SupervisorPeopleFilterV3.PENDING,
                                onClick = { filter = SupervisorPeopleFilterV3.PENDING },
                                label = { Text("Pendentes $pending") },
                            )
                        }
                    }
                }

                item("title") {
                    SectionTitle(
                        "Colaboradores",
                        if (visible.isEmpty()) {
                            "Nenhum resultado para o filtro atual."
                        } else {
                            "Toque em uma pessoa para abrir rosto, avatar e ações de exclusão."
                        },
                    )
                }

                if (visible.isEmpty()) {
                    item("empty") {
                        PcEmptyState(
                            title = "Nenhum colaborador encontrado",
                            supportingText = "Altere a busca ou o filtro para ver outros registros.",
                            icon = Icons.Default.People,
                        )
                    }
                } else {
                    items(visible, key = { "supervisor-person-v3-${it.id}" }) { person ->
                        val expanded = expandedId == person.id
                        val avatarLoading = avatarBusyId == person.id
                        SupervisorPersonCardV3(
                            person = person,
                            expanded = expanded,
                            loading = state.carregando || avatarLoading,
                            onClick = { expandedId = if (expanded) null else person.id },
                            onBiometric = { viewModel.cadastrarOuAtualizarRosto(person) },
                            onAvatar = {
                                avatarError = null
                                avatarTarget = person
                                avatarLauncher.launch("image/*")
                            },
                            onDeleteAvatar = {
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
                            },
                            onDeleteFace = { deleteFace = person },
                            onDeleteCollaborator = { deleteCollaborator = person },
                        )
                    }
                }
            }

            PcScrollToTopFab(
                listState = listState,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = responsive.pagePadding, bottom = PontoCafeSpacing.md),
            )
        }
    }
}

@Composable
private fun SupervisorPersonCardV3(
    person: Colaborador,
    expanded: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
    onBiometric: () -> Unit,
    onAvatar: () -> Unit,
    onDeleteAvatar: () -> Unit,
    onDeleteFace: () -> Unit,
    onDeleteCollaborator: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(
            1.dp,
            if (expanded) MaterialTheme.colorScheme.primary.copy(alpha = .42f) else MaterialTheme.colorScheme.outlineVariant,
        ),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(PontoCafeSpacing.md),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                CollaboratorAvatar(person.nome, person.avatarUrl)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(person.nome, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        listOfNotNull(person.setor, person.turno)
                            .filter { it.isNotBlank() }
                            .joinToString(" · ")
                            .ifBlank { "Sem setor/turno" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusPill(
                            text = if (person.rostoCadastrado) "Rosto cadastrado" else "Rosto pendente",
                            tone = if (person.rostoCadastrado) PontoCafeTone.SUCCESS else PontoCafeTone.WARNING,
                        )
                        if (!person.avatarUrl.isNullOrBlank()) StatusPill("Avatar", PontoCafeTone.INFO)
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Fechar ações" else "Abrir ações",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!person.rostoCadastrado) {
                Button(
                    onClick = onBiometric,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading,
                ) {
                    Icon(Icons.Default.Face, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Cadastrar rosto", modifier = Modifier.padding(start = 7.dp))
                }
            }

            if (expanded) {
                OutlinedButton(
                    onClick = onAvatar,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading,
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        if (person.avatarUrl.isNullOrBlank()) "Escolher avatar" else "Trocar avatar",
                        modifier = Modifier.padding(start = 7.dp),
                    )
                }
                if (!person.avatarUrl.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = onDeleteAvatar,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading,
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Remover avatar", modifier = Modifier.padding(start = 7.dp))
                    }
                }
                Text(
                    "O avatar é WebP otimizado e separado da biometria facial; a imagem não é salva no banco de dados.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedButton(
                    onClick = onBiometric,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading,
                ) {
                    Icon(Icons.Default.Face, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        if (person.rostoCadastrado) "Atualizar rosto" else "Cadastrar rosto",
                        modifier = Modifier.padding(start = 7.dp),
                    )
                }

                if (person.rostoCadastrado) {
                    OutlinedButton(
                        onClick = onDeleteFace,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = .35f)),
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
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = .62f)),
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Excluir colaborador", modifier = Modifier.padding(start = 7.dp))
                }
            }
        }
    }
}
