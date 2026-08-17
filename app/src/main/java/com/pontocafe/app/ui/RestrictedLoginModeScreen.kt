package com.pontocafe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.pontocafe.app.data.SavedRestrictedAccount
import com.pontocafe.app.data.SecureAdminSessionStore

private data class RestrictedAccountEntry(
    val account: SavedRestrictedAccount,
    val admin: Boolean,
)

@Composable
fun RestrictedLoginModeScreen(
    onAdminClick: () -> Unit,
    onSupervisorClick: () -> Unit,
    onBackToPonto: () -> Unit,
) {
    val context = LocalContext.current
    val adminStore = remember(context) { SecureAdminSessionStore(context.applicationContext, "admin") }
    val supervisorStore = remember(context) { SecureAdminSessionStore(context.applicationContext, "supervisor") }

    fun loadAccounts(): List<RestrictedAccountEntry> = buildList {
        adminStore.savedAccounts().forEach { add(RestrictedAccountEntry(it, admin = true)) }
        supervisorStore.savedAccounts().forEach { add(RestrictedAccountEntry(it, admin = false)) }
    }.sortedWith(compareBy<RestrictedAccountEntry> { !it.account.hasSession }.thenBy { it.account.name.lowercase() })

    var accounts by remember { mutableStateOf(loadAccounts()) }
    var accountToForget by remember { mutableStateOf<RestrictedAccountEntry?>(null) }
    val listState = rememberLazyListState()

    fun openSaved(entry: RestrictedAccountEntry) {
        val store = if (entry.admin) adminStore else supervisorStore
        store.activate(entry.account.id)
        if (entry.admin) onAdminClick() else onSupervisorClick()
    }

    fun startNewLogin(admin: Boolean) {
        val store = if (admin) adminStore else supervisorStore
        store.beginNewLogin()
        if (admin) onAdminClick() else onSupervisorClick()
    }

    accountToForget?.let { entry ->
        AlertDialog(
            onDismissRequest = { accountToForget = null },
            title = { Text("Esquecer esta conta?") },
            text = {
                Text(
                    "A sessão salva de ${entry.account.name} será removida somente deste aparelho. A conta continuará existindo no sistema.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val store = if (entry.admin) adminStore else supervisorStore
                        store.forgetAccount(entry.account.id)
                        accounts = loadAccounts()
                        accountToForget = null
                    },
                ) { Text("Esquecer") }
            },
            dismissButton = {
                TextButton(onClick = { accountToForget = null }) { Text("Cancelar") }
            },
        )
    }

    val legacyAdmin = adminStore.hasToken() && adminStore.savedAccounts().isEmpty()
    val legacySupervisor = supervisorStore.hasToken() && supervisorStore.savedAccounts().isEmpty()

    PontoCafeResponsivePage(maxContentWidth = 720.dp) { responsive ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(
                    start = responsive.pagePadding,
                    end = responsive.pagePadding,
                    top = PontoCafeSpacing.xl,
                    bottom = 104.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.lg),
            ) {
                item("header") {
                    PontoCafeScreenHeader(
                        title = if (accounts.isEmpty()) "Escolha como entrar" else "Escolha uma conta",
                        eyebrow = "Acesso restrito",
                    )
                }

                item("hero") {
                    PcHeroCard(
                        title = if (accounts.isEmpty()) "Primeiro acesso neste aparelho" else "Acesso rápido e protegido",
                        supportingText = if (accounts.isEmpty()) {
                            "Faça login uma vez. Depois, a conta poderá ser selecionada aqui sem armazenar sua senha."
                        } else {
                            "Contas com sessão pronta abrem depois da validação do PIN do dispositivo. Senhas nunca são armazenadas."
                        },
                        icon = Icons.Default.Lock,
                        tone = PontoCafeTone.INFO,
                    )
                }

                if (accounts.isNotEmpty()) {
                    item("saved-title") {
                        SectionTitle(
                            title = "Contas neste aparelho",
                            subtitle = "${accounts.size} conta(s) salva(s). Toque em uma para continuar.",
                        )
                    }
                    items(accounts, key = { "account-${it.admin}-${it.account.id}" }) { entry ->
                        SavedAccountCard(
                            entry = entry,
                            onClick = { openSaved(entry) },
                            onForget = { accountToForget = entry },
                        )
                    }
                }

                if (legacyAdmin || legacySupervisor) {
                    item("legacy") {
                        PcSectionSurface {
                            Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                                Text(
                                    "Sessão existente",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "Foi encontrada uma sessão criada por uma versão anterior do app.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (legacyAdmin) {
                                    PcSecondaryButton(
                                        text = "Continuar como Administrador",
                                        icon = Icons.Default.AdminPanelSettings,
                                        onClick = onAdminClick,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                if (legacySupervisor) {
                                    PcSecondaryButton(
                                        text = "Continuar como Supervisor",
                                        icon = Icons.Default.Badge,
                                        onClick = onSupervisorClick,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                }

                item("other-title") {
                    SectionTitle(
                        title = if (accounts.isEmpty()) "Entrar" else "Usar outra conta",
                        subtitle = "O novo login ficará separado das contas já salvas neste aparelho.",
                    )
                }

                item("new-admin") {
                    PcActionTile(
                        title = if (accounts.isEmpty()) "Entrar como Administrador" else "Outra conta de Administrador",
                        supportingText = "Gestão completa, dispositivos, regras e auditoria",
                        icon = if (accounts.isEmpty()) Icons.Default.AdminPanelSettings else Icons.Default.PersonAdd,
                        onClick = { startNewLogin(admin = true) },
                    )
                }

                item("new-supervisor") {
                    PcActionTile(
                        title = if (accounts.isEmpty()) "Entrar como Supervisor" else "Outra conta de Supervisor",
                        supportingText = "Operação, pessoas, autorizações e relatórios",
                        icon = if (accounts.isEmpty()) Icons.Default.Badge else Icons.Default.PersonAdd,
                        onClick = { startNewLogin(admin = false) },
                    )
                }

                item("back") {
                    PcSecondaryButton(
                        text = "Continuar no Ponto Café",
                        onClick = onBackToPonto,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                item("security") {
                    PcStateBanner(
                        title = "Protegido pelo Android Keystore",
                        supportingText = "As sessões ficam cifradas no dispositivo e nenhuma senha é salva pelo Ponto Café.",
                        tone = PontoCafeTone.NEUTRAL,
                    )
                }
            }

            PcScrollToTopFab(
                listState = listState,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = responsive.pagePadding, bottom = PontoCafeSpacing.md),
            )
        }
    }
}

@Composable
private fun SavedAccountCard(
    entry: RestrictedAccountEntry,
    onClick: () -> Unit,
    onForget: () -> Unit,
) {
    val account = entry.account
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(
                start = PontoCafeSpacing.md,
                top = PontoCafeSpacing.sm,
                bottom = PontoCafeSpacing.sm,
                end = PontoCafeSpacing.xs,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            InitialAvatar(account.name)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xxs),
            ) {
                Text(
                    account.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    account.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                    StatusPill(
                        text = if (entry.admin) "Administrador" else "Supervisor",
                        tone = if (entry.admin) PontoCafeTone.INFO else PontoCafeTone.NEUTRAL,
                    )
                    StatusPill(
                        text = if (account.hasSession) "Sessão pronta" else "Senha necessária",
                        tone = if (account.hasSession) PontoCafeTone.SUCCESS else PontoCafeTone.WARNING,
                    )
                }
            }
            IconButton(onClick = onForget) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Esquecer ${account.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
