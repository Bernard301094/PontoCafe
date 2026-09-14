package com.pontocafe.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.pontocafe.app.BuildConfig
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pontocafe.app.PontoCafeUiState
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val KIOSK_ZONE: ZoneId = ZoneId.of("America/Fortaleza")
private const val RECEIPT_AUTO_DISMISS_MILLIS = 12_000L

internal enum class RestrictedAreaRequest { SUPERVISOR, ADMIN, LOGIN }

/**
 * Tudo o que os passos do quiosque pedem ao ViewModel, e nada mais.
 *
 * Os passos recebiam o ViewModel inteiro e liam dele o estado. Isso prendia cada
 * tela a um objeto que só existe com repositório, cofre de credencial e fila
 * offline por trás -- e por isso nenhuma delas podia ser desenhada fora do
 * aparelho. Com o estado como valor e as ações como funções, a mesma tela se
 * renderiza num teste com dados de exemplo, que é o que permite ver o totem
 * antes de gerar um APK.
 */
internal class KioskActions(
    val carregarColaboradores: () -> Unit,
    val selecionarColaborador: (Colaborador) -> Unit,
    val voltarParaLista: () -> Unit,
    val acrescentarDigito: (Char) -> Unit,
    val apagarUltimoDigito: () -> Unit,
    val registrar: () -> Unit,
    val concluirComprovante: () -> Unit,
    val abrirLeitorQr: () -> Unit,
    val fecharLeitorQr: () -> Unit,
    val lerQr: (String) -> Unit,
) {
    companion object {
        fun from(viewModel: PontoCafeViewModel) = KioskActions(
            carregarColaboradores = { viewModel.carregarColaboradores(force = false) },
            selecionarColaborador = viewModel::selecionarColaborador,
            voltarParaLista = viewModel::voltarParaLista,
            acrescentarDigito = viewModel::acrescentarDigito,
            apagarUltimoDigito = viewModel::apagarUltimoDigito,
            registrar = { viewModel.registrar() },
            concluirComprovante = viewModel::concluirComprovante,
            abrirLeitorQr = viewModel::abrirLeitorQr,
            fecharLeitorQr = viewModel::fecharLeitorQr,
            lerQr = viewModel::lerQr,
        )

        /** Ações que não fazem nada: para desenhar as telas em testes. */
        val Inertes = KioskActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}

internal fun formatarTempoKiosk(segundos: Int): String =
    "%02d:%02d".format(segundos / 60, segundos % 60)

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
    val actions = remember(viewModel) { KioskActions.from(viewModel) }

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
        // Capturado aqui: dentro do Column o receptor implícito passa a ser o
        // ColumnScope e maxWidth deixa de estar acessível.
        val larga = maxWidth >= 840.dp && !compactHeight

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

            // O quiosque lia só a ALTURA. Num tablet de parede de 1280dp o
            // fluxo desenhava uma coluna estreita de telefone com metade do
            // ecrã vazio — e é o aparelho que fica ligado o dia inteiro.
            // A partir da largura de tablet o passo fica à esquerda e o painel
            // operacional à direita.
            if (larga) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    KioskStepContent(
                        state = state,
                        actions = actions,
                        compactHeight = compactHeight,
                        onInteracao = { wakeTick += 1 },
                        modifier = Modifier
                            .weight(.6f)
                            .fillMaxHeight(),
                    )
                    KioskOperationalPanel(
                        state = state,
                        modifier = Modifier
                            .weight(.4f)
                            .fillMaxHeight(),
                    )
                }
            } else {
                KioskStepContent(
                    state = state,
                    actions = actions,
                    compactHeight = compactHeight,
                    onInteracao = { wakeTick += 1 },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
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

/** Os três passos, extraídos para poderem viver sozinhos ou dentro do split. */
@Composable
internal fun KioskStepContent(
    state: PontoCafeUiState,
    actions: KioskActions,
    compactHeight: Boolean,
    onInteracao: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Como o totem do painel: a linha de estado do aparelho, e o passo dentro
    // de uma tarjeta branca, centrada no fundo cinzento. Rola quando o ecrã é
    // baixo -- o teclado de 32 teclas não cabe num telemóvel deitado.
    BoxWithConstraints(modifier = modifier) {
        val alturaDisponivel = maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = alturaDisponivel)
                .padding(horizontal = 16.dp, vertical = if (compactHeight) 12.dp else 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        ) {
            val (textoEstado, corEstado) = when {
                state.modoOffline && state.eventosPendentes > 0 ->
                    "Sem conexão · ${state.eventosPendentes} registro(s) na fila" to Painel.amber400
                state.modoOffline -> "Sem conexão · os registros ficam na fila" to Painel.amber400
                state.eventosPendentes > 0 ->
                    "${state.eventosPendentes} registro(s) aguardando envio" to Painel.amber400
                else -> "Aparelho conectado" to Painel.emerald500
            }
            PainelLinhaEstado(
                texto = textoEstado,
                cor = corEstado,
                modifier = Modifier.widthIn(max = 520.dp),
            )

            PainelCartao(modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
                AnimatedContent(
                    targetState = state.passo,
                    transitionSpec = {
                        fadeIn(tween(PontoCafeMotion.Standard)) togetherWith
                            fadeOut(tween(PontoCafeMotion.Quick))
                    },
                    label = "ponto-step",
                ) { passo ->
                    when (passo) {
                        PontoStep.ESCOLHER_PESSOA -> CollaboratorPickerStep(
                            state = state,
                            actions = actions,
                            compactHeight = compactHeight,
                            onInteracao = onInteracao,
                        )

                        PontoStep.DIGITAR_CODIGO -> AccessCodeStep(
                            state = state,
                            actions = actions,
                            compactHeight = compactHeight,
                        )

                        PontoStep.COMPROVANTE -> ReceiptStep(state = state, actions = actions)
                    }
                }
            }
        }
    }
}

/**
 * Painel direito do quiosque de parede.
 *
 * Mostra só o que é da própria operação de quem está em frente ao aparelho:
 * hora, data, o passo em que está e o nome que ele mesmo escolheu. Nada de
 * terceiros — nem quem saiu, nem quem voltou, nem a que horas. O quiosque fica
 * num corredor, e o resto do sistema foi construído para que ele não aprenda
 * quem tomou café: a lista já vem cortada do servidor por essa razão. Um mural
 * de pausas alheias aqui desfaria isso de uma vez.
 *
 * O relógio é a única animação da tela. Acorda uma vez por segundo, alinhado à
 * viragem do segundo em vez de um `delay(1000)` que iria derivando, e morre com
 * a composição — num aparelho ligado 24 h isso é a diferença entre um timer e
 * um vazamento.
 */
@Composable
internal fun KioskOperationalPanel(
    state: PontoCafeUiState,
    modifier: Modifier = Modifier,
) {
    var agora by remember { mutableStateOf(ZonedDateTime.now(KIOSK_ZONE)) }
    // Preso ao ciclo de vida. O quiosque fica quase sempre em primeiro plano,
    // mas quando alguém entra na área restrita este relógio continuava a acordar
    // de segundo a segundo por trás, sem ninguém para o ler.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val instante = ZonedDateTime.now(KIOSK_ZONE)
                agora = instante
                delay(1_000L - (instante.nano / 1_000_000L))
            }
        }
    }

    // A coluna da direita do totem de parede, como uma barra lateral do painel:
    // branca, separada do passo por uma borda stone-200, com o relógio em mono.
    Surface(
        modifier = modifier,
        color = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawLine(
                        color = Painel.stone200,
                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                        end = androidx.compose.ui.geometry.Offset(0f, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
                .padding(PontoCafeSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xxs)) {
                Text(
                    agora.format(DateTimeFormatter.ofPattern("HH:mm", Locale("pt", "BR"))),
                    style = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 56.sp),
                    color = Painel.coffee900,
                )
                Text(
                    agora.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale("pt", "BR")))
                        .replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                    color = Painel.stone500,
                )
            }

            HorizontalDivider(color = Painel.stone200)

            val (titulo, apoio) = when (state.passo) {
                PontoStep.ESCOLHER_PESSOA ->
                    "Toque no seu nome" to
                        "Depois vem o código de ${AccessCode.LENGTH} caracteres que o Supervisor entregou."

                PontoStep.DIGITAR_CODIGO -> if (state.acaoEsperada == "RETORNO") {
                    "Digite o mesmo código" to "É o código que você usou para sair. Ele não expira para o retorno."
                } else {
                    "Digite o código" to "Ele vale para sair até o fim da janela de café do período."
                }

                PontoStep.COMPROVANTE ->
                    "Registro concluído" to "O comprovante fecha sozinho e volta para a lista de nomes."
            }

            Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xxs)) {
                Text(
                    titulo,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Painel.coffee950,
                )
                Text(
                    apoio,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Painel.stone500,
                )
            }

            // O nome só aparece depois de a própria pessoa se ter escolhido —
            // é dela, e some no momento em que o comprovante fecha.
            state.selecionado?.let { pessoa ->
                Surface(
                    shape = Painel.canto2xl,
                    color = Painel.stone50,
                    border = PainelBordaSuave,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(PontoCafeSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                    ) {
                        InitialAvatar(name = pessoa.nome, avatarSize = 48.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                pessoa.nome,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                if (state.acaoEsperada == "RETORNO") "Registrando o retorno" else "Registrando a saída",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            if (state.modoOffline) {
                PainelAvisoTom(
                    title = "Sem conexão",
                    supportingText = if (state.eventosPendentes > 0) {
                        "${state.eventosPendentes} registro(s) guardados neste aparelho, à espera da rede."
                    } else {
                        "Os registros ficam guardados no aparelho e sobem quando a rede voltar."
                    },
                    tone = PontoCafeTone.WARNING,
                )
            }
        }
    }
}

@Composable
internal fun KioskTopBar(
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

    // O cabeçalho do painel. O estado da ligação saiu daqui para a linha por
    // cima da tarjeta, que é onde o painel o põe; aqui fica só o ponto colorido
    // ao lado do relógio.
    PainelCabecalho(
        versao = BuildConfig.VERSION_NAME,
        subtitulo = "Fuso horário: ${KIOSK_ZONE.id}",
        relogio = relogio,
        sincronizado = !offline && pendingEvents == 0,
    ) {
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
                tint = Painel.stone400,
            )
        }
    }
}

// region Passo 1 — escolher a pessoa

/**
 * O primeiro passo, com a cara do totem do painel: selo âmbar, título centrado,
 * o campo que abre a lista, e só depois o que é do app -- o atalho do QR, a
 * contagem de quem está apto e a saída para quem não se encontra.
 */
@Composable
private fun CollaboratorPickerStep(
    state: PontoCafeUiState,
    actions: KioskActions,
    compactHeight: Boolean,
    onInteracao: () -> Unit,
) {
    var seletorAberto by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { actions.carregarColaboradores() }

    if (seletorAberto) {
        PcCollaboratorPickerSheet(
            pessoas = state.colaboradores,
            titulo = "Toque no seu nome",
            onDismiss = { seletorAberto = false },
            onEscolher = { pessoa ->
                seletorAberto = false
                actions.selecionarColaborador(pessoa)
            },
            onInteracao = { onInteracao() },
            vazioTitulo = "Nenhum colaborador disponível",
            vazioTexto = "Ou todos já tomaram café neste período, ou ninguém foi cadastrado ainda. " +
                "Fale com o Supervisor.",
        )
    }

    // A folha da câmara vive neste passo porque é aqui que o QR faz sentido:
    // ele traz a pessoa E o código, e por isso salta directamente para o
    // comprovante sem passar pelo teclado.
    if (state.lendoQr) {
        QrScannerSheet(
            onLeitura = { conteudo -> actions.lerQr(conteudo) },
            onFechar = { actions.fecharLeitorQr() },
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compactHeight) 10.dp else 14.dp),
    ) {
        PainelSeloIcone(Icons.Outlined.Groups)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PainelEtapa("PASSO 1 DE 2")
            PainelTitulo("Quem vai ao café?", modifier = Modifier.semantics { heading() })
            PainelSubtitulo("Toque no seu nome e depois digite o código de ${AccessCode.LENGTH} caracteres.")
        }

        when {
            state.carregandoColaboradores && state.colaboradores.isEmpty() -> {
                CircularProgressIndicator(color = Painel.coffee900, strokeWidth = 3.dp)
            }

            else -> {
                // Seletor fechado em vez da lista inteira: a grade de noventa e
                // seis nomes obrigava a rolar antes de qualquer coisa e enchia a
                // tela de gente que não é você. A busca vive dentro da folha,
                // onde há uma lista para ela filtrar.
                PcCollaboratorPickerField(
                    selecionado = null,
                    placeholder = "Toque aqui para se encontrar",
                    onClick = {
                        onInteracao()
                        seletorAberto = true
                    },
                    grande = true,
                )

                // O atalho da câmara só aparece onde foi liberado. Um botão que
                // existisse sempre e falhasse com "não liberado" ensinaria a
                // ignorá-lo; aqui a ausência é a própria resposta.
                if (state.qrHabilitado) {
                    PainelBotaoSecundario(
                        texto = "Ler meu QR",
                        onClick = {
                            onInteracao()
                            actions.abrirLeitorQr()
                        },
                        icon = PontoQrIcon,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Default.Coffee,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Painel.amber600,
                    )
                    Text(
                        if (state.colaboradores.isEmpty()) {
                            "Ninguém disponível para café neste período"
                        } else {
                            "${state.colaboradores.size} pessoas aptas para café neste período"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = Painel.stone500,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        state.erro?.let { erro ->
            PainelAvisoTom(
                title = "Não foi possível continuar",
                supportingText = erro,
                tone = PontoCafeTone.DANGER,
            )
        }

        // Saída para quem não se encontra na lista, no rodapé da tarjeta: evita
        // que a pessoa fique parada no totem sem rumo.
        HorizontalDivider(color = Painel.stone200, modifier = Modifier.padding(top = 4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Default.Badge,
                contentDescription = null,
                modifier = Modifier.size(18.dp).padding(top = 1.dp),
                tint = Painel.stone400,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Não encontrou seu nome?",
                    style = MaterialTheme.typography.labelLarge,
                    color = Painel.stone700,
                )
                Text(
                    "Peça ao Supervisor para conferir sua escala ou liberar um intervalo avulso.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Painel.stone500,
                )
            }
        }
    }
}

// endregion

// region Passo 2 — digitar o código

@Composable
private fun AccessCodeStep(
    state: PontoCafeUiState,
    actions: KioskActions,
    compactHeight: Boolean,
) {
    val colaborador = state.selecionado ?: return
    val retorno = state.acaoEsperada == "RETORNO"

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compactHeight) 10.dp else 12.dp),
    ) {
        // "← Trocar de pessoa", pequeno e discreto no canto, como no painel.
        Row(
            modifier = Modifier
                .clickable(enabled = !state.registrando, onClick = actions.voltarParaLista)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = "Voltar para a lista de nomes",
                modifier = Modifier.size(14.dp),
                tint = Painel.stone400,
            )
            Text(
                "Trocar de pessoa",
                style = MaterialTheme.typography.labelMedium,
                color = Painel.stone400,
            )
        }

        // O nome é o título do passo: confirma a identidade escolhida antes de
        // a pessoa gastar o código.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PainelEtapa("PASSO 2 DE 2")
            PainelTitulo(
                colaborador.nome,
                grande = false,
                modifier = Modifier.semantics { heading() },
            )
            PainelSubtitulo(
                if (retorno) {
                    "Digite o mesmo código que usou para sair."
                } else {
                    "Digite os ${AccessCode.LENGTH} caracteres entregues pelo Supervisor."
                },
            )
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

        AccessCodeBoxes(
            codigo = state.codigo,
            error = state.erro != null,
            compactHeight = compactHeight,
        )

        // O prazo vale só para a SAÍDA, e é o fim da janela de café do período a
        // que o código pertence -- não uma contagem a partir da emissão. Quem já
        // saiu precisa da garantia oposta: que não perde o código enquanto toma café.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            Icon(
                Icons.Default.Schedule,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = Painel.stone400,
            )
            Text(
                if (retorno) {
                    "Este código não expira para o retorno."
                } else {
                    "Vale para sair até o fim da janela de café do seu período."
                },
                style = MaterialTheme.typography.labelSmall,
                color = Painel.stone500,
                textAlign = TextAlign.Center,
            )
        }

        state.erro?.let { erro ->
            PainelAvisoTom(
                title = "Código não aceito",
                supportingText = erro,
                tone = PontoCafeTone.DANGER,
            )
        }

        AccessCodeKeypad(
            enabled = !state.registrando,
            onDigit = actions.acrescentarDigito,
            onBackspace = actions.apagarUltimoDigito,
            compactHeight = compactHeight,
        )

        PainelBotaoPrimario(
            texto = if (retorno) "Registrar retorno" else "Liberar e sair para o café",
            onClick = { actions.registrar() },
            enabled = state.codigoCompleto && !state.registrando,
            loading = state.registrando,
            icon = Icons.Default.Coffee,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Seis caixas em vez de um campo de texto.
 *
 * Num quiosque a pessoa digita de pé, muitas vezes sem óculos: o que ela precisa
 * de ver num relance é quantos caracteres já entraram e qual falta. As caixas
 * são as do painel -- stone-50 com borda fina, a próxima em âmbar, e a letra em
 * JetBrains Mono.
 */
@Composable
private fun AccessCodeBoxes(codigo: String, error: Boolean, compactHeight: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "Código: ${codigo.length} de ${AccessCode.LENGTH} caracteres digitados"
                liveRegion = LiveRegionMode.Polite
            },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(AccessCode.LENGTH) { index ->
            val char = codigo.getOrNull(index)
            val preenchido = char != null
            // A caixa que recebe o próximo caractere fica âmbar, como o cursor do
            // painel; a borda vermelha só aparece para dizer uma coisa -- erro.
            val proxima = !error && index == codigo.length
            val (fundo, borda, largura) = when {
                error -> Triple(Painel.red50, Color(0xFFF87171), 2.dp)
                proxima -> Triple(Painel.amber50, Painel.amber300, 2.dp)
                else -> Triple(Painel.stone50, Painel.stone200, 1.dp)
            }
            // O dígito que entra salta de 0,7 para 1. É o único retorno visual
            // de que a tecla pegou: o dedo tapa a caixa no instante do toque, e
            // sem o salto a pessoa só descobre ao levantar a mão.
            val pop = rememberPopOnChange(gatilho = char, ativo = preenchido)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(if (compactHeight) 50.dp else 58.dp)
                    .graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                    }
                    .background(fundo, Painel.cantoXl)
                    .border(largura, borda, Painel.cantoXl),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    char?.toString() ?: "",
                    style = TextStyle(
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                    ),
                    color = if (error) Color(0xFFB91C1C) else Painel.coffee950,
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
    val alturaTecla = if (compactHeight) 38.dp else 44.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 640.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        teclas.chunked(porLinha).forEach { linha ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
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
        PainelBotaoSuave(
            texto = "Apagar",
            onClick = onBackspace,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
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
    val interactionSource = remember { MutableInteractionSource() }
    // Tecla comprime 0,92 -- mais que os 0,96 de um botão comum. Aqui a
    // compressão é o recibo do toque, e o dedo tapa o número enquanto o preme.
    val escala = rememberPontoPressScale(interactionSource, PontoPressScale.Key)
    Box(
        modifier = modifier
            .pontoPressScale { escala }
            // Tecla do painel: branca, borda stone-200, canto de 12dp.
            .shadow(1.dp, Painel.cantoXl)
            .background(Color.White, Painel.cantoXl)
            .border(1.dp, Painel.stone200, Painel.cantoXl)
            .clickable(
                enabled = enabled,
                onClick = onClick,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 16.sp),
            color = if (enabled) Painel.coffee900 else Painel.stone400,
        )
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

    PainelAvisoTom(
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
private fun ReceiptStep(state: PontoCafeUiState, actions: KioskActions) {
    val comprovante = state.comprovante ?: return

    // O comprovante fecha sozinho para o quiosque não ficar preso no ecrã de uma
    // pessoa que já saiu andando. Doze segundos são suficientes para ler o
    // horário e conferir o nome.
    //
    // A contagem é visível, como no painel: quem está atrás na fila vê que o
    // totem se libera sozinho e não fica a perguntar se pode tocar.
    var segundosRestantes by remember(comprovante) {
        mutableIntStateOf((RECEIPT_AUTO_DISMISS_MILLIS / 1_000L).toInt())
    }
    LaunchedEffect(comprovante) {
        while (segundosRestantes > 0) {
            delay(1_000L)
            segundosRestantes--
        }
        actions.concluirComprovante()
    }

    val saida = comprovante.tipo == TipoComprovantePonto.INICIO
    val tone = when {
        comprovante.excedeuLimite -> PontoCafeTone.WARNING
        else -> PontoCafeTone.SUCCESS
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // O momento em que o produto inteiro diz "deu certo": o selo salta e uma
        // onda sai dele e se apaga -- uma vez só, sem laço nenhum a correr depois.
        val marca = rememberPopOnChange(gatilho = comprovante, de = 0.6f)
        val onda = remember(comprovante) { Animatable(0f) }
        LaunchedEffect(comprovante) { onda.animateTo(1f, tween(PontoCafeMotion.Slow)) }
        val corOnda = if (comprovante.excedeuLimite) Painel.red600 else Painel.amber400

        val icone = if (comprovante.excedeuLimite) Icons.Default.Warning else Icons.Default.Coffee
        PainelSeloIcone(
            icon = icone,
            tom = when {
                comprovante.excedeuLimite -> PainelTom.VERMELHO
                saida -> PainelTom.AMBAR
                else -> PainelTom.VERDE
            },
            tamanho = 72.dp,
            modifier = Modifier
                .drawBehind {
                    val progresso = onda.value
                    if (progresso < 1f) {
                        drawCircle(
                            color = corOnda,
                            radius = size.minDimension / 2f * (1f + progresso * 0.7f),
                            alpha = (1f - progresso) * 0.45f,
                        )
                    }
                }
                .graphicsLayer {
                    scaleX = marca.value
                    scaleY = marca.value
                },
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PainelTitulo(
                if (saida) "Bom café, ${comprovante.nome.substringBefore(' ')}!" else "Bem-vindo de volta!",
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
            PainelSubtitulo(
                if (saida) {
                    "Saída registrada às ${comprovante.horarioRegistrado}" +
                        (comprovante.retornoAte?.let { ". Volte até $it." } ?: ".")
                } else {
                    "Retorno registrado às ${comprovante.horarioRegistrado}."
                },
            )
        }

        // O recibo, numa caixa stone-50: o que o painel diz numa frase, o totem
        // detalha linha a linha, porque é o único comprovante que a pessoa leva.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Painel.stone50, Painel.canto2xl)
                .border(1.dp, Painel.stone200, Painel.canto2xl)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReceiptLine(
                label = if (saida) "Saída registrada" else "Retorno registrado",
                value = comprovante.horarioRegistrado,
            )
            if (saida) {
                comprovante.contagemComecaAs?.let {
                    ReceiptLine(label = "Seu tempo começa a contar", value = it)
                }
                comprovante.retornoAte?.let {
                    ReceiptLine(label = "Volte até", value = it)
                }
            } else {
                comprovante.duracaoSegundos?.let {
                    ReceiptLine(label = "Tempo fora", value = formatarTempoKiosk(it))
                }
                comprovante.tempoContadoSegundos?.let {
                    ReceiptLine(label = "Tempo contado (sem a tolerância)", value = formatarTempoKiosk(it))
                }
            }
            ReceiptLine(
                label = "Limite do café",
                value = formatarTempoKiosk(comprovante.limiteSegundos) +
                    if (comprovante.carenciaSegundos > 0) {
                        " + ${comprovante.carenciaSegundos / 60} min"
                    } else {
                        ""
                    },
            )
            ReceiptLine(
                label = "Estado",
                value = if (comprovante.pendenteSincronizacao) "Na fila" else "Autenticado",
            )
        }

        if (comprovante.excedeuLimite) {
            PainelAvisoTom(
                title = "Retorno acima do limite",
                supportingText = "O tempo de café foi ultrapassado. O Supervisor recebeu este registro.",
                tone = tone,
            )
        }

        if (comprovante.pendenteSincronizacao) {
            PainelAvisoTom(
                title = "Registrado sem conexão",
                supportingText = "Este registro está guardado neste aparelho e será enviado ao servidor assim que a rede voltar. O código será validado nesse momento.",
                tone = PontoCafeTone.INFO,
            )
        }

        if (comprovante.foraHorario) {
            PainelAvisoTom(
                title = "Fora do horário habitual",
                supportingText = "A pausa foi liberada pelo código, mas ficou marcada como fora da janela de café.",
                tone = PontoCafeTone.INFO,
            )
        }

        PainelBotaoPrimario(
            texto = "Liberar para o próximo",
            onClick = actions.concluirComprovante,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            "Este totem se libera sozinho em ${segundosRestantes}s.",
            style = MaterialTheme.typography.labelSmall,
            color = Painel.stone400,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ReceiptLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = Painel.stone500,
        )
        Text(
            value,
            style = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 14.sp),
            color = Painel.coffee900,
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
                    // O PIN recusado tremia só no texto de erro abaixo. O campo
                    // em si não dava sinal nenhum, e num quiosque quem erra o
                    // PIN costuma estar a olhar para os dedos, não para o aviso.
                    modifier = Modifier
                        .fillMaxWidth()
                        .shakeOnChange(error),
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

