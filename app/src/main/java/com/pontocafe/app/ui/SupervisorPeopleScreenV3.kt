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
    val activeAccount = remember(sessionStore, state.sessaoAdministrativa) { sessionStore.activeAccount() }
    val accountProfileLabel = if (state.sessaoAdministrativa) "Administrador" else "Supervisor"
    val accountFallbackName = activeAccount?.name?.takeIf { it.isNotBlank() } ?: accountProfileLabel

    var search by rememberSaveable { mutableStateOf("") }
    var faceFilter by rememberSaveable { mutableStateOf(PeopleFaceFilter.ALL) }
    var peopleSort by rememberSaveable { mutableStateOf(PeopleSort.PRIORITY) }
    var selectedPersonId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteCollaborator by remember { mutableStateOf<Colaborador?>(null) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var sectorFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var shiftFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var showAccountSheet by remember { mutableStateOf(false) }

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

    deleteCollaborator?.let { collaborator ->
        AlertDialog(
            onDismissRequest = { if (!state.carregando) deleteCollaborator = null },
            title = { Text("Excluir colaborador?") },
            text = {
                PcDialogBody {
                    Text("${collaborator.nome} deixará de aparecer imediatamente entre os colaboradores ativos.")
                    PcStateBanner(
                        title = "Histórico preservado",
                        supportingText = "Pausas e auditoria anteriores continuam disponíveis. Qualquer código pendente é cancelado.",
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
    val pending = all.count { it.emPausa }
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
        .filter { faceFilter != PeopleFaceFilter.EM_PAUSA || it.emPausa }
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
            compareBy<Colaborador>({ !it.emPausa }, { it.nome.lowercase() }),
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

    // Gerar um código sem o mostrar não serve para nada: o Supervisor precisa
    // lê-lo em voz alta para quem está do outro lado do balcão. A tela emitia
    // e deixava só a mensagem de sucesso -- os seis caracteres ficavam no
    // estado sem nunca aparecerem em lado nenhum.
    state.codigoEmitido?.let { emitido ->
        PcIssuedCodeDialog(codigo = emitido, onDismiss = viewModel::limparCodigoEmitido)
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
                loading = state.colaboradorOcupadoId == selectedPerson.id,
                onDismiss = { selectedPersonId = null },
                onGerarCodigo = {
                    selectedPersonId = null
                    viewModel.emitirCodigo(selectedPerson, null)
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
                // Mesma decisão da tela de Admin: os dois números repetiam os
                // chips logo abaixo e custavam quase um quinto da altura útil.
                if (!compactHeight) {
                    Text(
                        if (pending > 0) {
                            "$pending no café agora · ${all.size} colaboradores"
                        } else {
                            "Ninguém no café agora · ${all.size} colaboradores"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .82f),
                    )
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
                                    loading = state.colaboradorOcupadoId == person.id,
                                    onClick = { selectedPersonId = person.id },
                                    onSelected = {},
                                    onGerarCodigo = { viewModel.emitirCodigo(person, null) },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }

                        PersonDetailPanel(
                            person = selectedPerson,
                            loading = state.colaboradorOcupadoId == selectedPerson?.id,
                            onGerarCodigo = { pessoa -> viewModel.emitirCodigo(pessoa, null) },
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
                                loading = state.colaboradorOcupadoId == person.id,
                                onClick = { selectedPersonId = person.id },
                                onSelected = {},
                                onGerarCodigo = { viewModel.emitirCodigo(person, null) },
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
