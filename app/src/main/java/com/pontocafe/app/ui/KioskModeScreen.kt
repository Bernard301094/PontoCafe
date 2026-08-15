package com.pontocafe.app.ui

import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.pontocafe.app.data.KioskModeSettings
import com.pontocafe.app.data.KioskModeStore

@Composable
fun KioskModeScreen(
    activity: FragmentActivity,
    store: KioskModeStore,
    onBack: () -> Unit,
) {
    var settings by remember { mutableStateOf(store.read()) }
    var message by remember { mutableStateOf<String?>(null) }

    fun persist(next: KioskModeSettings) {
        settings = next
        store.save(next)
        if (next.enabled && next.keepScreenOn) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = PontoCafeSpacing.lg),
        contentPadding = PaddingValues(top = PontoCafeSpacing.lg, bottom = PontoCafeSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
    ) {
        item("header") { PontoCafeScreenHeader(title = "Modo terminal", eyebrow = "Este dispositivo", onBack = onBack) }
        item("intro") {
            OperationalAlertCard(
                title = if (settings.enabled) "Modo terminal configurado" else "Modo terminal opcional",
                text = "Use estas opções somente em aparelhos dedicados ao Ponto Café. O acesso Admin/Supervisor continua protegido.",
                tone = if (settings.enabled) PontoCafeTone.SUCCESS else PontoCafeTone.INFO,
            )
        }
        message?.let { text ->
            item("message") {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Text(text, Modifier.padding(PontoCafeSpacing.md), color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }

        item("settings") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(PontoCafeSpacing.md), verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md)) {
                    KioskSwitchRow(
                        title = "Ativar modo terminal",
                        subtitle = "Marca este aparelho como dedicado ao Ponto Café.",
                        checked = settings.enabled,
                        onCheckedChange = { persist(settings.copy(enabled = it)) },
                    )
                    KioskSwitchRow(
                        title = "Manter tela ligada",
                        subtitle = "Evita que a tela durma enquanto este aparelho estiver funcionando como terminal.",
                        checked = settings.keepScreenOn,
                        enabled = settings.enabled,
                        onCheckedChange = { persist(settings.copy(keepScreenOn = it)) },
                    )
                    KioskSwitchRow(
                        title = "Fixar aplicativo na tela",
                        subtitle = "Usa o Lock Task/Screen Pinning do Android. O sistema pode pedir confirmação para fixar ou sair.",
                        checked = settings.lockTask,
                        enabled = settings.enabled,
                        onCheckedChange = { persist(settings.copy(lockTask = it)) },
                    )
                    KioskSwitchRow(
                        title = "Tentar abrir após reiniciar",
                        subtitle = "Solicita abertura do Ponto Café após boot. Android pode restringir inicialização automática em alguns aparelhos.",
                        checked = settings.autoStartAfterBoot,
                        enabled = settings.enabled,
                        onCheckedChange = { persist(settings.copy(autoStartAfterBoot = it)) },
                    )
                }
            }
        }

        item("lock-task") {
            Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                Button(
                    onClick = {
                        runCatching { activity.startLockTask() }
                            .onSuccess { message = "Solicitação para fixar o Ponto Café enviada ao Android." }
                            .onFailure { message = "Não foi possível fixar a app: ${it.message ?: "restrição do Android"}." }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = settings.enabled && settings.lockTask,
                ) { Text("Fixar Ponto Café agora") }
                OutlinedButton(
                    onClick = {
                        runCatching { activity.stopLockTask() }
                        message = "Modo de tela fixada encerrado, quando estava ativo."
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Encerrar tela fixada") }
            }
        }

        item("notes") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.padding(PontoCafeSpacing.md), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Limites de segurança do Android", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Sem provisionar o aparelho como Device Owner, a app usa o modo de fixação permitido a aplicativos comuns. Ela não tenta contornar PIN, biometria, políticas do sistema ou proteções do Samsung Knox.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun KioskSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
