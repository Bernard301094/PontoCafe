package com.pontocafe.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.pontocafe.app.SupervisorViewModel
import com.pontocafe.app.camera.BlinkLiveness
import com.pontocafe.app.camera.FaceCameraPreview
import com.pontocafe.app.camera.FaceObservation
import com.pontocafe.app.camera.FrameCaptureController
import com.pontocafe.app.camera.LivenessState
import com.pontocafe.app.data.Colaborador
import kotlin.math.abs

@Composable
private fun SupervisorManagementFeedback(viewModel: SupervisorViewModel) {
    viewModel.state.mensagem?.let {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(it, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
    viewModel.state.erro?.let {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(it, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
fun SupervisorCollaboratorsScreen(viewModel: SupervisorViewModel) {
    val state = viewModel.state
    var busca by remember { mutableStateOf("") }
    var excluirRostoDe by remember { mutableStateOf<Colaborador?>(null) }
    var excluirColaborador by remember { mutableStateOf<Colaborador?>(null) }

    val filtrados = state.colaboradores.filter {
        busca.isBlank() || it.nome.contains(busca, ignoreCase = true)
    }

    excluirRostoDe?.let { colaborador ->
        AlertDialog(
            onDismissRequest = { excluirRostoDe = null },
            title = { Text("Excluir rosto?") },
            text = { Text("A biometria de ${colaborador.nome} será removida. O colaborador continuará cadastrado e poderá ter o rosto registrado novamente.") },
            confirmButton = {
                Button(onClick = { excluirRostoDe = null; viewModel.excluirRosto(colaborador) }) { Text("Excluir rosto") }
            },
            dismissButton = { TextButton(onClick = { excluirRostoDe = null }) { Text("Cancelar") } },
        )
    }

    excluirColaborador?.let { colaborador ->
        AlertDialog(
            onDismissRequest = { excluirColaborador = null },
            title = { Text("Excluir colaborador?") },
            text = { Text("${colaborador.nome} será removido da lista ativa e o rosto será excluído. O histórico de pausas será preservado para auditoria.") },
            confirmButton = {
                Button(onClick = { excluirColaborador = null; viewModel.excluirColaborador(colaborador) }) { Text("Excluir colaborador") }
            },
            dismissButton = { TextButton(onClick = { excluirColaborador = null }) { Text("Cancelar") } },
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PontoCafeHeader("Gestão de colaboradores")
        Text(
            "O Supervisor pode cadastrar colaboradores, registrar ou remover rostos e retirar colaboradores da lista ativa.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SupervisorManagementFeedback(viewModel)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::abrirNovoColaborador, modifier = Modifier.weight(1f)) { Text("Novo colaborador") }
            OutlinedButton(onClick = viewModel::voltarAoVivo, modifier = Modifier.weight(1f)) { Text("Voltar") }
        }

        OutlinedTextField(
            value = busca,
            onValueChange = { busca = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Buscar colaborador") },
            placeholder = { Text("Nome") },
            singleLine = true,
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(filtrados, key = { it.id }) { colaborador ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(colaborador.nome, fontWeight = FontWeight.SemiBold)
                        val detalhe = listOfNotNull(colaborador.setor, colaborador.turno)
                            .filter { it.isNotBlank() }
                            .joinToString(" · ")
                        if (detalhe.isNotBlank()) Text(detalhe, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        Text(
                            if (colaborador.rostoCadastrado) "Rosto cadastrado" else "Pendente de registro de rosto",
                            color = if (colaborador.rostoCadastrado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                        )

                        Button(
                            onClick = { viewModel.cadastrarOuAtualizarRosto(colaborador) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.carregando,
                        ) {
                            Text(if (colaborador.rostoCadastrado) "Atualizar rosto" else "Cadastrar rosto")
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { excluirRostoDe = colaborador },
                                modifier = Modifier.weight(1f),
                                enabled = colaborador.rostoCadastrado && !state.carregando,
                            ) { Text("Excluir rosto") }
                            OutlinedButton(
                                onClick = { excluirColaborador = colaborador },
                                modifier = Modifier.weight(1f),
                                enabled = !state.carregando,
                            ) { Text("Excluir colaborador") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SupervisorNewCollaboratorScreen(viewModel: SupervisorViewModel) {
    var nome by remember { mutableStateOf("") }
    var setor by remember { mutableStateOf("Produção") }
    var turno by remember { mutableStateOf("A") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PontoCafeHeader("Novo colaborador")
        Text("Depois de salvar os dados, a câmera abrirá automaticamente para cadastrar o rosto.")
        SupervisorManagementFeedback(viewModel)

        OutlinedTextField(
            value = nome,
            onValueChange = { nome = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Nome completo") },
            singleLine = true,
        )
        OutlinedTextField(
            value = setor,
            onValueChange = { setor = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Setor") },
            singleLine = true,
        )
        OutlinedTextField(
            value = turno,
            onValueChange = { turno = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Turno") },
            singleLine = true,
        )

        Button(
            onClick = { viewModel.criarColaborador(nome, setor, turno) },
            modifier = Modifier.fillMaxWidth(),
            enabled = nome.trim().length >= 2 && !viewModel.state.carregando,
        ) { Text("Salvar e cadastrar rosto") }
        OutlinedButton(
            onClick = viewModel::voltarColaboradores,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Cancelar") }
    }
}

private enum class SupervisorEnrollmentPose(
    val title: String,
    val instruction: String,
) {
    FRONT("Olhe para frente", "Mantenha o rosto centralizado e olhe diretamente para a câmera."),
    LEFT("Vire levemente para a esquerda", "Gire o rosto devagar para a sua esquerda, sem inclinar a cabeça."),
    RIGHT("Vire levemente para a direita", "Agora gire o rosto devagar para a sua direita."),
    UP("Olhe um pouco para cima", "Levante levemente o rosto, mantendo-se no centro."),
    BLINK("Pisque os olhos", "Volte a olhar para frente e faça um piscar completo."),
    ;

    fun accepts(observation: FaceObservation): Boolean {
        if (!observation.isWellPositioned) return false
        return when (this) {
            FRONT -> abs(observation.yaw) <= 10f && abs(observation.pitch) <= 10f
            LEFT -> observation.yaw in 18f..38f && abs(observation.pitch) <= 15f
            RIGHT -> observation.yaw in -38f..-18f && abs(observation.pitch) <= 15f
            UP -> observation.pitch in 12f..30f && abs(observation.yaw) <= 15f
            BLINK -> false
        }
    }
}

private fun supervisorPositioningHint(observation: FaceObservation, pose: SupervisorEnrollmentPose): String {
    return when {
        observation.faceCount == 0 -> "ROSTO NÃO DETECTADO — encaixe o rosto dentro do contorno"
        observation.faceCount > 1 -> "Deixe apenas uma pessoa visível"
        !observation.isCentered -> "Centralize o rosto dentro do contorno"
        observation.faceWidthRatio < 0.22f -> "Aproxime um pouco o rosto"
        observation.faceWidthRatio > 0.68f -> "Afaste um pouco o rosto"
        abs(observation.roll) > 12f -> "Mantenha a cabeça reta"
        else -> pose.instruction
    }
}

@Composable
fun SupervisorBiometricEnrollmentScreen(viewModel: SupervisorViewModel) {
    val context = LocalContext.current
    val state = viewModel.state
    val colaborador = state.colaboradorSelecionado ?: return
    val poses = remember(colaborador.id) { SupervisorEnrollmentPose.entries.shuffled() }
    val stepIndex = state.biometricStepIndex.coerceIn(0, poses.lastIndex)
    val currentPose = poses[stepIndex]

    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { permissionGranted = it },
    )

    val captureController = remember { FrameCaptureController() }
    val liveness = remember { BlinkLiveness() }
    var livenessState by remember { mutableStateOf(LivenessState.POSICIONE_ROSTO) }
    var captureRequested by remember { mutableStateOf(false) }
    var stableFrames by remember { mutableStateOf(0) }
    var cameraHint by remember { mutableStateOf(currentPose.instruction) }

    LaunchedEffect(state.biometricScanCycle, stepIndex) {
        liveness.reset()
        captureRequested = false
        stableFrames = 0
        livenessState = LivenessState.POSICIONE_ROSTO
        cameraHint = currentPose.instruction
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (permissionGranted) {
            FaceCameraPreview(
                modifier = Modifier.fillMaxSize(),
                captureController = captureController,
                onObservation = { observation ->
                    if (!state.carregando && !captureRequested) {
                        cameraHint = supervisorPositioningHint(observation, currentPose)
                        if (currentPose == SupervisorEnrollmentPose.BLINK) {
                            val next = liveness.update(observation)
                            livenessState = next
                            cameraHint = when (next) {
                                LivenessState.POSICIONE_ROSTO -> supervisorPositioningHint(observation, currentPose)
                                LivenessState.PISQUE -> "Pisque os olhos para confirmar presença"
                                LivenessState.ABRA_OS_OLHOS -> "Agora abra os olhos"
                                LivenessState.CONCLUIDO -> "Presença confirmada. Capturando..."
                            }
                            if (next == LivenessState.CONCLUIDO) {
                                captureRequested = true
                                captureController.request()
                            }
                        } else {
                            if (currentPose.accepts(observation)) {
                                stableFrames += 1
                                if (stableFrames >= 4) {
                                    captureRequested = true
                                    cameraHint = "Posição confirmada. Capturando..."
                                    captureController.request()
                                }
                            } else stableFrames = 0
                        }
                    }
                },
                onFrame = viewModel::processarAmostraBiometrica,
            )
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("A câmera é necessária para cadastrar o rosto.", color = Color.White)
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Permitir câmera") }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().padding(16.dp).align(Alignment.TopCenter),
            color = Color.Black.copy(alpha = 0.72f),
            shape = RoundedCornerShape(20.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Cadastrar rosto", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(colaborador.nome, color = Color.White.copy(alpha = 0.78f))
                }
                TextButton(onClick = viewModel::voltarColaboradores) { Text("Cancelar", color = Color.White) }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().padding(18.dp).align(Alignment.BottomCenter),
            color = Color.Black.copy(alpha = 0.78f),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Etapa ${stepIndex + 1} de ${poses.size} · ${currentPose.title}", color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    text = when {
                        !viewModel.faceModelReady -> "Modelo facial não instalado"
                        state.carregando && state.biometricSamplesCaptured < poses.size - 1 -> "Processando amostra..."
                        state.carregando -> "Combinando e salvando biometria..."
                        else -> cameraHint
                    },
                    color = if (cameraHint.startsWith("ROSTO NÃO DETECTADO")) Color(0xFFFFC7C7) else Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
                Text("${state.biometricSamplesCaptured} de ${poses.size} amostras capturadas", color = Color.White.copy(alpha = 0.72f))
                Text("A ordem das etapas muda a cada cadastro. Somente o template facial combinado e cifrado será salvo; nenhuma foto é armazenada.", color = Color.White.copy(alpha = 0.75f))
                state.mensagem?.let { Text(it, color = Color(0xFFD7F3E4)) }
                state.erro?.let { Text(it, color = Color(0xFFFFC7C7)) }
            }
        }
    }
}
