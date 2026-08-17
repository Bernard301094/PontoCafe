@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pontocafe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.pontocafe.app.data.SavedRestrictedAccount

@Composable
fun PcAccountSummaryCard(
    account: SavedRestrictedAccount?,
    fallbackName: String,
    profileLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = account?.name?.takeIf { it.isNotBlank() } ?: fallbackName
    val email = account?.email?.takeIf { it.isNotBlank() }

    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = androidx.compose.ui.unit.Dp.Unspecified),
    ) {
        Row(
            modifier = Modifier.padding(PontoCafeSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            InitialAvatar(name)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xxs),
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = email ?: "$profileLabel neste aparelho",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusPill(profileLabel, PontoCafeTone.INFO)
        }
    }
}

@Composable
fun PcAccountProfileSheet(
    account: SavedRestrictedAccount?,
    fallbackName: String,
    profileLabel: String,
    onDismiss: () -> Unit,
    onLogout: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val name = account?.name?.takeIf { it.isNotBlank() } ?: fallbackName
    val email = account?.email?.takeIf { it.isNotBlank() } ?: "Sessão local protegida"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(
                    start = PontoCafeSpacing.lg,
                    end = PontoCafeSpacing.lg,
                    bottom = PontoCafeSpacing.xl,
                ),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                InitialAvatar(name)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            PcKeyValueCard(
                title = "Conta neste aparelho",
                rows = listOf(
                    "Perfil" to profileLabel,
                    "Sessão" to if (account?.hasSession != false) "Ativa e protegida" else "Senha necessária",
                    "Proteção" to "Android Keystore · AES-GCM",
                ),
            )

            PcStateBanner(
                title = "Privacidade da sessão",
                supportingText = "A senha não é armazenada. O token de sessão permanece cifrado pelo Android Keystore neste dispositivo.",
                tone = PontoCafeTone.NEUTRAL,
            )

            if (onLogout != null) {
                PcSecondaryButton(
                    text = "Encerrar esta sessão",
                    icon = Icons.Default.Lock,
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth(),
                    contentColor = MaterialTheme.colorScheme.error,
                )
            }

            PcPrimaryButton(
                text = "Fechar",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
