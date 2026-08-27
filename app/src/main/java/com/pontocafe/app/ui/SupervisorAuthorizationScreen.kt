package com.pontocafe.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.pontocafe.app.ManualPunchResult
import com.pontocafe.app.ManualPunchType
import androidx.compose.ui.unit.dp
import com.pontocafe.app.SupervisorViewModel
import com.pontocafe.app.data.Colaborador
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val SupervisorAuthorizationReasons = listOf(
    "Necessidade operacional",
    "Atraso na produção",
    "Orientação do Supervisor",
    "Outro",
)

private val SupervisorManualPunchReasons = listOf(
    "Reconhecimento facial falhou",
    "Esqueceu de marcar",
    "Outro",
)

private enum class AuthorizationScreenMode { EXCECAO, MANUAL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupervisorAuthorizationScreen(viewModel: SupervisorViewModel) {
    val state = viewModel.state
    var modo by rememberSaveable { mutableStateOf(AuthorizationScreenMode.EXCECAO) }
    var selecionado by remember { mutableStateOf<Colaborador?>(null) }
    var busca by rememberSaveable { mutableStateOf("") }
    var motivoRapido by rememberSaveable { mutableStateOf<String?>(null) }
    var outroMotivo by rememberSaveable { mutableStateOf("") }
    var confirmarLiberacao by remember { mutableStateOf(false) }
    var confirmarCancelamento by remember { mutableStateOf(false) }

    fun trocarModo(novoModo: AuthorizationScreenMode) {
        modo = novoModo
        selecionado = null
        busca = ""
        motivoRapido = null
        outroMotivo = ""
        viewModel.limparRegistroManual()
    }

    val motivoFinal = when (motivoRapido) {
        null -> ""
        "Outro" -> outroMotivo.trim()
        else -> motivoRapido.orEmpty()
    }
    val motivoValido = motivoFinal.length >= 2
    val reasonOptions = if (modo == AuthorizationScreenMode.MANUAL) SupervisorManualPunchReasons else SupervisorAuthorizationReasons

    val filtrados = state.colaboradores
        .asSequence()
        .filter {
            val query = busca.trim()
            query.isBlank() ||
                it.nome.contains(query, ignoreCase = true) ||
                it.setor?.contains(query, ignoreCase = true) == true ||
                it.turno?.contains(query, ignoreCase = true) == true
        }
        .sortedBy { it.nome.lowercase() }
        .toList()

    val liberacaoAtiva = state.authorizationId != null
    val expiraEmMillis = remember(state.authorizationId, state.authorizationExpiresSeconds) {
        if (state.authorizationId != null && (state.authorizationExpiresSeconds ?: 0) > 0) {
            System.currentTimeMillis() + (state.authorizationExpiresSeconds ?: 0) * 1_000L
        } else {
            null
        }
    }
    val expiraEmLocal = expiraEmMillis?.let(::formatAuthorizationClock)

    if (confirmarLiberacao && selecionado != null) {
        val manual = modo == AuthorizationScreenMode.MANUAL
        AlertDialog(
            onDismissRequest = { if (!state.carregando) confirmarLiberacao = false },
            title = { Text(if (manual) "Confirmar registro manual" else "Confirmar liberação") },
            text = {
                PcDialogBody {
                    Text(
                        if (manual) "Registrar saída de ${selecionado!!.nome} agora?" else "Liberar ${selecionado!!.nome}?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Motivo: $motivoFinal",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (manual) {
                            "Use somente quando o reconhecimento facial não identificar a pessoa dentro do horário normal. Fica registrado que esta saída foi feita manualmente."
                        } else {
                            "O período será identificado automaticamente pelo servidor de acordo com o horário atual. A liberação é de uso único e expira automaticamente."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                PcPrimaryButton(
                    text = if (manual) "Registrar saída" else "Liberar pausa",
                    onClick = {
                        confirmarLiberacao = false
                        selecionado?.let {
                            if (manual) viewModel.registrarPausaManual(it, motivoFinal) else viewModel.autorizarPausa(it, motivoFinal)
                        }
                    },
                    loading = state.carregando,
                )
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmarLiberacao = false },
                    enabled = !state.carregando,
                ) {
                    Text("Voltar")
                }
            },
        )
    }

    if (confirmarCancelamento && selecionado != null) {
        AlertDialog(
            onDismissRequest = { if (!state.carregando) confirmarCancelamento = false },
            title = { Text("Cancelar liberação?") },
            text = {
                PcDialogBody {
                    Text(
                        "${selecionado!!.nome} deixará de poder iniciar esta pausa fora do horário. Se a liberação já tiver sido usada, o servidor não permitirá o cancelamento.",
                    )
                }
            },
            confirmButton = {
                PcDangerButton(
                    text = "Cancelar liberação",
                    onClick = {
                        confirmarCancelamento = false
                        selecionado?.let {
                            viewModel.cancelarAutorizacao(it)
                        }
                    },
                    loading = state.carregando,
                )
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmarCancelamento = false },
                    enabled = !state.carregando,
                ) {
                    Text("Manter liberação")
                }
            },
        )
    }

    val manualSucesso = state.manualPunchResult
    PcHeroPage(
        heroContent = {
            PcHeroZoneScreenHeader(
                title = if (modo == AuthorizationScreenMode.MANUAL) "Registro manual de ponto" else "Liberações fora do horário",
                onBack = viewModel::voltarAoVivo,
                backLabel = "Ao vivo",
                eyebrow = "Supervisor",
            )
            Text(
                when {
                    liberacaoAtiva || manualSucesso != null -> "Registrado"
                    selecionado == null -> "Passo 1 de 2 · Escolha o colaborador"
                    else -> "Passo 2 de 2 · Informe o motivo"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
            )
        },
    ) {
    PontoCafeResponsivePage(maxContentWidth = 900.dp) { responsive ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding(),
        ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = responsive.pagePadding,
                end = responsive.pagePadding,
                top = PontoCafeSpacing.md,
                bottom = PontoCafeSpacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
        ) {
            if (!liberacaoAtiva && manualSucesso == null) {
                item(key = "mode-toggle") {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        AuthorizationScreenMode.entries.forEachIndexed { index, option ->
                            SegmentedButton(
                                selected = modo == option,
                                onClick = { trocarModo(option) },
                                shape = SegmentedButtonDefaults.itemShape(index, AuthorizationScreenMode.entries.size),
                            ) {
                                Text(if (option == AuthorizationScreenMode.MANUAL) "Registrar manualmente" else "Autorizar exceção")
                            }
                        }
                    }
                }
            }

            item(key = "context") {
                if (modo == AuthorizationScreenMode.MANUAL) ManualPunchContextCard() else AuthorizationContextCard()
            }

            state.erro?.let { error ->
                item(key = "error") {
                    AuthorizationFeedbackCard(text = error, error = true)
                }
            }

            state.mensagem?.let { message ->
                item(key = "message") {
                    AuthorizationFeedbackCard(text = message, error = false)
                }
            }

            if (manualSucesso != null) {
                item(key = "manual-success") {
                    ManualPunchSuccessCard(
                        result = manualSucesso,
                        onAnother = {
                            viewModel.limparRegistroManual()
                            selecionado = null
                            busca = ""
                            motivoRapido = null
                            outroMotivo = ""
                        },
                    )
                }
            } else if (liberacaoAtiva) {
                item(key = "success") {
                    AuthorizationReleasedCard(
                        employeeName = state.authorizationEmployeeName ?: selecionado?.nome ?: "Colaborador",
                        period = state.authorizationPeriod,
                        reason = motivoFinal,
                        expiresAt = expiraEmLocal,
                        loading = state.carregando,
                        onCancel = { confirmarCancelamento = true },
                        onAnother = {
                            viewModel.limparAutorizacao()
                            selecionado = null
                            busca = ""
                            motivoRapido = null
                            outroMotivo = ""
                        },
                    )
                }
            } else if (selecionado == null) {
                item(key = "employee-step") {
                    AuthorizationStepHeader(
                        number = "1",
                        title = "Escolha o colaborador",
                        subtitle = "Busque pelo nome, setor ou turno.",
                    )
                }

                item(key = "search") {
                    val focusManager = LocalFocusManager.current
                    OutlinedTextField(
                        value = busca,
                        onValueChange = { busca = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Buscar colaborador") },
                        placeholder = { Text("Digite o nome") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                        shape = RoundedCornerShape(20.dp),
                    )
                }

                item(key = "count") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (busca.isBlank()) "Colaboradores" else "Resultados",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            filtrados.size.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (filtrados.isEmpty()) {
                    item(key = "empty") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Text(
                                "Nenhum colaborador encontrado.",
                                modifier = Modifier.padding(18.dp),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    items(filtrados, key = { "liberacao-${it.id}" }) { colaborador ->
                        AuthorizationEmployeeRow(
                            collaborator = colaborador,
                            onClick = {
                                selecionado = colaborador
                                busca = ""
                                motivoRapido = null
                                outroMotivo = ""
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            } else {
                item(key = "employee-step-selected") {
                    AuthorizationStepHeader(
                        number = "1",
                        title = "Colaborador",
                        subtitle = "A lista foi recolhida para reduzir erros de seleção.",
                        completed = true,
                    )
                }

                item(key = "selected-employee") {
                    AuthorizationSelectedEmployeeCard(
                        collaborator = selecionado!!,
                        onChange = {
                            selecionado = null
                            motivoRapido = null
                            outroMotivo = ""
                        },
                    )
                }

                if (modo == AuthorizationScreenMode.EXCECAO) {
                    item(key = "automatic-period") {
                        AutomaticPeriodCard()
                    }
                }

                item(key = "reason-step") {
                    AuthorizationStepHeader(
                        number = "2",
                        title = "Motivo",
                        subtitle = "Use um motivo rápido ou escolha Outro para detalhar.",
                    )
                }

                item(key = "quick-reasons") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(end = 8.dp),
                    ) {
                        items(reasonOptions, key = { it }) { reason ->
                            FilterChip(
                                selected = motivoRapido == reason,
                                onClick = {
                                    motivoRapido = reason
                                    if (reason != "Outro") outroMotivo = ""
                                },
                                label = { Text(reason) },
                            )
                        }
                    }
                }

                if (motivoRapido == "Outro") {
                    item(key = "other-reason") {
                        val focusManager = LocalFocusManager.current
                        OutlinedTextField(
                            value = outroMotivo,
                            onValueChange = { outroMotivo = it.take(300) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Descreva o motivo") },
                            placeholder = { Text("Ex.: atividade operacional terminou após o horário") },
                            minLines = 3,
                            maxLines = 5,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            shape = RoundedCornerShape(20.dp),
                            isError = outroMotivo.isNotBlank() && !motivoValido,
                            supportingText = {
                                Text(
                                    "${outroMotivo.length}/300",
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.End,
                                )
                            },
                        )
                    }
                }

                item(key = "review") {
                    AuthorizationReviewCard(
                        employeeName = selecionado!!.nome,
                        reason = motivoFinal.takeIf { motivoValido },
                        automaticPeriod = modo == AuthorizationScreenMode.EXCECAO,
                    )
                }
            }
        }

        if (!liberacaoAtiva && manualSucesso == null && selecionado != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 12.dp,
            ) {
                Column(
                    modifier = Modifier.padding(
                        start = responsive.pagePadding,
                        end = responsive.pagePadding,
                        top = PontoCafeSpacing.sm,
                        bottom = PontoCafeSpacing.md,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "${selecionado!!.nome.substringBefore(' ')}" +
                            if (modo == AuthorizationScreenMode.EXCECAO) " · período automático" else "",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    PcPrimaryButton(
                        text = if (modo == AuthorizationScreenMode.MANUAL) "Registrar saída" else "Liberar pausa",
                        onClick = { confirmarLiberacao = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = motivoValido,
                        loading = state.carregando,
                    )
                    if (!motivoValido) {
                        Text(
                            "Selecione um motivo para continuar.",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        }
    }
    }
}

@Composable
private fun AuthorizationContextCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "Liberação prévia",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Escolha apenas a pessoa e o motivo. O sistema define automaticamente a pausa da manhã ou da tarde usando a hora oficial do servidor.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AutomaticPeriodCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Default.AccessTime,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Período automático",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Você não precisa escolher Manhã ou Tarde. O servidor relaciona a liberação à janela de café correspondente ao horário atual.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AuthorizationFeedbackCard(text: String, error: Boolean) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                liveRegion = if (error) LiveRegionMode.Assertive else LiveRegionMode.Polite
                stateDescription = text
            },
        shape = RoundedCornerShape(18.dp),
        color = if (error) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            LocalPontoCafeSemanticColors.current.successContainer
        },
        border = BorderStroke(
            1.dp,
            if (error) MaterialTheme.colorScheme.error.copy(alpha = 0.30f)
            else LocalPontoCafeSemanticColors.current.success.copy(alpha = 0.30f),
        ),
    ) {
        Text(
            text,
            modifier = Modifier.padding(14.dp),
            color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun AuthorizationStepHeader(
    number: String,
    title: String,
    subtitle: String,
    completed: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.size(34.dp),
            shape = CircleShape,
            color = if (completed) {
                LocalPontoCafeSemanticColors.current.successContainer
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            },
            border = BorderStroke(
                1.dp,
                if (completed) {
                    LocalPontoCafeSemanticColors.current.success.copy(alpha = 0.30f)
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                },
            ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (completed) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = LocalPontoCafeSemanticColors.current.success,
                    )
                } else {
                    Text(
                        number,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AuthorizationEmployeeRow(
    collaborator: Colaborador,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale = rememberPcPressScale(interactionSource)
    Card(
        modifier = modifier.fillMaxWidth().pcPressScale(pressScale),
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CollaboratorAvatar(
                name = collaborator.nome,
                avatarUrl = collaborator.avatarUrl,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    collaborator.nome,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                CollaboratorAuthorizationDetail(collaborator)
            }
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun AuthorizationSelectedEmployeeCard(
    collaborator: Colaborador,
    onChange: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)),
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CollaboratorAvatar(
                    name = collaborator.nome,
                    avatarUrl = collaborator.avatarUrl,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        collaborator.nome,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    CollaboratorAuthorizationDetail(collaborator)
                }
                TextButton(onClick = onChange) { Text("Alterar") }
            }

            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    "Não liberado",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CollaboratorAuthorizationDetail(collaborator: Colaborador) {
    val detalhe = listOfNotNull(collaborator.setor, collaborator.turno)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
    if (detalhe.isNotBlank()) {
        Text(
            detalhe,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AuthorizationReviewCard(
    employeeName: String,
    reason: String?,
    automaticPeriod: Boolean = true,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "Revisão",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(employeeName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                reason ?: "Selecione um motivo",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (automaticPeriod) {
                    "Período definido automaticamente pelo horário do servidor."
                } else {
                    "A saída será registrada com o horário atual."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun ManualPunchContextCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = LocalPontoCafeSemanticColors.current.warningContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = LocalPontoCafeSemanticColors.current.onWarningContainer,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "Registro manual",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Use apenas quando o reconhecimento facial não identificar a pessoa dentro do horário normal. Fora do horário, use Autorizar exceção.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ManualPunchSuccessCard(
    result: ManualPunchResult,
    onAnother: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = LocalPontoCafeSemanticColors.current.successContainer,
        border = BorderStroke(
            1.dp,
            LocalPontoCafeSemanticColors.current.success.copy(alpha = 0.38f),
        ),
        shadowElevation = 10.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = LocalPontoCafeSemanticColors.current.success,
                    modifier = Modifier.size(34.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (result.tipo == ManualPunchType.INICIO) "Saída registrada" else "Retorno registrado",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Registrado manualmente às ${result.horarioLocal}",
                        style = MaterialTheme.typography.labelLarge,
                        color = LocalPontoCafeSemanticColors.current.success,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                result.colaboradorNome,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            PcPrimaryButton(
                text = "Registrar outra pessoa",
                onClick = onAnother,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AuthorizationReleasedCard(
    employeeName: String,
    period: String?,
    reason: String,
    expiresAt: String?,
    loading: Boolean,
    onCancel: () -> Unit,
    onAnother: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = LocalPontoCafeSemanticColors.current.successContainer,
        border = BorderStroke(
            1.dp,
            LocalPontoCafeSemanticColors.current.success.copy(alpha = 0.38f),
        ),
        shadowElevation = 10.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = LocalPontoCafeSemanticColors.current.success,
                    modifier = Modifier.size(34.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Pausa liberada",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Liberado agora",
                        style = MaterialTheme.typography.labelLarge,
                        color = LocalPontoCafeSemanticColors.current.success,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Text(
                employeeName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.52f),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        "Período: ${if (period == "MANHA") "manhã" else if (period == "TARDE") "tarde" else "definido pelo servidor"}",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text("Motivo: ${reason.ifBlank { "Exceção operacional" }}")
                    Text(
                        if (expiresAt != null) "Liberada até $expiresAt · uso único"
                        else "Uso único · expira automaticamente",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                "O colaborador já pode ir ao Ponto. O reconhecimento facial encontrará esta liberação automaticamente.",
                style = MaterialTheme.typography.bodyMedium,
            )

            PcDangerButton(
                text = "Cancelar liberação",
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading,
                loading = loading,
            )

            PcPrimaryButton(
                text = "Liberar outra pessoa",
                onClick = onAnother,
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading,
            )
        }
    }
}

private fun formatAuthorizationClock(epochMillis: Long): String = runCatching {
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.of("America/Fortaleza"))
        .format(DateTimeFormatter.ofPattern("HH:mm"))
}.getOrDefault("--:--")
