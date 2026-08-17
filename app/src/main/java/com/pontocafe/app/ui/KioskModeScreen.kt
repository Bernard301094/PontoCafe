package com.pontocafe.app.ui

import android.view.WindowManager
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
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
    val listState = rememberLazyListState()

    fun persist(next: KioskModeSettings) {
        settings = next
        store.save(next)
        if (next.enabled && next.keepScreenOn) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    PontoCafeResponsivePage(maxContentWidth = 840.dp) { responsive ->
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
                        title = "Modo terminal",
                        eyebrow = "Este dispositivo",
                        onBack = onBack,
                    )
                }

                item("hero") {
                    PcHeroCard(
                        title = if (settings.enabled) "Terminal dedicado ativo" else "Modo terminal desativado",
                        supportingText = if (settings.enabled) {
                            "Este aparelho está configurado para operar como Ponto Café. O acesso Admin/Supervisor continua protegido."
                        } else {
                            "Ative somente em aparelhos dedicados ao Ponto Café. Nenhuma proteção do Android é contornada."
                        },
                        icon = Icons.Default.PhoneAndroid,
                        tone = if (settings.enabled) PontoCafeTone.SUCCESS else PontoCafeTone.INFO,
                    )
                }

                item("toggle-main") {
                    PcSectionSurface {
                        KioskSwitchRow(
                            title = "Ativar modo terminal",
                            subtitle = "Marca este aparelho como dedicado ao Ponto Café.",
                            checked = settings.enabled,
                            onCheckedChange = { persist(settings.copy(enabled = it)) },
                        )
                    }
                }

                message?.let { text ->
                    item("message") {
                        PcStateBanner(
                            title = "Ação do Android",
                            supportingText = text,
                            tone = PontoCafeTone.INFO,
                        )
                    }
                }

                item("settings-title") {
                    SectionTitle(
                        "Comportamento do terminal",
                        if (settings.enabled) {
                            "Escolha somente os recursos necessários para este aparelho."
                        } else {
                            "Ative o modo terminal para liberar estas opções."
                        },
                    )
                }

                item("settings") {
                    PcSectionSurface {
                        Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md)) {
                            KioskSwitchRow(
                                title = "Manter tela ligada",
                                subtitle = "Evita que a tela durma enquanto o terminal estiver em operação.",
                                checked = settings.keepScreenOn,
                                enabled = settings.enabled,
                                onCheckedChange = { persist(settings.copy(keepScreenOn = it)) },
                            )
                            Divider(color = MaterialTheme.colorScheme.outlineVariant)
                            KioskSwitchRow(
                                title = "Fixar aplicativo na tela",
                                subtitle = "Usa Lock Task/Screen Pinning. O Android pode pedir confirmação para fixar ou sair.",
                                checked = settings.lockTask,
                                enabled = settings.enabled,
                                onCheckedChange = { persist(settings.copy(lockTask = it)) },
                            )
                            Divider(color = MaterialTheme.colorScheme.outlineVariant)
                            KioskSwitchRow(
                                title = "Tentar abrir após reiniciar",
                                subtitle = "Solicita abertura do Ponto Café após o boot, sujeito às restrições do Android/Samsung.",
                                checked = settings.autoStartAfterBoot,
                                enabled = settings.enabled,
                                onCheckedChange = { persist(settings.copy(autoStartAfterBoot = it)) },
                            )
                        }
                    }
                }

                item("lock-title") {
                    SectionTitle(
                        "Tela fixada",
                        "A fixação mantém o Ponto Café em primeiro plano usando os recursos permitidos pelo Android.",
                    )
                }

                item("lock-task") {
                    Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                        PcPrimaryButton(
                            text = "Fixar Ponto Café agora",
                            icon = Icons.Default.Lock,
                            onClick = {
                                runCatching { activity.startLockTask() }
                                    .onSuccess { message = "Solicitação para fixar o Ponto Café enviada ao Android." }
                                    .onFailure { message = "Não foi possível fixar a app: ${it.message ?: "restrição do Android"}." }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = settings.enabled && settings.lockTask,
                        )
                        PcSecondaryButton(
                            text = "Encerrar tela fixada",
                            onClick = {
                                runCatching { activity.stopLockTask() }
                                message = "Modo de tela fixada encerrado, quando estava ativo."
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                item("notes") {
                    PcStateBanner(
                        title = "Limites de segurança do Android",
                        supportingText = "Sem provisionamento como Device Owner, a app usa somente a fixação permitida a aplicativos comuns e não contorna PIN, biometria, políticas do sistema ou Samsung Knox.",
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
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.55f),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}
