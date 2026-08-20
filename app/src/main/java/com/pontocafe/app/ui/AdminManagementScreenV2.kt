package com.pontocafe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.pontocafe.app.AdminReliabilityViewModel
import com.pontocafe.app.AdminViewModel
import kotlinx.coroutines.delay

/**
 * Compatibilidade temporária com o shell administrativo existente.
 * A experiência real de Gestão vive em [AdminManagementScreenV3].
 */
@Composable
fun AdminManagementScreenV2(
    viewModel: AdminViewModel,
    reliabilityViewModel: AdminReliabilityViewModel,
    onDevicesClick: () -> Unit,
    onSyncClick: () -> Unit,
    onKioskClick: () -> Unit,
    onClose: () -> Unit,
) {
    AdminManagementScreenV3(
        viewModel = viewModel,
        reliabilityViewModel = reliabilityViewModel,
        onDevicesClick = onDevicesClick,
        onSyncClick = onSyncClick,
        onKioskClick = onKioskClick,
        onClose = onClose,
    )
}

@Composable
fun ReliabilityFeedback(viewModel: AdminReliabilityViewModel) {
    val state = viewModel.state
    val message = state.message

    LaunchedEffect(message) {
        if (message != null) {
            delay(3_500)
            if (viewModel.state.message == message) {
                viewModel.clearFeedback()
            }
        }
    }

    message?.let {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = PontoCafeSpacing.md,
                        top = PontoCafeSpacing.xs,
                        bottom = PontoCafeSpacing.xs,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
            ) {
                Text(
                    text = it,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                IconButton(onClick = viewModel::clearFeedback) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fechar aviso",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }

    state.error?.let { error ->
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = PontoCafeSpacing.md,
                        top = PontoCafeSpacing.xs,
                        bottom = PontoCafeSpacing.xs,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
            ) {
                Text(
                    text = error,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                IconButton(onClick = viewModel::clearFeedback) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fechar erro",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}
