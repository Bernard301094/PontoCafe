package com.pontocafe.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.pontocafe.app.avatar.AvatarImageOptimizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Fonte única para avatar de colaborador.
 *
 * A foto pode vir da câmera ou da galeria, mas ambas passam exatamente pelo
 * mesmo pipeline de recorte/otimização antes de serem entregues ao chamador.
 * O bitmap nunca participa do reconhecimento facial.
 */
@Composable
fun CollaboratorAvatarSourceDialog(
    collaboratorName: String,
    onDismiss: () -> Unit,
    onImageReady: (ByteArray) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun optimizeBitmap(bitmap: Bitmap) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    AvatarImageOptimizer.optimize(bitmap)
                }
            }.onSuccess { bytes ->
                onDismiss()
                onImageReady(bytes)
            }.onFailure { error ->
                onDismiss()
                onError(error.message ?: "Não foi possível preparar a foto da câmera.")
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview(),
    ) { bitmap ->
        if (bitmap == null) {
            onDismiss()
        } else {
            optimizeBitmap(bitmap)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            cameraLauncher.launch(null)
        } else {
            onDismiss()
            onError("Permissão de câmera necessária para tirar a foto do avatar.")
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) {
            onDismiss()
        } else {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        AvatarImageOptimizer.optimize(context.applicationContext, uri)
                    }
                }.onSuccess { bytes ->
                    onDismiss()
                    onImageReady(bytes)
                }.onFailure { error ->
                    onDismiss()
                    onError(error.message ?: "Não foi possível preparar a imagem selecionada.")
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Image, contentDescription = null) },
        title = { Text("Definir avatar") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                Text("Escolha como deseja definir o avatar de $collaboratorName.")
                Text("A foto é usada somente como avatar e permanece separada da biometria facial.")
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (
                        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        cameraLauncher.launch(null)
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Text("Tirar foto")
            }
        },
        dismissButton = {
            TextButton(onClick = { galleryLauncher.launch("image/*") }) {
                Icon(Icons.Default.Image, contentDescription = null)
                Text("Galeria")
            }
        },
    )
}
