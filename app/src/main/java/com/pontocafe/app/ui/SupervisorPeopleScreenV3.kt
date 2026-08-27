@file:OptIn(ExperimentalFoundationApi::class)

package com.pontocafe.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pontocafe.app.SupervisorViewModel
import com.pontocafe.app.data.Colaborador
import com.pontocafe.app.data.PontoRepositories
import com.pontocafe.app.data.SecureAdminSessionStore
import com.pontocafe.app.data.SupervisorApiClient
import kotlinx.coroutines.launch

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
    // Vida longa: antes era remember, e o cache morria ao sair da tela.
    val avatarRepository = remember(sessionStore) { PontoRepositories.supervisor(sessionStore) }
    val activeAccount = remember(sessionStore, state.sessaoAdministrativa) { sessionStore.activeAccount() }
    val accountProfileLabel = if (state.sessaoAdministrativa) "Administrador" else "Supervisor"
    val accountFallbackName = activeAccount?.name?.takeIf { it.isNotBlank() } ?: accountProfileLabel

    var search by rememberSaveable { mutableStateOf("") }
    var faceFilter by rememberSaveable { mutableStateOf(PeopleFaceFilter.ALL) }
    var peopleSort by rememberSaveable { mutableStateOf(PeopleSort.PRIORITY) }
    var selectedPersonId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteFace by remember { mutableStateOf<Colaborador?>(null) }
    var deleteCollaborator by remember { mutableStateOf<Colaborador?>(null) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var sectorFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var shiftFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var avatarTarget by remember { mutableStateOf<Colaborador?>(null) }
    var avatarBusyId by remember { mutableStateOf<String?>(null) }
    var avatarError by remember { mutableStateOf<String?>(null) }
    var avatarMessage by remember { mutableStateOf<String?>(null) }
    var showAccountSheet by remember { mutableStateOf(false) }

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
                PcDialogBody {
                    Text("O rosto de ${collaborator.nome} será removido. O colaborador continuará ativo e poderá cadastrar a biometria novamente.")
                }
            },
            confirmButton = {
                PcDangerButton(
                    text = "Excluir rosto",
                    onClick = {
                        deleteFace = null
                        viewModel.excluirRosto(collaborator)
                    },
                    enabled = !state.carregando,
                    loading = state.carregando,
                )
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
                PcDialogBody {
                    Text("${collaborator.nome} deixará de aparecer imediatamente entre os colaboradores ativos.")
                    PcStateBanner(
                        title = "Histórico preservado",
                        supportingText = "Pausas e auditoria anteriores continuam disponíveis. A biometria será excluída.",
                        tone = PontoCafeTone.INFO,
                    )
                }
            },
            confirmButton = {
                PcDangerButton(
                    text = "Excluir colaborador",
                    onClick = {
                        deleteCollaborator = null
                        selectedPersonId = null
                        viewModel.excluirColaborador(collaborator)
                    },
                    enabled = !state.carregando,
                    loading = state.carregando,
                )
            },
            dismissButton = {
                TextButton(onClick = { deleteCollaborator = null }, enabled = !state.carregando) { Text("Cancelar") }
            },
        )
    }

    val all = state.colaboradores.sortedBy { it.nome.lowercase() }
    val pending = all.count { !it.rostoCadastrado }
    val query = search.trim()

    val sectors = all
        .mapNotNull { it.setor?.trim()?.takeIf(String::isNotBlank) }
        .distinct()
        .sortedBy { it.lowercase() }

    val shifts = all
        .mapNotNull { it.turno?.trim()?.takeIf(String::isNotBlank) }
        .distinct()
        .sortedBy { it.lowercase() }

    val filtered = all.asSequence()
        .filter { faceFilter != PeopleFaceFilter.PENDING || !it.rostoCadastrado }
        .filter {
            query.isBlank() ||
                it.nome.contains(query, true) ||
                it.setor.orEmpty().contains(query, true) ||
                it.turno.orEmpty().contains(query, true)
        }
        .filter { sectorFilter == null || it.setor.orEmpty().equals(sectorFilter, ignoreCase = true) }
        .filter { shiftFilter == null || it.turno.orEmpty().equals(shiftFilter, ignoreCase = true) }
        .toList()
    val visible = when (peopleSort) {
        PeopleSort.PRIORITY -> filtered.sortedWith(
            compareBy<Colaborador>({ it.rostoCadastrado }, { it.nome.lowercase() }),
        )
        PeopleSort.NAME -> filtered.sortedBy { it.nome.lowercase() }
        PeopleSort.SECTOR -> filtered.sortedWith(
            compareBy<Colaborador>({ it.setor.orEmpty().lowercase() }, { it.nome.lowercase() }),
        )
    }

    val selectedPerson = all.firstOrNull { it.id == selectedPersonId }
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

    PontoCafeResponsiveOverlayScreen(
        modifier = Modifier
            .navigationBarsPadding()
            .imePadding(),
    ) { responsive ->
        // Cópia literal do bloco de AdminPeopleScreenV4, com a mesma divergência de
        // 20.dp contra os 24.dp da política. As duas passam a ler do sistema.
        val compactHeight = responsive.useCompactVerticalLayout
        val expandedLayout = responsive.isExpanded && !compactHeight && !responsive.usesVeryLargeText
        val pagePadding = responsive.pagePadding

        if (!expandedLayout && selectedPerson != null) {
            PersonActionBottomSheet(
                person = selectedPerson,
                loading = state.carregando || avatarBusyId == selectedPerson.id,
                onDismiss = { selectedPersonId = null },
                onBiometric = {
                    selectedPersonId = null
                    viewModel.cadastrarOuAtualizarRosto(selectedPerson)
                },
                onAvatar = {
                    selectedPersonId = null
                    openAvatar(selectedPerson)
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
                        deleteFace = selectedPerson
                    }
                },
                onDeleteCollaborator = {
                    selectedPersonId = null
                    deleteCollaborator = selectedPerson
                },
            )
        }

        PcHeroPage(
            heroContent = {
                PcHeroZoneTopBar(
                    title = "Pessoas",
                    eyebrow = accountProfileLabel,
                    account = activeAccount,
                    fallbackName = accountFallbackName,
                    onProfileClick = { showAccountSheet = true },
                    onBackToPonto = onClose,
                )
                if (!compactHeight) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                        PcHeroStat(value = "${all.size}", label = "Colaboradores", modifier = Modifier.weight(1f))
                        PcHeroStat(value = "$pending", label = "Rosto pendente", modifier = Modifier.weight(1f))
                    }
                }
            },
        ) {
        // Mesmo limite do dashboard -- em telas muito largas o conteúdo
        // mestre-detalhe não deve esticar até a borda.
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = PontoCafeDimensions.dashboardContentWidth),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = pagePadding),
                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
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

                PeopleSearchField(
                    value = search,
                    onValueChange = { search = it },
                    accessMode = false,
                )

                PeopleFaceFilterRow(
                    selected = faceFilter,
                    total = all.size,
                    pending = pending,
                    activeExtraFilters = activeExtraFilters,
                    sort = peopleSort,
                    onSelected = { faceFilter = it },
                    onOpenFilters = { showFilters = true },
                )

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
                            peopleListWithOptionalStickyHeaders(
                                people = visible,
                                alphabetical = peopleSort == PeopleSort.NAME,
                                emptyContent = {
                                    PcEmptyState(
                                        title = "Nenhum colaborador encontrado",
                                        supportingText = "Altere a busca ou os filtros para ver outros registros.",
                                        icon = Icons.Default.People,
                                    )
                                },
                            ) { person ->
                                PeoplePersonCard(
                                    person = person,
                                    selected = person.id == selectedPersonId,
                                    selectionMode = false,
                                    loading = state.carregando || avatarBusyId == person.id,
                                    onClick = { selectedPersonId = person.id },
                                    onSelected = {},
                                    onBiometric = { viewModel.cadastrarOuAtualizarRosto(person) },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }

                        PersonDetailPanel(
                            person = selectedPerson,
                            loading = state.carregando || (selectedPerson != null && avatarBusyId == selectedPerson.id),
                            onBiometric = viewModel::cadastrarOuAtualizarRosto,
                            onAvatar = ::openAvatar,
                            onDeleteAvatar = ::removeAvatar,
                            onDeleteFace = { deleteFace = it },
                            onDeleteCollaborator = { deleteCollaborator = it },
                            modifier = Modifier.weight(.52f),
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
                    ) {
                        peopleListWithOptionalStickyHeaders(
                            people = visible,
                            alphabetical = peopleSort == PeopleSort.NAME,
                            emptyContent = {
                                PcEmptyState(
                                    title = "Nenhum colaborador encontrado",
                                    supportingText = "Altere a busca ou os filtros para ver outros registros.",
                                    icon = Icons.Default.People,
                                )
                            },
                        ) { person ->
                            PeoplePersonCard(
                                person = person,
                                selected = false,
                                selectionMode = false,
                                loading = state.carregando || avatarBusyId == person.id,
                                onClick = { selectedPersonId = person.id },
                                onSelected = {},
                                onBiometric = { viewModel.cadastrarOuAtualizarRosto(person) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }

            ExtendedFloatingActionButton(
                onClick = viewModel::abrirNovoColaborador,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = pagePadding, bottom = PontoCafeSpacing.md),
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Novo colaborador") },
                expanded = true,
            )
        }
        }
        }
    }
}

/**
 * Cabeçalhos fixos por letra inicial -- só fazem sentido quando a lista está
 * ordenada por nome (PeopleSort.NAME). Nos outros modos (prioridade, setor) o
 * agrupamento alfabético quebraria a própria ordenação que a pessoa escolheu,
 * então a lista continua plana.
 */
private fun LazyListScope.peopleListWithOptionalStickyHeaders(
    people: List<Colaborador>,
    alphabetical: Boolean,
    emptyContent: @Composable () -> Unit,
    itemContent: @Composable androidx.compose.foundation.lazy.LazyItemScope.(Colaborador) -> Unit,
) {
    if (people.isEmpty()) {
        item("empty") { emptyContent() }
        return
    }
    if (!alphabetical) {
        items(people, key = { "supervisor-person-v4-${it.id}" }) { itemContent(it) }
        return
    }
    val grouped = people.groupBy { it.nome.trim().firstOrNull()?.uppercaseChar() ?: '#' }
    grouped.toSortedMap().forEach { (letter, group) ->
        stickyHeader(key = "supervisor-person-header-$letter") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Text(
                    letter.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                )
            }
        }
        items(group, key = { "supervisor-person-v4-${it.id}" }) { itemContent(it) }
    }
}
