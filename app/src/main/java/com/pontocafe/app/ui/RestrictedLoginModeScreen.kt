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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
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
    }.sortedWith(
        compareBy<RestrictedAccountEntry> { !it.account.hasSession }
            .thenBy { it.account.name.lowercase() },
    )

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
            icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
            title = { Text("Remover conta deste aparelho?") },
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
                ) { Text("Remover") }
            },
            dismissButton = {
                TextButton(onClick = { accountToForget = null }) { Text("Cancelar") }
            },
        )
    }

    val legacyAdmin = adminStore.hasToken() && adminStore.savedAccounts().isEmpty()
    val legacySupervisor = supervisorStore.hasToken() && supervisorStore.savedAccounts().isEmpty()

    PontoCafeResponsivePage(maxContentWidth = 920.dp) { responsive ->
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
                    top = PontoCafeSpacing.lg,
                    bottom = 104.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.lg),
            ) {
                item("header") {
                    PontoCafeScreenHeader(
                        title = if (accounts.isEmpty()) "Escolha como entrar" else "Entrar com uma conta salva",
                        eyebrow = "Acesso restrito",
                    )
                }

                item("security-strip") {
                    RestrictedAccessSecurityStrip(hasSavedAccounts = accounts.isNotEmpty())
                }

                item("main-content") {
                    if (responsive.supportsTwoColumns && accounts.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.lg),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(
                                modifier = Modifier.weight(1.18f),
                                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                            ) {
                                SavedAccountsSection(
                                    accounts = accounts,
                                    onOpen = ::openSaved,
                                    onForget = { accountToForget = it },
                                )
                                LegacySessionsSection(
                                    legacyAdmin = legacyAdmin,
                                    legacySupervisor = legacySupervisor,
                                    onAdminClick = onAdminClick,
                                    onSupervisorClick = onSupervisorClick,
                                )
                            }
                            Column(
                                modifier = Modifier.weight(0.82f),
                                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                            ) {
                                OtherAccountSection(
                                    hasSavedAccounts = true,
                                    twoColumns = false,
                                    onAdmin = { startNewLogin(admin = true) },
                                    onSupervisor = { startNewLogin(admin = false) },
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.lg)) {
                            if (accounts.isNotEmpty()) {
                                SavedAccountsSection(
                                    accounts = accounts,
                                    onOpen = ::openSaved,
                                    onForget = { accountToForget = it },
                                )
                            }
                            LegacySessionsSection(
                                legacyAdmin = legacyAdmin,
                                legacySupervisor = legacySupervisor,
                                onAdminClick = onAdminClick,
                                onSupervisorClick = onSupervisorClick,
                            )
                            OtherAccountSection(
                                hasSavedAccounts = accounts.isNotEmpty(),
                                twoColumns = responsive.supportsTwoColumns,
                                onAdmin = { startNewLogin(admin = true) },
                                onSupervisor = { startNewLogin(admin = false) },
                            )
                        }
                    }
                }

                item("back") {
                    PcSecondaryButton(
                        text = "Voltar ao Ponto Café",
                        icon = Icons.Default.ArrowForward,
                        onClick = onBackToPonto,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                item("security-footer") {
                    Text(
                        text = "As sessões ficam cifradas pelo Android Keystore. O Ponto Café não armazena a senha da conta.",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun RestrictedAccessSecurityStrip(hasSavedAccounts: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = PontoCafeSpacing.md, vertical = PontoCafeSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (hasSavedAccounts) Icons.Default.Security else Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = if (hasSavedAccounts) "Acesso protegido neste dispositivo" else "Primeiro acesso protegido",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (hasSavedAccounts) {
                        "Sessões prontas usam a validação do dispositivo. Sua senha não fica salva."
                    } else {
                        "Entre uma vez para criar uma sessão cifrada sem armazenar sua senha."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusPill("Keystore", PontoCafeTone.SUCCESS)
        }
    }
}

@Composable
private fun SavedAccountsSection(
    accounts: List<RestrictedAccountEntry>,
    onOpen: (RestrictedAccountEntry) -> Unit,
    onForget: (RestrictedAccountEntry) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
        SectionTitle(
            title = "Sessões salvas",
            subtitle = if (accounts.size == 1) {
                "1 conta pronta neste aparelho."
            } else {
                "${accounts.size} contas disponíveis neste aparelho."
            },
        )
        accounts.forEach { entry ->
            SavedAccountCard(
                entry = entry,
                onOpen = { onOpen(entry) },
                onForget = { onForget(entry) },
            )
        }
    }
}

@Composable
private fun LegacySessionsSection(
    legacyAdmin: Boolean,
    legacySupervisor: Boolean,
    onAdminClick: () -> Unit,
    onSupervisorClick: () -> Unit,
) {
    if (!legacyAdmin && !legacySupervisor) return

    PcSectionSurface {
        Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
            Text(
                "Sessão de versão anterior",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Encontramos uma sessão criada antes do seletor de contas. Você ainda pode continuar normalmente.",
                style = MaterialTheme.typography.bodySmall,
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

@Composable
private fun OtherAccountSection(
    hasSavedAccounts: Boolean,
    twoColumns: Boolean,
    onAdmin: () -> Unit,
    onSupervisor: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
        SectionTitle(
            title = if (hasSavedAccounts) "Entrar com outra conta" else "Entrar",
            subtitle = if (hasSavedAccounts) {
                "O novo login fica separado das sessões já salvas."
            } else {
                "Escolha o perfil para iniciar uma sessão protegida."
            },
        )

        if (twoColumns) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                verticalAlignment = Alignment.Top,
            ) {
                RestrictedLoginOption(
                    admin = true,
                    hasSavedAccounts = hasSavedAccounts,
                    onClick = onAdmin,
                    modifier = Modifier.weight(1f),
                )
                RestrictedLoginOption(
                    admin = false,
                    hasSavedAccounts = hasSavedAccounts,
                    onClick = onSupervisor,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            RestrictedLoginOption(
                admin = true,
                hasSavedAccounts = hasSavedAccounts,
                onClick = onAdmin,
            )
            RestrictedLoginOption(
                admin = false,
                hasSavedAccounts = hasSavedAccounts,
                onClick = onSupervisor,
            )
        }
    }
}

@Composable
private fun RestrictedLoginOption(
    admin: Boolean,
    hasSavedAccounts: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PcActionTile(
        title = when {
            admin && hasSavedAccounts -> "Outro Administrador"
            !admin && hasSavedAccounts -> "Outro Supervisor"
            admin -> "Administrador"
            else -> "Supervisor"
        },
        supportingText = if (admin) {
            "Gestão completa, dispositivos e auditoria"
        } else {
            "Operação, pessoas, autorizações e relatórios"
        },
        icon = when {
            hasSavedAccounts -> Icons.Default.PersonAdd
            admin -> Icons.Default.AdminPanelSettings
            else -> Icons.Default.Badge
        },
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun SavedAccountCard(
    entry: RestrictedAccountEntry,
    onOpen: () -> Unit,
    onForget: () -> Unit,
) {
    val account = entry.account
    var menuExpanded by remember(account.id) { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(PontoCafeSpacing.md),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                InitialAvatar(account.name, avatarSize = 48.dp)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xxs),
                ) {
                    Text(
                        account.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        account.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
            ) {
                StatusPill(
                    text = if (entry.admin) "Administrador" else "Supervisor",
                    tone = if (entry.admin) PontoCafeTone.INFO else PontoCafeTone.NEUTRAL,
                )
                StatusPill(
                    text = if (account.hasSession) "Sessão pronta" else "Senha necessária",
                    tone = if (account.hasSession) PontoCafeTone.SUCCESS else PontoCafeTone.WARNING,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
            ) {
                PcTonalButton(
                    text = if (account.hasSession) "Abrir conta" else "Continuar login",
                    icon = Icons.Default.ArrowForward,
                    onClick = onOpen,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Mais opções de ${account.name}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Remover deste aparelho") },
                            leadingIcon = {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null)
                            },
                            onClick = {
                                menuExpanded = false
                                onForget()
                            },
                        )
                    }
                }
            }
        }
    }
}
