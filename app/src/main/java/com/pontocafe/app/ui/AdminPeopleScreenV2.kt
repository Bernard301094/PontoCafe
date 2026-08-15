package com.pontocafe.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.GroupWork
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.pontocafe.app.AdminReliabilityViewModel
import com.pontocafe.app.AdminViewModel
import com.pontocafe.app.data.Colaborador
import com.pontocafe.app.domain.CsvCollaboratorParser
import com.pontocafe.app.domain.CsvImportPreview

private enum class PeopleFilterV2(val label: String) {
    ALL("Todos"),
    COLLABORATORS("Colaboradores"),
    ACCESS("Acessos"),
}

@Composable
fun AdminPeopleScreenV2(
    viewModel: AdminViewModel,
    reliabilityViewModel: AdminReliabilityViewModel,
) {
    val context = LocalContext.current
    val state = viewModel.state
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(PeopleFilterV2.ALL) }
    var editing by remember { mutableStateOf<Colaborador?>(null) }
    var importPreview by remember { mutableStateOf<CsvImportPreview?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBulkDialog by remember { mutableStateOf(false) }

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: error("Não foi possível abrir o arquivo.")
            }.getOrElse {
                importPreview = CsvImportPreview(emptyList(), listOf(it.message ?: "Não foi possível ler o CSV."))
                return@rememberLauncherForActivityResult
            }
            importPreview = CsvCollaboratorParser.parse(text)
        }
    }

    editing?.let { collaborator ->
        EditCollaboratorDialogV2(
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
        ImportPreviewDialog(
            preview = preview,
            loading = reliabilityViewModel.state.loading,
            onDismiss = { importPreview = null },
            onConfirm = {
                reliabilityViewModel.importCollaborators(preview.valid)
                importPreview = null
            },
        )
    }

    if (showBulkDialog) {
        BulkEditDialog(
            selectedCount = selectedIds.size,
            loading = reliabilityViewModel.state.loading,
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

    val collaborators = state.colaboradores
        .filter {
            search.isBlank() || it.nome.contains(search, true) || it.setor.orEmpty().contains(search, true) || it.turno.orEmpty().contains(search, true)
        }
        .sortedWith(compareBy({ it.rostoCadastrado }, { it.nome.lowercase() }))
    val accounts = state.usuarios
        .filter { search.isBlank() || it.nome.contains(search, true) || it.email.contains(search, true) }
        .sortedBy { it.nome.lowercase() }
    val pendingFaces = state.colaboradores.count { !it.rostoCadastrado }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = PontoCafeSpacing.lg),
        contentPadding = PaddingValues(top = PontoCafeSpacing.lg, bottom = PontoCafeSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
    ) {
        item("header") { PontoCafeScreenHeader(title = "Pessoas", eyebrow = "Equipe e acessos") }
        item("feedback") {
            Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                AdminFeedback(viewModel)
                ReliabilityFeedback(reliabilityViewModel)
            }
        }

        if (state.colaboradores.isNotEmpty()) {
            item("face-progress") {
                ThinProgressSummary(
                    completed = state.colaboradores.size - pendingFaces,
                    total = state.colaboradores.size,
                    title = "Cadastro facial",
                    detail = "${state.colaboradores.size - pendingFaces} de ${state.colaboradores.size} colaboradores prontos",
                )
            }
        }

        item("actions") {
            Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                    Button(onClick = viewModel::abrirNovoColaborador, modifier = Modifier.weight(1f)) { Text("Novo colaborador") }
                    OutlinedButton(onClick = viewModel::abrirNovaConta, modifier = Modifier.weight(1f)) { Text("Novo acesso") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                    OutlinedButton(
                        onClick = { fileLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain")) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null)
                        Text(" Importar CSV")
                    }
                    OutlinedButton(
                        onClick = {
                            selectionMode = !selectionMode
                            if (!selectionMode) selectedIds = emptySet()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.GroupWork, contentDescription = null)
                        Text(if (selectionMode) " Cancelar lote" else " Editar em lote")
                    }
                }
            }
        }

        if (selectionMode) {
            item("selection") {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Row(
                        Modifier.fillMaxWidth().padding(PontoCafeSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("${selectedIds.size} selecionado(s)")
                        Button(onClick = { showBulkDialog = true }, enabled = selectedIds.isNotEmpty()) { Text("Alterar") }
                    }
                }
            }
        }

        item("search") {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Buscar pessoa") },
                placeholder = { Text("Nome, setor, turno ou e-mail") },
                singleLine = true,
            )
        }
        item("filters") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                PeopleFilterV2.entries.forEach { item ->
                    FilterChip(selected = filter == item, onClick = { filter = item }, label = { Text(item.label) })
                }
            }
        }

        if (filter != PeopleFilterV2.ACCESS) {
            item("collaborators-title") {
                SectionTitle("Colaboradores", "${collaborators.size} resultado(s) · biometria pendente aparece primeiro.")
            }
            items(collaborators, key = { "person-${it.id}" }) { collaborator ->
                CollaboratorCardV2(
                    collaborator = collaborator,
                    loading = state.carregando || reliabilityViewModel.state.loading,
                    selectionMode = selectionMode,
                    selected = collaborator.id in selectedIds,
                    onSelected = { selected ->
                        selectedIds = if (selected) selectedIds + collaborator.id else selectedIds - collaborator.id
                    },
                    onHistory = { reliabilityViewModel.openHistory(collaborator.id) },
                    onEdit = { editing = collaborator },
                    onBiometric = { viewModel.cadastrarOuAtualizarRosto(collaborator) },
                )
            }
        }

        if (filter != PeopleFilterV2.COLLABORATORS) {
            item("accounts-title") { SectionTitle("Contas de acesso", "${accounts.size} conta(s) de Supervisor ou Administrador.") }
            items(accounts, key = { "access-${it.id}" }) { user ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { viewModel.selecionarUsuario(user) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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

@Composable
private fun CollaboratorCardV2(
    collaborator: Colaborador,
    loading: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onSelected: (Boolean) -> Unit,
    onHistory: () -> Unit,
    onEdit: () -> Unit,
    onBiometric: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(PontoCafeSpacing.md), verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                if (selectionMode) Checkbox(checked = selected, onCheckedChange = onSelected)
                InitialAvatar(collaborator.nome)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(collaborator.nome, style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOfNotNull(collaborator.setor, collaborator.turno).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Sem setor/turno" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    StatusPill(
                        if (collaborator.rostoCadastrado) "Rosto cadastrado" else "Rosto pendente",
                        if (collaborator.rostoCadastrado) PontoCafeTone.SUCCESS else PontoCafeTone.WARNING,
                    )
                }
            }
            if (!selectionMode) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                    OutlinedButton(onClick = onHistory, modifier = Modifier.weight(1f), enabled = !loading) { Text("Histórico") }
                    OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f), enabled = !loading) { Text("Editar") }
                }
                Button(onClick = onBiometric, modifier = Modifier.fillMaxWidth(), enabled = !loading) {
                    Text(if (collaborator.rostoCadastrado) "Atualizar rosto" else "Cadastrar rosto")
                }
            }
        }
    }
}

@Composable
private fun ImportPreviewDialog(
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
                    if (preview.errors.size > 6) Text("… e mais ${preview.errors.size - 6} aviso(s).")
                }
                Text("Formato recomendado: Nome;Setor;Turno", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = preview.valid.isNotEmpty() && !loading) { Text("Importar ${preview.valid.size}") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("Cancelar") } },
    )
}

@Composable
private fun BulkEditDialog(
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
        title = { Text("Alterar $selectedCount colaborador(es)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = changeSector, onCheckedChange = { changeSector = it })
                    Text("Alterar setor")
                }
                if (changeSector) {
                    OutlinedTextField(sector, { sector = it.take(120) }, Modifier.fillMaxWidth(), label = { Text("Novo setor") })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = changeShift, onCheckedChange = { changeShift = it })
                    Text("Alterar turno")
                }
                if (changeShift) {
                    OutlinedTextField(shift, { shift = it.take(80) }, Modifier.fillMaxWidth(), label = { Text("Novo turno") })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = deactivate, onCheckedChange = { deactivate = it })
                    Text("Desativar colaboradores selecionados")
                }
                if (deactivate) {
                    Text(
                        "A desativação será bloqueada se alguma pessoa estiver com pausa aberta. A biometria segue a política de retenção configurada.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(if (changeSector) sector else null, if (changeShift) shift else null, deactivate) },
                enabled = !loading && (changeSector || changeShift || deactivate),
            ) { Text("Aplicar") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("Cancelar") } },
    )
}

@Composable
private fun EditCollaboratorDialogV2(
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
                Text("A correção mantém biometria, UUID e histórico de pausas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(name, { name = it.take(160) }, Modifier.fillMaxWidth(), label = { Text("Nome completo") }, singleLine = true)
                OutlinedTextField(sector, { sector = it.take(120) }, Modifier.fillMaxWidth(), label = { Text("Setor") }, singleLine = true)
                OutlinedTextField(shift, { shift = it.take(80) }, Modifier.fillMaxWidth(), label = { Text("Turno") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, sector, shift) }, enabled = !loading && name.trim().length >= 2) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("Cancelar") } },
    )
}
