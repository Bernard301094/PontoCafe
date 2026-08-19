package com.pontocafe.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.pontocafe.app.AdminReliabilityViewModel
import com.pontocafe.app.AdminViewModel
import com.pontocafe.app.camera.FaceCameraPreview
import com.pontocafe.app.camera.FaceObservation
import com.pontocafe.app.camera.FrameCaptureController
import com.pontocafe.app.data.BiometricCalibrationMetrics
import com.pontocafe.app.data.BiometricCalibrationMetricsApiClient
import com.pontocafe.app.data.Colaborador
import com.pontocafe.app.data.SecureAdminSessionStore
import kotlin.math.roundToInt

@Composable
fun BiometricDiagnosticsScreen(
    adminViewModel: AdminViewModel,
    viewModel: AdminReliabilityViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val state = viewModel.state
    val summary = state.biometricSummary
    val metricsRepository = remember(context) {
        BiometricCalibrationMetricsApiClient.create(
            SecureAdminSessionStore(context.applicationContext, "admin"),
        )
    }
    var metrics by remember { mutableStateOf<BiometricCalibrationMetrics?>(null) }
    var metricsError by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Colaborador?>(null) }
    var cameraOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (summary == null) viewModel.openBiometricDiagnostics()
    }
    LaunchedEffect(state.calibration) {
        runCatching { metricsRepository.summary() }
            .onSuccess {
                metrics = it
                metricsError = null
            }
            .onFailure { metricsError = "As métricas acumuladas ainda não puderam ser carregadas." }
    }

    if (cameraOpen && selected != null) {
        CalibrationCamera(
            collaborator = selected!!,
            viewModel = viewModel,
            onClose = { cameraOpen = false },
        )
        return
    }

    val candidates = adminViewModel.state.colaboradores
        .filter { it.rostoCadastrado }
        .filter {
            search.isBlank() ||
                it.nome.contains(search, true) ||
                it.setor.orEmpty().contains(search, true)
        }
        .sortedBy { it.nome.lowercase() }

    PontoCafeResponsivePage(maxContentWidth = 880.dp) { responsive ->
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
                    top = PontoCafeSpacing.lg,
                    bottom = 104.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.lg),
            ) {
                item("header") {
                    PontoCafeScreenHeader(
                        title = "Biometria",
                        eyebrow = "Precisão e governança",
                        onBack = onBack,
                    )
                }

                item("feedback") { ReliabilityFeedback(viewModel) }

                if (summary != null) {
                    item("summary-title") {
                        SectionTitle(
                            "Cobertura biométrica",
                            "Visão do cadastro facial ativo sem expor fotos ou embeddings.",
                        )
                    }

                    item("metrics") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                        ) {
                            PcMetricTile(
                                value = summary.biometriaCadastrada.toString(),
                                label = "Rostos cadastrados",
                                icon = Icons.Default.Face,
                                modifier = Modifier.weight(1f),
                            )
                            PcMetricTile(
                                value = summary.biometriaPendente.toString(),
                                label = "Pendentes",
                                icon = Icons.Default.Warning,
                                modifier = Modifier.weight(1f),
                                attention = summary.biometriaPendente > 0,
                            )
                        }
                    }

                    item("thresholds") {
                        PcKeyValueCard(
                            title = "Critérios atuais",
                            rows = listOf(
                                "Limiar de reconhecimento" to summary.limiar.toString(),
                                "Margem entre candidatos" to summary.margemMinima.toString(),
                                "Limiar contra duplicado" to summary.limiarDuplicidade.toString(),
                                "Retenção após desativação" to "${summary.retencaoDias} dias",
                            ),
                        )
                    }

                    if (summary.modelos.isNotEmpty()) {
                        item("models") {
                            PcSectionSurface {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                                ) {
                                    Text(
                                        "Modelo em uso",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    summary.modelos.forEach { model ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(model.modelo, style = MaterialTheme.typography.bodyMedium)
                                                Text(
                                                    model.versaoModelo,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            StatusPill("${model.total} rosto(s)", PontoCafeTone.NEUTRAL)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                metrics?.let { calibration ->
                    item("calibration-stat-title") {
                        SectionTitle(
                            "Precisão medida",
                            "Resultados empíricos das amostras de calibração realizadas neste ambiente.",
                        )
                    }

                    item("calibration-stat-count") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                        ) {
                            PcMetricTile(
                                value = calibration.amostras.toString(),
                                label = "Amostras",
                                icon = Icons.Default.Face,
                                modifier = Modifier.weight(1f),
                            )
                            PcMetricTile(
                                value = formatPercent(calibration.top1Accuracy),
                                label = "Top-1 accuracy",
                                icon = Icons.Default.CheckCircle,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    item("calibration-stat-rates") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                        ) {
                            PcMetricTile(
                                value = formatPercent(calibration.falseRejectRate),
                                label = "FRR · falsas rejeições",
                                icon = Icons.Default.Warning,
                                modifier = Modifier.weight(1f),
                                attention = (calibration.falseRejectRate ?: 0.0) > 0.05,
                            )
                            PcMetricTile(
                                value = formatPercent(calibration.falseAcceptRate),
                                label = "FAR · falsos aceites",
                                icon = Icons.Default.Warning,
                                modifier = Modifier.weight(1f),
                                attention = (calibration.falseAcceptRate ?: 0.0) > 0.0,
                            )
                        }
                    }

                    item("calibration-stat-note") {
                        PcStateBanner(
                            title = "Como interpretar estes números",
                            supportingText = "FRR mede rejeições indevidas; FAR mede aceitações indevidas. ${calibration.comparacoesImpostor} comparação(ões) impostoras, ${calibration.falsosAceitesImpostor} acima do limiar. ${calibration.observacao}",
                            tone = PontoCafeTone.NEUTRAL,
                        )
                    }
                }

                metricsError?.let { message ->
                    item("metrics-error") {
                        PcStateBanner(
                            title = "Métricas acumuladas indisponíveis",
                            supportingText = message,
                            tone = PontoCafeTone.WARNING,
                        )
                    }
                }

                item("calibration-title") {
                    SectionTitle(
                        "Teste de calibração",
                        "Capture uma amostra real e compare o rosto correto contra os demais templates ativos sem salvar a foto.",
                    )
                }

                item("search") {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Buscar colaborador com rosto") },
                        leadingIcon = { androidx.compose.material3.Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                    )
                }

                if (candidates.isEmpty()) {
                    item("empty") {
                        PcEmptyState(
                            title = "Nenhum colaborador encontrado",
                            supportingText = "Ajuste a busca ou cadastre uma biometria antes de executar a calibração.",
                            icon = Icons.Default.Face,
                        )
                    }
                } else {
                    items(candidates, key = { "cal-${it.id}" }) { collaborator ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                selected = collaborator
                                cameraOpen = true
                            },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            shape = MaterialTheme.shapes.large,
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(PontoCafeSpacing.md),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                            ) {
                                InitialAvatar(collaborator.nome)
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        collaborator.nome,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        listOfNotNull(collaborator.setor, collaborator.turno)
                                            .filter { it.isNotBlank() }
                                            .joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                StatusPill("Testar", PontoCafeTone.INFO)
                            }
                        }
                    }
                }

                state.calibration?.let { result ->
                    item("last-result") {
                        SectionTitle("Último resultado", "Resultado da amostra mais recente desta sessão.")
                    }
                    item("last-result-card") { CalibrationResultCard(result) }
                }

                item("retention-title") {
                    SectionTitle(
                        "Governança",
                        "A limpeza automática também é executada diariamente no backend.",
                    )
                }

                item("retention") {
                    PcSecondaryButton(
                        text = "Executar política de retenção agora",
                        onClick = viewModel::runRetentionCleanup,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.loading,
                    )
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
private fun CalibrationCamera(
    collaborator: Colaborador,
    viewModel: AdminReliabilityViewModel,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val state = viewModel.state
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = it
    }
    val captureController = remember { FrameCaptureController() }
    var observation by remember { mutableStateOf(FaceObservation()) }
    var capturePending by remember { mutableStateOf(false) }

    LaunchedEffect(state.loading) {
        if (!state.loading) capturePending = false
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (permissionGranted) {
            FaceCameraPreview(
                modifier = Modifier.fillMaxSize(),
                captureController = captureController,
                onObservation = { observation = it },
                onFrame = { frame -> viewModel.calibrate(collaborator.id, frame) },
            )
        } else {
            Column(
                Modifier.align(Alignment.Center).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                Text("A câmera é necessária para testar a biometria.", color = Color.White)
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Permitir câmera")
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(PontoCafeSpacing.md)
                .align(Alignment.TopCenter),
            color = Color.Black.copy(alpha = .72f),
            shape = RoundedCornerShape(20.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(PontoCafeSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Calibrar biometria", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(collaborator.nome, color = Color.White.copy(alpha = .78f))
                }
                OutlinedButton(onClick = onClose) { Text("Voltar") }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(PontoCafeSpacing.md)
                .align(Alignment.BottomCenter),
            color = MaterialTheme.colorScheme.surface.copy(alpha = .96f),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                Modifier.padding(PontoCafeSpacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                val hint = when {
                    observation.faceCount == 0 -> "Posicione o rosto dentro do quadro"
                    observation.faceCount > 1 -> "Deixe apenas uma pessoa visível"
                    !observation.isCentered -> "Centralize o rosto"
                    !observation.isFrontal -> "Olhe diretamente para a câmera"
                    else -> "Posição adequada para o teste"
                }
                Text(hint, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Text(
                    "Modelo ${viewModel.faceModelName}. A foto capturada existe apenas em memória durante o cálculo do embedding.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        capturePending = true
                        captureController.request()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = permissionGranted && observation.isFrontal && !state.loading && !capturePending && viewModel.faceModelReady,
                ) {
                    Text(if (state.loading || capturePending) "Processando…" else "Capturar amostra e medir")
                }
                state.calibration?.let { CalibrationResultCard(it) }
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun CalibrationResultCard(result: com.pontocafe.app.data.CalibrationResponse) {
    val scorePct = result.score?.let { (it * 100).roundToInt() }
    val marginPct = result.margem?.let { (it * 100).roundToInt() }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (result.aprovado) {
                LocalPontoCafeSemanticColors.current.successContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
        ),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier.padding(PontoCafeSpacing.md),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                if (result.aprovado) "Amostra aprovada" else "Amostra abaixo do critério",
                style = MaterialTheme.typography.titleMedium,
            )
            Text("Score correto · ${scorePct?.let { "$it%" } ?: "—"} (limiar ${result.limiar})")
            Text("Margem · ${marginPct?.let { "$it p.p." } ?: "—"} (mínima ${result.margemMinima})")
            result.outroMaisProximo?.let {
                Text("Outro rosto mais próximo · ${it.nome} · ${it.score ?: "—"}")
            }
        }
    }
}

private fun formatPercent(value: Double?): String =
    value?.let { "%.2f%%".format(it * 100.0) } ?: "—"
