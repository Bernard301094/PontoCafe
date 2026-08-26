package com.pontocafe.app.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Info
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

    PcHeroPage(
        heroContent = {
            PcHeroZoneScreenHeader(
                title = "Nova conta de acesso",
                eyebrow = "Administrador",
                onBack = {
                    draftState.reset()
                    viewModel.voltarHome()
                },
                backLabel = "Painel",
            )
            Text(
                "Contas de Supervisor e Administrador, separadas do cadastro facial",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
            )
        },
    ) {
    PontoCafeResponsivePage(maxContentWidth = 760.dp) { responsive ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding(),
            contentPadding = PaddingValues(
                start = responsive.pagePadding,
                end = responsive.pagePadding,
                top = PontoCafeSpacing.md,
                bottom = PontoCafeSpacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
        ) {
            item("context") { AccountAccessContextCard() }

            item("form") {
                AdminAccountForm(
                    draftState = draftState,
                    carregando = state.carregando,
                    initialProfile = AccountProfile.SUPERVISOR_A,
                    showHeader = false,
                    responsive = responsive,
                    onSubmit = { input ->
                        viewModel.trackAccountDraftSubmission(draftState)
                        viewModel.criarConta(input)
                    },
                )
            }

            item("feedback") { AdminFeedback(viewModel) }
        }
    }
    }
}

@Composable
private fun AccountAccessContextCard() {
    PcSectionSurface {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
        ) {
            AccountContextRow(
                icon = Icons.Default.Info,
                title = "Acesso ao sistema",
                text = "Para Supervisor, informe nome, e-mail e turno A, B, C ou D. A senha temporária é gerada pelo sistema e deve ser trocada no primeiro acesso.",
            )
            AccountContextRow(
                icon = Icons.Default.AdminPanelSettings,
                title = "Segurança do primeiro acesso",
                text = "A senha temporária não é salva em texto puro. Depois da troca obrigatória, somente o hash da nova senha permanece no servidor.",
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
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xxs),
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
