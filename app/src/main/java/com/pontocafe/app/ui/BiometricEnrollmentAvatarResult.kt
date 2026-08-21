package com.pontocafe.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pontocafe.app.avatar.EnrollmentAvatarUploadStatus
import com.pontocafe.app.data.Colaborador

/** Shared completion state for Admin and Supervisor enrollment. */
@Composable
fun BiometricEnrollmentAvatarResult(
    collaborator: Colaborador,
    avatarPreviewWebp: ByteArray?,
    avatarUrl: String?,
    avatarStatus: EnrollmentAvatarUploadStatus,
    avatarError: String?,
    message: String?,
    busy: Boolean,
    onRetryAvatar: () -> Unit,
    onReplaceAvatar: (ByteArray) -> Unit,
    onDone: () -> Unit,
) {
    var choosingAvatar by remember { mutableStateOf(false) }
    var sourceError by remember { mutableStateOf<String?>(null) }
    if (choosingAvatar) {
        CollaboratorAvatarSourceDialog(
            collaboratorName = collaborator.nome,
            onDismiss = { choosingAvatar = false },
            onImageReady = {
                sourceError = null
                onReplaceAvatar(it)
            },
            onError = { sourceError = it },
        )
    }

    val previewBitmap = remember(avatarPreviewWebp) {
        avatarPreviewWebp?.takeIf { it.isNotEmpty() }?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }
    DisposableEffect(previewBitmap) {
        onDispose {
            previewBitmap?.takeIf { !it.isRecycled }?.recycle()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        val compactHeight = maxHeight < 480.dp
        Card(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .widthIn(max = 620.dp)
                .fillMaxWidth()
                .heightIn(
                    max = if (compactHeight) {
                        (maxHeight - 24.dp).coerceAtLeast(120.dp)
                    } else {
                        720.dp
                    },
                ),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(if (compactHeight) 18.dp else 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF167A63),
                    modifier = Modifier.size(42.dp),
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "Rosto cadastrado",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        collaborator.nome,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                Box(
                    modifier = Modifier
                        .size(if (compactHeight) 104.dp else 132.dp)
                        .clip(CircleShape)
                        .clearAndSetSemantics {
                            contentDescription = if (previewBitmap != null || !avatarUrl.isNullOrBlank()) {
                                "Foto de perfil capturada de ${collaborator.nome}"
                            } else {
                                "${collaborator.nome} ainda está sem foto de perfil"
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        previewBitmap != null -> Image(
                            bitmap = previewBitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        !avatarUrl.isNullOrBlank() -> CollaboratorAvatar(
                            name = collaborator.nome,
                            avatarUrl = avatarUrl,
                            avatarSize = if (compactHeight) 104.dp else 132.dp,
                        )
                        else -> InitialAvatar(
                            name = collaborator.nome,
                            avatarSize = if (compactHeight) 104.dp else 132.dp,
                        )
                    }
                }

                val banner = when (avatarStatus) {
                    EnrollmentAvatarUploadStatus.SAVED -> Triple(
                        "Foto de perfil salva",
                        "A foto está separada da biometria e já foi associada ao colaborador.",
                        PontoCafeTone.SUCCESS,
                    )
                    EnrollmentAvatarUploadStatus.UPLOADING -> Triple(
                        "Salvando foto de perfil",
                        "A biometria já está salva. Agora falta apenas concluir o envio da foto.",
                        PontoCafeTone.INFO,
                    )
                    EnrollmentAvatarUploadStatus.FAILED -> Triple(
                        "Biometria salva; foto pendente",
                        avatarError ?: "Tente salvar novamente ou escolha outra foto. Não é necessário cadastrar o rosto outra vez.",
                        PontoCafeTone.WARNING,
                    )
                    EnrollmentAvatarUploadStatus.NOT_CAPTURED -> Triple(
                        "Biometria salva; foto opcional",
                        "Adicione uma foto agora ou conclua. Isso não altera o cadastro facial.",
                        PontoCafeTone.INFO,
                    )
                }
                PcStateBanner(
                    title = banner.first,
                    supportingText = banner.second,
                    tone = banner.third,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                sourceError?.let {
                    PcStateBanner(
                        title = "Não foi possível preparar a foto",
                        supportingText = it,
                        tone = PontoCafeTone.WARNING,
                    )
                }

                message?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                when (avatarStatus) {
                    EnrollmentAvatarUploadStatus.SAVED -> PcFormActions(
                        primaryText = "Concluir",
                        onPrimary = onDone,
                        primaryEnabled = !busy,
                        secondaryText = "Trocar somente a foto",
                        onSecondary = { choosingAvatar = true },
                        secondaryEnabled = !busy,
                    )
                    EnrollmentAvatarUploadStatus.UPLOADING -> PcFormActions(
                        primaryText = "Salvando foto...",
                        onPrimary = {},
                        primaryEnabled = false,
                        primaryLoading = true,
                    )
                    EnrollmentAvatarUploadStatus.FAILED -> {
                        PcFormActions(
                            primaryText = if (avatarPreviewWebp == null) "Escolher outra foto" else "Tentar salvar novamente",
                            onPrimary = if (avatarPreviewWebp == null) {
                                { choosingAvatar = true }
                            } else {
                                onRetryAvatar
                            },
                            primaryEnabled = !busy,
                            secondaryText = "Escolher outra foto",
                            onSecondary = { choosingAvatar = true },
                            secondaryEnabled = !busy,
                        )
                        TextButton(onClick = onDone, enabled = !busy) {
                            Text("Concluir sem salvar a foto")
                        }
                    }
                    EnrollmentAvatarUploadStatus.NOT_CAPTURED -> PcFormActions(
                        primaryText = "Adicionar foto de perfil",
                        onPrimary = { choosingAvatar = true },
                        primaryEnabled = !busy,
                        secondaryText = "Concluir sem foto",
                        onSecondary = onDone,
                        secondaryEnabled = !busy,
                    )
                }

                Text(
                    "A foto de perfil não é usada para reconhecer ou validar a identidade.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Icon(
                    Icons.Default.Face,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
