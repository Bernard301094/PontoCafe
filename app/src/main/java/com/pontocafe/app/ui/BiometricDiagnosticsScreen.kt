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
import androidx.compose.foundation.shape.RoundedCornerShape
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
        .filter { search.isBlank() || it.nome.contains(search, true) || it.setor.orEmpty().contains(search, true) }
        .sortedBy { it.nome.lowercase() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = PontoCafeSpacing.lg),
        contentPadding = PaddingValues(top = PontoCafeSpacing.lg, bottom = PontoCafeSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
    ) {
        item("header") { PontoCafeScreenHeader(title = "Biometria", eyebrow = "Precisão e governança", onBack = onBack) }
        item("feedback") { ReliabilityFeedback(viewModel) }

        if (summary != null) {
            item("metrics") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                    MetricCard(summary.biometriaCadastrada.toString(), "Rostos cadastrados", Modifier.weight(1f))
                    MetricCard(summary.biometriaPendente.toString(), "Pendentes", Modifier.weight(1f), emphasized = summary.biometriaPendente > 0)
                }
            }
            item("thresholds") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(PontoCafeSpacing.md), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Critérios atuais", style = MaterialTheme.typography.titleMedium)
                        Text("Limiar de reconhecimento · ${summary.limiar}")
                        Text("Margem mínima entre candidatos · ${summary.margemMinima}")
                        Text("Limiar contra rosto duplicado · ${summary.limiarDuplicidade}")
                        Text("Retenção após desativação · ${summary.retencaoDias} dias")
                        if (summary.modelos.isNotEmpty()) {
                            Text(
                                summary.modelos.joinToString("\n") { "${it.modelo} · ${it.versaoModelo} · ${it.total} rosto(s)" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                    MetricCard(calibration.amostras.toString(), "Amostras", Modifier.weight(1f))
                    MetricCard(formatPercent(calibration.top1Accuracy), "Top-1 accuracy", Modifier.weight(1f))
                }
            }
            item("calibration-stat-rates") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                    MetricCard(
                        formatPercent(calibration.falseRejectRate),
                        "FRR",
                        Modifier.weight(1f),
                        emphasized = (calibration.falseRejectRate ?: 0.0) > 0.05,
                    )
                    MetricCard(
                        formatPercent(calibration.falseAcceptRate),
                        "FAR",
                        Modifier.weight(1f),
                        emphasized = (calibration.falseAcceptRate ?: 0.0) > 0.0,
                    )
                }
            }
            item("calibration-stat-note") {
                Text(
                    "${calibration.comparacoesImpostor} comparação(ões) impostoras · ${calibration.falsosAceitesImpostor} acima do limiar. ${calibration.observacao}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        metricsError?.let { message ->
            item("metrics-error") {
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item("calibration-title") {
            SectionTitle(
                "Teste de calibração",
                "Capture uma amostra real. A app compara o rosto correto contra todos os outros templates ativos sem salvar a foto.",
            )
        }
        item("search") {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Buscar colaborador com rosto") },
                singleLine = true,
            )
        }
        if (candidates.isEmpty()) {
            item("empty") {
                Card(Modifier.fillMaxWidth()) {
                    Text("Nenhum colaborador com biometria encontrada para este filtro.", Modifier.padding(PontoCafeSpacing.md))
                }
            }
        } else {
            items(candidates, key = { "cal-${it.id}" }) { collaborator ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        selected = collaborator
                        cameraOpen = true
                    },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(PontoCafeSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                    ) {
                        InitialAvatar(collaborator.nome)
                        Column(Modifier.weight(1f)) {
                            Text(collaborator.nome, style = MaterialTheme.typography.titleMedium)
                            Text(
                                listOfNotNull(collaborator.setor, collaborator.turno).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("Testar")
                    }
                }
            }
        }

        state.calibration?.let { result ->
            item("last-result") { CalibrationResultCard(result) }
        }

        item("retention-title") { SectionTitle("Governança", "A limpeza automática também é executada diariamente no backend.") }
        item("retention") {
            OutlinedButton(
                onClick = viewModel::runRetentionCleanup,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.loading,
            ) { Text("Executar política de retenção agora") }
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
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
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
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Permitir câmera") }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(PontoCafeSpacing.md).align(Alignment.TopCenter),
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
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(PontoCafeSpacing.md).align(Alignment.BottomCenter),
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
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
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
            containerColor = if (result.aprovado) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(Modifier.padding(PontoCafeSpacing.md), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (result.aprovado) "Amostra aprovada" else "Amostra abaixo do critério", style = MaterialTheme.typography.titleMedium)
            Text("Score correto · ${scorePct?.let { "$it%" } ?: "—"} (limiar ${result.limiar})")
            Text("Margem · ${marginPct?.let { "$it p.p." } ?: "—"} (mínima ${result.margemMinima})")
            result.outroMaisProximo?.let {
                Text("Outro rosto mais próximo · ${it.nome} · ${it.score ?: "—"}")
            }
        }
    }
}

private fun formatPercent(value: Double?): String = value?.let { "%.2f%%".format(it * 100.0) } ?: "—"
