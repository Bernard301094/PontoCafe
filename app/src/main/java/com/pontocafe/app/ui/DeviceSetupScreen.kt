package com.pontocafe.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pontocafe.app.BuildConfig
import com.pontocafe.app.PontoCafeViewModel

private fun looksLikeActivationToken(text: String): Boolean =
    text.length == 10 && text.all { it.isLetterOrDigit() }

@Composable
fun DeviceSetupScreen(
    viewModel: PontoCafeViewModel,
    onAdminClick: () -> Unit = {},
    onSupervisorClick: () -> Unit = {},
) {
    val state = viewModel.state
    DeviceSetupContent(
        carregando = state.carregando,
        erro = state.erro,
        mensagem = state.mensagem,
        onAtivar = viewModel::configurarDispositivo,
        onAdminClick = onAdminClick,
        onSupervisorClick = onSupervisorClick,
    )
}

/**
 * "Vincular este aparelho", com a cara do cartão de ativação do painel.
 *
 * Separado do ViewModel pelo mesmo motivo que os passos do totem: para poder ser
 * desenhado num teste com dados de exemplo e comparado com o painel antes de
 * gerar um APK.
 */
@Composable
internal fun DeviceSetupContent(
    carregando: Boolean,
    erro: String?,
    mensagem: String?,
    onAtivar: (String) -> Unit,
    onAdminClick: () -> Unit,
    onSupervisorClick: () -> Unit,
    tokenInicial: String = "",
) {
    val focusManager = LocalFocusManager.current
    val clipboard = LocalClipboardManager.current
    var token by rememberSaveable { mutableStateOf(tokenInicial) }
    var clipboardSuggestion by remember { mutableStateOf<String?>(null) }
    var focado by remember { mutableStateOf(false) }

    // Sugestão de colar só aparece se o conteúdo da área de transferência já
    // parece um código de ativação válido (10 caracteres alfanuméricos) — não
    // preenche sozinho, é sempre uma sugestão que a pessoa confirma com um toque.
    //
    // O código vem do alfabeto de 62 símbolos, onde `a` e `A` são caracteres
    // diferentes. Normalizar para maiúsculas aqui destruía metade dos códigos
    // colados: o servidor comparava o hash de outra string e devolvia sempre
    // "token inválido", sem nada no ecrã a explicar porquê.
    LaunchedEffect(Unit) {
        val clipped = runCatching { clipboard.getText()?.text?.trim() }.getOrNull()
        if (clipped != null && looksLikeActivationToken(clipped) && clipped != token) {
            clipboardSuggestion = clipped
        }
    }

    fun activate() {
        if (token.length != 10 || carregando) return
        focusManager.clearFocus()
        onAtivar(token)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Painel.stone100),
    ) {
        PainelCabecalho(
            versao = BuildConfig.VERSION_NAME,
            subtitulo = "Configuração inicial do aparelho",
            relogio = null,
            sincronizado = true,
        ) {}

        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val alturaDisponivel = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = alturaDisponivel)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                PainelCartao(modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        PainelSeloIcone(Icons.Outlined.Devices)

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            PainelTitulo("Vincular este aparelho")
                            PainelSubtitulo(
                                "Digite o código de ativação de 10 caracteres que o Administrador gerou em Dispositivos.",
                            )
                        }

                        clipboardSuggestion?.let { suggestion ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Painel.amber50, Painel.cantoXl)
                                    .border(1.dp, Painel.amber200, Painel.cantoXl)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Default.ContentPaste,
                                    contentDescription = null,
                                    tint = Painel.amber800,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    "Código copiado: $suggestion",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Painel.stone700,
                                )
                                Text(
                                    "Colar",
                                    modifier = Modifier
                                        .clickable {
                                            token = suggestion
                                            clipboardSuggestion = null
                                        }
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Painel.coffee900,
                                )
                            }
                        }

                        // O campo do painel: fundo stone-50, código em mono e
                        // espaçado, a borda âmbar enquanto se escreve.
                        BasicTextField(
                            value = token,
                            onValueChange = { value ->
                                token = value.filter { it.isLetterOrDigit() }.take(10)
                                if (token == clipboardSuggestion) clipboardSuggestion = null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { focado = it.isFocused },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp,
                                letterSpacing = 4.sp,
                                textAlign = TextAlign.Center,
                                color = Painel.coffee950,
                            ),
                            cursorBrush = SolidColor(Painel.amber600),
                            // Sem capitalização automática e sem correção: o
                            // teclado do sistema costuma promover a primeira
                            // letra a maiúscula, e num código onde `a` e `A` são
                            // símbolos distintos isso é um erro invisível.
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Ascii,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { activate() }),
                            decorationBox = { campo ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (focado) androidx.compose.ui.graphics.Color.White else Painel.stone50,
                                            Painel.canto2xl,
                                        )
                                        .border(
                                            if (focado) 2.dp else 1.dp,
                                            if (focado) Painel.amber300 else Painel.stone300,
                                            Painel.canto2xl,
                                        )
                                        .padding(horizontal = 16.dp, vertical = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    if (token.isEmpty()) {
                                        Text(
                                            "··········",
                                            style = TextStyle(
                                                fontFamily = JetBrainsMono,
                                                fontSize = 24.sp,
                                                letterSpacing = 4.sp,
                                            ),
                                            color = Painel.stone400,
                                        )
                                    } else {
                                        campo()
                                    }
                                }
                            },
                        )

                        Text(
                            "Letras e números. Maiúsculas e minúsculas contam: ${token.length}/10",
                            style = MaterialTheme.typography.labelSmall,
                            color = Painel.stone400,
                            textAlign = TextAlign.Center,
                        )

                        val aviso = erro ?: mensagem
                        if (aviso != null) {
                            PainelAviso(
                                titulo = if (erro != null) "Atenção" else "Informação",
                                texto = aviso,
                                tom = if (erro != null) PainelTom.VERMELHO else PainelTom.NEUTRO,
                                modifier = Modifier.semantics {
                                    liveRegion = if (erro != null) LiveRegionMode.Assertive else LiveRegionMode.Polite
                                },
                            )
                        }

                        PainelBotaoPrimario(
                            texto = "Ativar este aparelho",
                            onClick = ::activate,
                            enabled = token.length == 10,
                            loading = carregando,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Text(
                            "O código curto vale uma vez só. Depois deste passo o Android guarda uma credencial longa e protegida.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Painel.stone400,
                            textAlign = TextAlign.Center,
                        )

                        HorizontalDivider(color = Painel.stone200)

                        // Gestão pode entrar mesmo antes de o aparelho ser ativado.
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            LinkDiscreto("Entrar como Administrador", onAdminClick)
                            LinkDiscreto("Entrar como Supervisor", onSupervisorClick)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkDiscreto(texto: String, onClick: () -> Unit) {
    Text(
        texto,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = Painel.stone500,
    )
}
