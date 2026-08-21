package com.pontocafe.app.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Estado único para todos os fluxos que dependem da câmera. */
data class CameraPermissionUiState(
    val granted: Boolean,
    val requiresSettings: Boolean,
    val requestPermission: () -> Unit,
    val openSettings: () -> Unit,
)

/**
 * Mantém o estado sincronizado também quando a pessoa volta dos ajustes do
 * Android. `hasRequested` evita confundir o estado inicial com um bloqueio.
 */
@Composable
fun rememberCameraPermissionUiState(): CameraPermissionUiState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = rememberPermissionActivity(context)
    var hasRequested by rememberSaveable { mutableStateOf(false) }
    var granted by rememberSaveable {
        mutableStateOf(context.hasCameraPermission())
    }
    var requiresSettings by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { accepted ->
        hasRequested = true
        granted = accepted
        requiresSettings = !accepted && activity?.let {
            !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
        } == true
    }

    DisposableEffect(lifecycleOwner, context, activity, hasRequested) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = context.hasCameraPermission()
                if (granted) {
                    requiresSettings = false
                } else if (hasRequested && activity != null) {
                    requiresSettings = !ActivityCompat.shouldShowRequestPermissionRationale(
                        activity,
                        Manifest.permission.CAMERA,
                    )
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return CameraPermissionUiState(
        granted = granted,
        requiresSettings = requiresSettings,
        requestPermission = {
            hasRequested = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        },
        openSettings = {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
    )
}

/** Mensaje y acción consistentes para Ponto, altas biométricas y diagnóstico. */
@Composable
fun CameraPermissionCard(
    state: CameraPermissionUiState,
    title: String,
    rationale: String,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
) {
    val container = if (dark) Color(0xF5161B19) else MaterialTheme.colorScheme.surface
    val content = if (dark) Color.White else MaterialTheme.colorScheme.onSurface
    val supporting = if (dark) Color.White.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurfaceVariant
    val accent = if (dark) Color(0xFF72DCBC) else MaterialTheme.colorScheme.primary
    val resolvedTitle = if (state.requiresSettings) {
        "Câmera bloqueada nos ajustes"
    } else {
        title
    }
    val resolvedRationale = if (state.requiresSettings) {
        "$rationale Abra os ajustes do Ponto Café e autorize o acesso à câmera."
    } else {
        rationale
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 520.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(PontoCafeSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = accent.copy(alpha = 0.14f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (state.requiresSettings) Icons.Default.Settings else Icons.Default.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                        tint = accent,
                    )
                }
            }
            Text(
                text = resolvedTitle,
                modifier = Modifier.semantics {
                    heading()
                    if (state.requiresSettings) liveRegion = LiveRegionMode.Polite
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = content,
                textAlign = TextAlign.Center,
            )
            Text(
                text = resolvedRationale,
                style = MaterialTheme.typography.bodyMedium,
                color = supporting,
                textAlign = TextAlign.Center,
            )
            PcPrimaryButton(
                text = if (state.requiresSettings) "Abrir ajustes" else "Permitir câmera",
                onClick = if (state.requiresSettings) state.openSettings else state.requestPermission,
                icon = if (state.requiresSettings) Icons.Default.Settings else Icons.Default.CameraAlt,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findPermissionActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findPermissionActivity()
    else -> null
}

@Composable
private fun rememberPermissionActivity(context: Context): Activity? = context.findPermissionActivity()
