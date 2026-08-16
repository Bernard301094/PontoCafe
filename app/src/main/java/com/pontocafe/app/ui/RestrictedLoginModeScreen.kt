package com.pontocafe.app.ui

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
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
    }.sortedBy { it.account.name.lowercase() }

    var accounts by remember { mutableStateOf(loadAccounts()) }
    var accountToForget by remember { mutableStateOf<RestrictedAccountEntry?>(null) }

    fun recreateAfter(action: () -> Unit) {
        action()
        (context as? Activity)?.recreate()
    }

    fun openSaved(entry: RestrictedAccountEntry) {
        val store = if (entry.admin) adminStore else supervisorStore
        store.activate(entry.account.id)
        recreateAfter(if (entry.admin) onAdminClick else onSupervisorClick)
    }

    fun startNewLogin(admin: Boolean) {
        val store = if (admin) adminStore else supervisorStore
        store.beginNewLogin()
        recreateAfter(if (admin) onAdminClick else onSupervisorClick)
    }

    accountToForget?.let { entry ->
        AlertDialog(
            onDismissRequest = { accountToForget = null },
            title = { Text("Esquecer esta conta?") },
            text = {
                Text(
                    "A sessão salva de ${entry.account.name} será removida deste aparelho. A conta continuará existindo no sistema.",
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 22.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = PontoCafePremium.glassStrong,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(32.dp),
            border = BorderStroke(1.dp, PontoCafePremium.border),
            shadowElevation = 16.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = PontoCafePremium.glowSoft,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.padding(17.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                Text(
                    "ACESSO RESTRITO",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    if (accounts.isEmpty()) "Escolha como entrar" else "Escolha uma conta",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    if (accounts.isEmpty()) {
                        "Faça o primeiro login neste aparelho. Depois, a conta ficará disponível aqui para acesso rápido."
                    } else {
                        "As contas autenticadas neste aparelho aparecem abaixo. Senhas nunca são armazenadas."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                if (accounts.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        accounts.forEach { entry ->
                            SavedAccountCard(
                                entry = entry,
                                onClick = { openSaved(entry) },
                                onForget = { accountToForget = entry },
                            )
                        }
                    }
                }

                val legacyAdmin = adminStore.hasToken() && adminStore.savedAccounts().isEmpty()
                val legacySupervisor = supervisorStore.hasToken() && supervisorStore.savedAccounts().isEmpty()
                if (legacyAdmin || legacySupervisor) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, PontoCafePremium.borderSoft),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "Sessão existente",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (legacyAdmin) {
                                TextButton(onClick = { recreateAfter(onAdminClick) }) {
                                    Text("Continuar como Administrador")
                                }
                            }
                            if (legacySupervisor) {
                                TextButton(onClick = { recreateAfter(onSupervisorClick) }) {
                                    Text("Continuar como Supervisor")
                                }
                            }
                        }
                    }
                }

                Text(
                    if (accounts.isEmpty()) "PRIMEIRO ACESSO" else "ENTRAR COM OUTRA CONTA",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (accounts.isEmpty()) 4.dp else 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Button(
                    onClick = { startNewLogin(admin = true) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(
                        if (accounts.isEmpty()) Icons.Default.AdminPanelSettings else Icons.Default.PersonAdd,
                        contentDescription = null,
                    )
                    Text(
                        if (accounts.isEmpty()) "Entrar como Administrador" else "Outra conta de Administrador",
                        modifier = Modifier.padding(start = 9.dp),
                        fontWeight = FontWeight.Bold,
                    )
                }

                OutlinedButton(
                    onClick = { startNewLogin(admin = false) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, PontoCafePremium.borderSoft),
                ) {
                    Icon(
                        if (accounts.isEmpty()) Icons.Default.Badge else Icons.Default.PersonAdd,
                        contentDescription = null,
                    )
                    Text(
                        if (accounts.isEmpty()) "Entrar como Supervisor" else "Outra conta de Supervisor",
                        modifier = Modifier.padding(start = 9.dp),
                    )
                }

                OutlinedButton(
                    onClick = onBackToPonto,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, PontoCafePremium.borderSoft),
                ) {
                    Text("Continuar no Ponto Café")
                }

                Text(
                    "Sessões protegidas pelo Android Keystore · nenhuma senha é salva",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                    textAlign = TextAlign.Center,
                )
            }
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, PontoCafePremium.borderSoft),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 13.dp, bottom = 13.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InitialAvatar(account.name, modifier = Modifier.size(46.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProfilePill(if (entry.admin) "ADMIN" else "SUPERVISOR")
                    StatusPill(
                        text = if (account.hasSession) "Sessão pronta" else "Senha necessária",
                        tone = if (account.hasSession) PontoCafeTone.SUCCESS else PontoCafeTone.WARNING,
                    )
                }
            }
            IconButton(onClick = onForget) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Esquecer conta",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
