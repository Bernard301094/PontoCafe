package com.pontocafe.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pontocafe.app.PontoCafeViewModel
import com.pontocafe.app.PontoStep
import com.pontocafe.app.TipoComprovantePonto
import com.pontocafe.app.data.Colaborador
import com.pontocafe.app.data.PontoCafeRepository
import com.pontocafe.app.domain.AccessCode
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val KIOSK_ZONE: ZoneId = ZoneId.of("America/Fortaleza")
private const val RECEIPT_AUTO_DISMISS_MILLIS = 12_000L

internal enum class RestrictedAreaRequest { SUPERVISOR, ADMIN, LOGIN }

/**
 * Quiosque do Ponto Café.
 *
 * Três passos, nesta ordem, e nada mais: a pessoa acha o próprio nome, digita o
 * código de 6 caracteres que o Supervisor entregou, e lê o comprovante. O mesmo
 * código serve para sair e para voltar — quem decide qual das duas está a
 * acontecer é o servidor, nunca este ecrã.
 */
@Composable
fun PontoFlowHost(
    viewModel: PontoCafeViewModel,
    hasAdminSession: Boolean,
    hasSupervisorSession: Boolean,
    onAdminClick: () -> Unit,
    onSupervisorClick: () -> Unit,
    onLoginModeClick: () -> Unit,
) {
    val state = viewModel.state
    val scope = rememberCoroutineScope()

    var restrictedAreaRequest by remember { mutableStateOf<RestrictedAreaRequest?>(null) }
    var exitPin by remember { mutableStateOf("") }
    var unlockLoading by remember { mutableStateOf(false) }
    var unlockError by remember { mutableStateOf<String?>(null) }
    var wakeTick by remember { mutableIntStateOf(0) }

    fun fecharSolicitacaoAcesso() {
        restrictedAreaRequest = null
        exitPin = ""
        unlockLoading = false
        unlockError = null
    }

    // O repouso conta a partir da última interação real. Sem câmera, o que se
    // economiza aqui é só brilho de tela — mas num quiosque ligado 24 h isso
    // continua a ser a diferença entre um painel queimado e um inteiro.
    var idle by remember { mutableStateOf(false) }
    LaunchedEffect(state.passo, state.codigo, state.busca, state.selecionado?.id, wakeTick) {
        idle = false
        delay(KIOSK_IDLE_TIMEOUT_MILLIS)
        idle = true
    }

    restrictedAreaRequest?.let { target ->
        RestrictedAccessDialog(
            target = target,
            pin = exitPin,
            loading = unlockLoading,
            error = unlockError,
            onPinChange = { value ->
                exitPin = value.filter(Char::isDigit).take(12)
                unlockError = null
            },
            onDismiss = { if (!unlockLoading) fecharSolicitacaoAcesso() },
            onLogin = {
                if (!unlockLoading) {
                    fecharSolicitacaoAcesso()
                    onLoginModeClick()
                }
            },
            onConfirm = {
                val destination = target
                unlockLoading = true
                unlockError = null
                scope.launch {
                    runCatching { viewModel.validarPinSaida(exitPin, destination.name) }
                        .onSuccess {
                            fecharSolicitacaoAcesso()
                            when (destination) {
                                RestrictedAreaRequest.SUPERVISOR -> onSupervisorClick()
                                RestrictedAreaRequest.ADMIN -> onAdminClick()
                                RestrictedAreaRequest.LOGIN -> onLoginModeClick()
                            }
                        }
                        .onFailure { error ->
                            if (PontoCafeRepository.isDevicePinNotConfigured(error)) {
                                fecharSolicitacaoAcesso()
                                onLoginModeClick()
                            } else {
                                unlockLoading = false
                                exitPin = ""
                                unlockError = PontoCafeRepository.mensagemErro(error)
                            }
                        }
                }
            },
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compactHeight = maxHeight < 640.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            KioskTopBar(
                offline = state.modoOffline,
                pendingEvents = state.eventosPendentes,
                hasAdminSession = hasAdminSession,
                hasSupervisorSession = hasSupervisorSession,
                onAdmin = {
                    restrictedAreaRequest =
                        if (hasAdminSession) RestrictedAreaRequest.ADMIN else null
                    if (!hasAdminSession) onLoginModeClick()
                },
                onSupervisor = {
                    restrictedAreaRequest =
                        if (hasSupervisorSession) RestrictedAreaRequest.SUPERVISOR else null
                    if (!hasSupervisorSession) onLoginModeClick()
                },
                onAccess = { restrictedAreaRequest = RestrictedAreaRequest.LOGIN },
            )

            AnimatedContent(
                targetState = state.passo,
                transitionSpec = {
                    fadeIn(tween(PontoCafeMotion.Standard)) togetherWith
                        fadeOut(tween(PontoCafeMotion.Quick))
                },
                label = "ponto-step",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { passo ->
                when (passo) {
                    PontoStep.ESCOLHER_PESSOA -> CollaboratorPickerStep(
                        viewModel = viewModel,
                        compactHeight = compactHeight,
                    )

                    PontoStep.DIGITAR_CODIGO -> AccessCodeStep(
                        viewModel = viewModel,
                        compactHeight = compactHeight,
                    )

                    PontoStep.COMPROVANTE -> ReceiptStep(viewModel = viewModel)
                }
            }
        }

        PontoConnectivityFeedback(
            modoOffline = state.modoOffline,
            sincronizandoPendencias = state.sincronizandoPendencias,
            eventosPendentes = state.eventosPendentes,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .systemBarsPadding()
                .padding(horizontal = 18.dp, vertical = 10.dp),
        )

        KioskIdleSaver(
            idle = idle,
            onWake = { wakeTick += 1 },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun KioskTopBar(
    offline: Boolean,
    pendingEvents: Int,
    hasAdminSession: Boolean,
    hasSupervisorSession: Boolean,
    onAdmin: () -> Unit,
    onSupervisor: () -> Unit,
    onAccess: () -> Unit,
) {
    var agora by remember { mutableStateOf(ZonedDateTime.now(KIOSK_ZONE)) }
    LaunchedEffect(Unit) {
        while (true) {
            agora = ZonedDateTime.now(KIOSK_ZONE)
            delay(1_000L)
        }
    }
    val relogio = remember(agora.minute, agora.hour) {
        agora.format(DateTimeFormatter.ofPattern("HH:mm", Locale("pt", "BR")))
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = PontoCafeSpacing.md, vertical = PontoCafeSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
        ) {
            Icon(
                Icons.Default.Coffee,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Ponto Café",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    when {
                        offline && pendingEvents > 0 -> "Sem conexão · $pendingEvents registro(s) na fila"
                        offline -> "Sem conexão · registros ficam na fila"
                        pendingEvents > 0 -> "$pendingEvents registro(s) aguardando envio"
                        else -> "Conectado"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                relogio,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(
                onClick = when {
                    hasAdminSession -> onAdmin
                    hasSupervisorSession -> onSupervisor
                    else -> onAccess
                },
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "Área restrita",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// region Passo 1 — escolher a pessoa

@Composable
private fun CollaboratorPickerStep(
    viewModel: PontoCafeViewModel,
    compactHeight: Boolean,
) {
    val state = viewModel.state

    LaunchedEffect(Unit) { viewModel.carregarColaboradores(force = false) }

    val termo = state.busca.trim()
    val filtrados = remember(termo, state.colaboradores) {
        if (termo.isEmpty()) {
            state.colaboradores
        } else {
            state.colaboradores.filter { pessoa ->
                pessoa.nome.contains(termo, ignoreCase = true) ||
                    pessoa.setor.orEmpty().contains(termo, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = PontoCafeSpacing.md),
        verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
    ) {
        if (!compactHeight) Spacer(Modifier.height(PontoCafeSpacing.xs))

        Text(
            "Encontre o seu nome",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "Depois de escolher, digite o código de ${AccessCode.LENGTH} caracteres que o Supervisor entregou.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = state.busca,
            onValueChange = viewModel::atualizarBusca,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Buscar por nome ou setor") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Search,
            ),
        )

        when {
            state.carregandoColaboradores && state.colaboradores.isEmpty() -> {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            filtrados.isEmpty() -> {
                PcEmptyState(
                    title = if (state.colaboradores.isEmpty()) {
                        "Nenhum colaborador disponível"
                    } else {
                        "Nenhum nome corresponde à busca"
                    },
                    supportingText = if (state.colaboradores.isEmpty()) {
                        "Peça ao Supervisor para cadastrar as pessoas na área restrita."
                    } else {
                        "Confira a escrita ou apague a busca para ver a lista inteira."
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        bottom = PontoCafeSpacing.lg,
                    ),
                ) {
                    items(filtrados, key = { it.id }) { pessoa ->
                        CollaboratorRow(
                            colaborador = pessoa,
                            onClick = { viewModel.selecionarColaborador(pessoa) },
                        )
                    }
                }
            }
        }

        state.erro?.let { erro ->
            PcStateBanner(
                title = "Não foi possível continuar",
                supportingText = erro,
                tone = PontoCafeTone.DANGER,
                modifier = Modifier.padding(bottom = PontoCafeSpacing.sm),
            )
        }
    }
}

@Composable
private fun CollaboratorRow(colaborador: Colaborador, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "Selecionar ${colaborador.nome}"
            },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = PontoCafeSpacing.md, vertical = PontoCafeSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            InitialAvatar(name = colaborador.nome, avatarSize = 44.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    colaborador.nome,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                val detalhe = listOfNotNull(
                    colaborador.setor?.takeIf { it.isNotBlank() },
                    colaborador.turno?.takeIf { it.isNotBlank() }?.let { "Turno $it" },
                ).joinToString(" · ")
                if (detalhe.isNotBlank()) {
                    Text(
                        detalhe,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// endregion

// region Passo 2 — digitar o código

@Composable
private fun AccessCodeStep(
    viewModel: PontoCafeViewModel,
    compactHeight: Boolean,
) {
    val state = viewModel.state
    val colaborador = state.selecionado ?: return
    val retorno = state.acaoEsperada == "RETORNO"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = PontoCafeSpacing.md),
        verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = viewModel::voltarParaLista, enabled = !state.registrando) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Voltar para a lista de nomes")
            }
            InitialAvatar(name = colaborador.nome, avatarSize = 40.dp)
            Spacer(Modifier.size(PontoCafeSpacing.xs))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    colaborador.nome,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (retorno) "Registrando o retorno" else "Registrando a saída para o café",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Quem já saiu vê o relógio da própria pausa enquanto digita: é a
        // resposta à única pergunta que essa pessoa tem neste momento.
        state.pausaAberta?.let { pausa ->
            OpenPauseCountdown(
                inicioLocal = pausa.inicioLocal,
                retornoAteLocal = pausa.retornoAteLocal,
                limiteSegundos = pausa.limiteSegundos,
                carenciaSegundos = pausa.carenciaSegundos,
                tempoDecorridoInicialSegundos = pausa.tempoDecorridoSegundos,
            )
        }

        Text(
            if (retorno) {
                "Digite o MESMO código que usou para sair."
            } else {
                "Digite o código de ${AccessCode.LENGTH} caracteres entregue pelo Supervisor."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        AccessCodeBoxes(
            codigo = state.codigo,
            error = state.erro != null,
        )

        if (!compactHeight) Spacer(Modifier.height(PontoCafeSpacing.xxs))

        state.erro?.let { erro ->
            PcStateBanner(
                title = "Código não aceito",
                supportingText = erro,
                tone = PontoCafeTone.DANGER,
            )
        }

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.BottomCenter) {
            AccessCodeKeypad(
                enabled = !state.registrando,
                onDigit = viewModel::acrescentarDigito,
                onBackspace = viewModel::apagarUltimoDigito,
                compactHeight = compactHeight,
            )
        }

        PcPrimaryButton(
            text = if (retorno) "Registrar retorno" else "Liberar e sair para o café",
            onClick = viewModel::registrar,
            enabled = state.codigoCompleto && !state.registrando,
            loading = state.registrando,
            icon = Icons.Default.Coffee,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = PontoCafeSpacing.md),
        )
    }
}

/**
 * Seis caixas em vez de um campo de texto.
 *
 * Num quiosque a pessoa digita de pé, muitas vezes sem óculos: o que ela precisa
 * de ver num relance é quantos caracteres já entraram e qual falta. Um
 * OutlinedTextField comum não responde a essa pergunta.
 */
@Composable
private fun AccessCodeBoxes(codigo: String, error: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "Código: ${codigo.length} de ${AccessCode.LENGTH} caracteres digitados"
                liveRegion = LiveRegionMode.Polite
            },
        horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
    ) {
        repeat(AccessCode.LENGTH) { index ->
            val char = codigo.getOrNull(index)
            val preenchido = char != null
            val borda = when {
                error -> MaterialTheme.colorScheme.error
                preenchido -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outlineVariant
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerLow,
                        RoundedCornerShape(14.dp),
                    )
                    .border(if (preenchido) 2.dp else 1.dp, borda, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    char?.toString() ?: "",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                )
            }
        }
    }
}

/**
 * Teclado próprio, restrito ao alfabeto do código.
 *
 * O teclado do sistema ofereceria I, L, O e U — símbolos que este alfabeto
 * exclui de propósito para não se confundirem com 1 e 0. Mostrá-los seria
 * convidar ao erro; aqui a tecla que não existe simplesmente não aparece.
 */
@Composable
private fun AccessCodeKeypad(
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    compactHeight: Boolean,
) {
    val teclas = AccessCode.ALPHABET.toList()
    val porLinha = 8
    val alturaTecla = if (compactHeight) 40.dp else 48.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 640.dp),
        verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xxs),
    ) {
        teclas.chunked(porLinha).forEach { linha ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xxs),
            ) {
                linha.forEach { tecla ->
                    KeypadKey(
                        label = tecla.toString(),
                        enabled = enabled,
                        onClick = { onDigit(tecla) },
                        modifier = Modifier.weight(1f).height(alturaTecla),
                    )
                }
                repeat(porLinha - linha.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        KeypadKey(
            label = "Apagar",
            enabled = enabled,
            onClick = onBackspace,
            modifier = Modifier.fillMaxWidth().height(alturaTecla),
        )
    }
}

@Composable
private fun KeypadKey(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/**
 * Relógio da pausa em curso.
 *
 * Durante a carência mostra quanto falta para o limite COMEÇAR — é a diferença
 * que o novo fluxo introduziu, e escondê-la faria a pessoa achar que perdeu um
 * minuto do café. Depois disso, mostra o que resta dos 15 minutos.
 */
@Composable
private fun OpenPauseCountdown(
    inicioLocal: String,
    retornoAteLocal: String,
    limiteSegundos: Int,
    carenciaSegundos: Int,
    tempoDecorridoInicialSegundos: Int,
) {
    var decorrido by remember(tempoDecorridoInicialSegundos) {
        mutableIntStateOf(tempoDecorridoInicialSegundos)
    }
    LaunchedEffect(tempoDecorridoInicialSegundos) {
        while (true) {
            delay(1_000L)
            decorrido += 1
        }
    }

    val emCarencia = decorrido < carenciaSegundos
    val restante = if (emCarencia) {
        carenciaSegundos - decorrido
    } else {
        (carenciaSegundos + limiteSegundos) - decorrido
    }
    val excedeu = !emCarencia && restante < 0
    val tone = when {
        excedeu -> PontoCafeTone.DANGER
        emCarencia -> PontoCafeTone.INFO
        restante <= 60 -> PontoCafeTone.WARNING
        else -> PontoCafeTone.SUCCESS
    }
    val relogio = "%02d:%02d".format(
        kotlin.math.abs(restante) / 60,
        kotlin.math.abs(restante) % 60,
    )

    PcStateBanner(
        title = when {
            excedeu -> "Limite excedido há $relogio"
            emCarencia -> "O tempo de café começa em $relogio"
            else -> "Faltam $relogio para o retorno"
        },
        supportingText = "Saída às $inicioLocal · retorno até $retornoAteLocal",
        tone = tone,
    )
}

// endregion

// region Passo 3 — comprovante

@Composable
private fun ReceiptStep(viewModel: PontoCafeViewModel) {
    val comprovante = viewModel.state.comprovante ?: return

    // O comprovante fecha sozinho para o quiosque não ficar preso no ecrã de uma
    // pessoa que já saiu andando. Doze segundos são suficientes para ler o
    // horário e conferir o nome.
    LaunchedEffect(comprovante) {
        delay(RECEIPT_AUTO_DISMISS_MILLIS)
        viewModel.concluirComprovante()
    }

    val saida = comprovante.tipo == TipoComprovantePonto.INICIO
    val tone = when {
        comprovante.excedeuLimite -> PontoCafeTone.WARNING
        else -> PontoCafeTone.SUCCESS
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(PontoCafeSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md, Alignment.CenterVertically),
    ) {
        Surface(
            modifier = Modifier.size(88.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (comprovante.excedeuLimite) Icons.Default.Warning else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(44.dp),
                )
            }
        }

        Text(
            if (saida) "Bom café, ${comprovante.nome.substringBefore(' ')}!" else "Bem-vindo de volta!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        )
        Text(
            comprovante.nome,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        PcSectionSurface(modifier = Modifier.widthIn(max = 520.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                ReceiptLine(
                    icon = Icons.Default.Timer,
                    label = if (saida) "Saída registrada" else "Retorno registrado",
                    value = comprovante.horarioRegistrado,
                )
                if (saida) {
                    comprovante.contagemComecaAs?.let {
                        ReceiptLine(
                            icon = Icons.Default.Timer,
                            label = "Seu tempo começa a contar",
                            value = it,
                        )
                    }
                    comprovante.retornoAte?.let {
                        ReceiptLine(
                            icon = Icons.Default.Timer,
                            label = "Volte até",
                            value = it,
                        )
                    }
                } else {
                    comprovante.duracaoSegundos?.let {
                        ReceiptLine(
                            icon = Icons.Default.Timer,
                            label = "Tempo fora",
                            value = viewModel.formatarTempo(it),
                        )
                    }
                    comprovante.tempoContadoSegundos?.let {
                        ReceiptLine(
                            icon = Icons.Default.Timer,
                            label = "Tempo contado (sem a tolerância)",
                            value = viewModel.formatarTempo(it),
                        )
                    }
                }
                ReceiptLine(
                    icon = Icons.Default.Badge,
                    label = "Limite do café",
                    value = viewModel.formatarTempo(comprovante.limiteSegundos) +
                        if (comprovante.carenciaSegundos > 0) {
                            " + ${comprovante.carenciaSegundos / 60} min de tolerância"
                        } else {
                            ""
                        },
                )
            }
        }

        if (comprovante.excedeuLimite) {
            PcStateBanner(
                title = "Retorno acima do limite",
                supportingText = "O tempo de café foi ultrapassado. O Supervisor recebeu este registro.",
                tone = tone,
                modifier = Modifier.widthIn(max = 520.dp),
            )
        }

        if (comprovante.pendenteSincronizacao) {
            PcStateBanner(
                title = "Registrado sem conexão",
                supportingText = "Este registro está guardado neste aparelho e será enviado ao servidor assim que a rede voltar. O código será validado nesse momento.",
                tone = PontoCafeTone.INFO,
                modifier = Modifier.widthIn(max = 520.dp),
            )
        }

        if (comprovante.foraHorario) {
            PcStateBanner(
                title = "Fora do horário habitual",
                supportingText = "A pausa foi liberada pelo código, mas ficou marcada como fora da janela de café.",
                tone = PontoCafeTone.INFO,
                modifier = Modifier.widthIn(max = 520.dp),
            )
        }

        PcPrimaryButton(
            text = "Concluir",
            onClick = viewModel::concluirComprovante,
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
        )
    }
}

@Composable
private fun ReceiptLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// endregion

@Composable
private fun RestrictedAccessDialog(
    target: RestrictedAreaRequest,
    pin: String,
    loading: Boolean,
    error: String?,
    onPinChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onLogin: () -> Unit,
    onConfirm: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val destinationName = when (target) {
        RestrictedAreaRequest.ADMIN -> "Administrador"
        RestrictedAreaRequest.SUPERVISOR -> "Supervisor"
        RestrictedAreaRequest.LOGIN -> "Início de sessão"
    }
    val instruction = when (target) {
        RestrictedAreaRequest.ADMIN -> "Use o PIN deste dispositivo para abrir a sessão de Administrador já salva."
        RestrictedAreaRequest.SUPERVISOR -> "Use o PIN deste dispositivo para abrir a sessão de Supervisor já salva."
        RestrictedAreaRequest.LOGIN -> "Use o PIN deste dispositivo para sair do modo Ponto e abrir o início de sessão."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        },
        title = { Text("Acesso restrito · $destinationName") },
        text = {
            PcDialogBody {
                Text(
                    instruction,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Se este aparelho não tiver PIN configurado, use Entrar com conta para autenticar um Administrador ou Supervisor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = onPinChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("PIN do dispositivo") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        if (!loading && pin.length in 4..12) onConfirm()
                    }),
                    isError = error != null,
                    supportingText = { Text(error ?: "4 a 12 números") },
                    enabled = !loading,
                )
            }
        },
        confirmButton = {
            PcPrimaryButton(
                text = "Desbloquear",
                enabled = !loading && pin.length in 4..12,
                onClick = onConfirm,
                loading = loading,
            )
        },
        dismissButton = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
            ) {
                TextButton(onClick = onDismiss, enabled = !loading) {
                    Text("Cancelar")
                }
                TextButton(onClick = onLogin, enabled = !loading) {
                    Text("Entrar com conta")
                }
            }
        },
    )
}
