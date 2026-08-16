package com.pontocafe.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pontocafe.app.AdminViewModel
import com.pontocafe.app.FormDraftRegistry
import com.pontocafe.app.trackAccountDraftSubmission

@Composable
fun AdminNewAccountScreen(viewModel: AdminViewModel) {
    val state = viewModel.state
    val draftState = remember(viewModel) { FormDraftRegistry.account(viewModel) }

    LaunchedEffect(Unit) {
        draftState.prepareForDisplay(serverError = state.erro, loading = state.carregando)
    }
    LaunchedEffect(state.erro) {
        if (state.erro != null) draftState.markServerFailure()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        contentPadding = PaddingValues(
            start = PontoCafeSpacing.lg,
            end = PontoCafeSpacing.lg,
            top = PontoCafeSpacing.md,
            bottom = PontoCafeSpacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
    ) {
        item("header") {
            PontoCafeScreenHeader(
                title = "Nova conta de acesso",
                eyebrow = "Ponto Café",
                onBack = {
                    draftState.reset()
                    viewModel.voltarHome()
                },
                backLabel = "Painel",
            )
        }

        item("context") {
            AccountAccessContextCard()
        }

        item("form") {
            AdminAccountForm(
                draftState = draftState,
                carregando = state.carregando,
                initialProfile = AccountProfile.SUPERVISOR,
                showHeader = false,
                onSubmit = { input ->
                    viewModel.trackAccountDraftSubmission(draftState)
                    viewModel.criarConta(input)
                },
            )
        }

        item("feedback") {
            AdminFeedback(viewModel)
        }
    }
}

@Composable
private fun AccountAccessContextCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = PontoCafePremium.glassStrong),
        border = BorderStroke(1.dp, PontoCafePremium.borderSoft),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(
            modifier = Modifier.padding(PontoCafeSpacing.md),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            AccountContextRow(
                icon = Icons.Default.Info,
                title = "Acesso ao sistema",
                text = "Crie aqui contas para Supervisor ou Administrador. Colaboradores que apenas batem o ponto são cadastrados separadamente.",
            )
            AccountContextRow(
                icon = Icons.Default.AdminPanelSettings,
                title = "Supervisor recomendado",
                text = "Supervisor já vem selecionado para a operação diária. Use Administrador somente quando a pessoa precisar de controle total.",
            )
        }
    }
}

@Composable
private fun AccountContextRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    text: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = CircleShape,
            color = PontoCafePremium.glowSoft,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
        ) {
            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
