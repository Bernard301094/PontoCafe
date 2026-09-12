package com.pontocafe.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * A folha da câmara.
 *
 * Sobe por cima da lista de nomes em vez de trocar de ecrã: quem desiste de ler
 * arrasta-a para baixo e continua exactamente onde estava, com a lista já
 * carregada. Trocar de tela obrigaria a refazer o caminho de volta.
 *
 * A leitura corre num executor próprio e devolve cada texto decodificado a
 * [onLeitura] — que é chamado muitas vezes por segundo, com o que estiver à
 * frente da lente. Filtrar o que é um QR do Ponto Café é do ViewModel, não
 * daqui: esta folha só sabe ler quadrados.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerSheet(
    onLeitura: (String) -> Unit,
    onFechar: () -> Unit,
) {
    val context = LocalContext.current
    var temPermissao by remember { mutableStateOf(temPermissaoCamera(context)) }
    var falhaCamera by remember { mutableStateOf<String?>(null) }

    val pedirPermissao = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { concedida -> temPermissao = concedida }

    // O pedido sai no instante em que a folha abre, e não no arranque do app:
    // a câmara só se justifica a quem acabou de tocar em "Ler QR".
    LaunchedEffect(Unit) {
        if (!temPermissao) pedirPermissao.launch(Manifest.permission.CAMERA)
    }

    val estadoFolha = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onFechar, sheetState = estadoFolha) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PontoCafeSpacing.md, vertical = PontoCafeSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Aponte para o seu QR",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "O código do café já traz o seu nome. A leitura registra a saída ou o retorno de uma vez.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.large)
                    .border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    !temPermissao -> AvisoCamera(
                        titulo = "Sem acesso à câmara",
                        detalhe = "Autorize a câmara para este aparelho, ou digite o código de 6 caracteres.",
                    )
                    falhaCamera != null -> AvisoCamera(
                        titulo = "A câmara não abriu",
                        detalhe = falhaCamera!!,
                    )
                    else -> CameraPreview(
                        onLeitura = onLeitura,
                        onFalha = { falhaCamera = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Text(
                "Sem o QR à mão? Feche esta janela e digite o código.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AvisoCamera(titulo: String, detalhe: String) {
    Column(
        modifier = Modifier.padding(PontoCafeSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
    ) {
        Icon(
            Icons.Default.NoPhotography,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(titulo, style = MaterialTheme.typography.titleSmall)
        Text(
            detalhe,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * O preview e a análise, presos ao ciclo de vida desta composição.
 *
 * `unbindAll` no descarte é obrigatório e não é zelo: a câmara é um recurso
 * exclusivo do sistema. Deixá-la ligada depois de a folha fechar significa que
 * a próxima abertura falha, e que o LED fica aceso num aparelho de corredor.
 */
@Composable
private fun CameraPreview(
    onLeitura: (String) -> Unit,
    onFalha: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val leituraAtual by rememberUpdatedState(onLeitura)
    val executor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember { PreviewView(context) }

    DisposableEffect(Unit) {
        val futuro = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null

        futuro.addListener({
            try {
                provider = futuro.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analise = ImageAnalysis.Builder()
                    // Quadros atrasados não interessam: o que vale é o que está
                    // à frente da lente agora. Guardá-los criaria uma fila que
                    // só aumenta a latência da leitura.
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analise.setAnalyzer(executor, QrAnalyzer { texto -> leituraAtual(texto) })

                provider?.unbindAll()
                provider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analise,
                )
            } catch (erro: Throwable) {
                onFalha(erro.message ?: "Este aparelho não disponibilizou a câmara.")
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            runCatching { provider?.unbindAll() }
            executor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

/**
 * Decodifica um quadro da câmara, sem copiar a imagem inteira.
 *
 * O plano Y do YUV já é a luminância que o ZXing quer — passá-lo directamente
 * evita converter para bitmap a cada quadro, que é o que faz um leitor parecer
 * lento num aparelho de entrada.
 *
 * `NotFoundException` é o resultado esperado na esmagadora maioria dos quadros:
 * significa "não há QR aqui", não uma falha. Por isso é engolida em silêncio.
 */
private class QrAnalyzer(private val onTexto: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val leitor = QRCodeReader()
    private val dicas = mapOf(DecodeHintType.TRY_HARDER to true)

    override fun analyze(image: ImageProxy) {
        try {
            val plano = image.planes.firstOrNull() ?: return
            val buffer = plano.buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            val fonte = PlanarYUVLuminanceSource(
                bytes,
                plano.rowStride,
                image.height,
                0,
                0,
                image.width,
                image.height,
                false,
            )
            val resultado = leitor.decode(BinaryBitmap(HybridBinarizer(fonte)), dicas)
            resultado.text?.takeIf { it.isNotBlank() }?.let(onTexto)
        } catch (_: Throwable) {
            // Quadro sem QR legível. É o caso normal enquanto a pessoa aponta.
        } finally {
            leitor.reset()
            image.close()
        }
    }
}

private fun temPermissaoCamera(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED


/** Ícone da ação, para o botão que abre esta folha. */
val PontoQrIcon = Icons.Default.QrCodeScanner
