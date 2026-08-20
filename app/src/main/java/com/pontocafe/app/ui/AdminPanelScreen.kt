package com.pontocafe.app.ui

import androidx.compose.runtime.Composable
import com.pontocafe.app.AdminViewModel

/**
 * Entry point mantido para compatibilidade com o shell administrativo.
 * A experiência visual atual do Início vive em [AdminHomeScreenV2].
 */
@Composable
fun AdminPanelScreen(
    viewModel: AdminViewModel,
    onClose: () -> Unit,
    onDevicesClick: () -> Unit,
) {
    AdminHomeScreenV2(
        viewModel = viewModel,
        onClose = onClose,
        onDevicesClick = onDevicesClick,
    )
}
