package com.pontocafe.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pontocafe.app.data.AccessCodeCreatedResponse
import com.pontocafe.app.data.AccessCodeItem
import com.pontocafe.app.data.Colaborador
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/**
 * Estado de um passe, do ponto de vista de quem opera a tela.
 *
 * A API devolve `estado` como texto; traduzi-lo uma vez aqui evita que cada
 * parte da interface refaça a mesma comparação de strings e chegue a
 * conclusões diferentes sobre o que oferecer.
 */
internal enum class AccessCodeState { NENHUM, AGUARDANDO_SAIDA, EM_PAUSA, EXPIRADO }

internal fun AccessCodeItem?.state(): AccessCodeState = when {
    this == null -> AccessCodeState.NENHUM
    estado == "EM_PAUSA" -> AccessCodeState.EM_PAUSA
    estado == "AGUARDANDO_SAIDA" -> AccessCodeState.AGUARDANDO_SAIDA
    else -> AccessCodeState.EXPIRADO
}

internal fun AccessCodeState.label(): String = when (this) {
    AccessCodeState.NENHUM -> "Sem código"
    AccessCodeState.AGUARDANDO_SAIDA -> "Aguardando saída"
    AccessCodeState.EM_PAUSA -> "Em pausa"
    AccessCodeState.EXPIRADO -> "Código expirado"
}

internal fun AccessCodeState.tone(): PontoCafeTone = when (this) {
    AccessCodeState.NENHUM -> PontoCafeTone.NEUTRAL
    AccessCodeState.AGUARDANDO_SAIDA -> PontoCafeTone.SUCCESS
    AccessCodeState.EM_PAUSA -> PontoCafeTone.INFO
    AccessCodeState.EXPIRADO -> PontoCafeTone.WARNING
}

/**
 * Emissão do código de acesso ao café.
 *
 * Uma tela, usada por Admin e Supervisor: procurar a pessoa, tocar em GERAR, e
 * ditar os seis caracteres que aparecem em letras grandes. Quem já tem passe
 * vivo sobe para o topo da lista — é o que o Supervisor precisa ver primeiro
 * para saber quem ainda não voltou.
 */
@Composable
fun AccessCodeScreen(
    colaboradores: List<Colaborador>,
    codigosAtivos: List<AccessCodeItem>,
    codigoEmitido: AccessCodeCreatedResponse?,
    carregando: Boolean,
    /**
     * Quem tem uma emissão em curso. Nulo quando não há nenhuma.
     *
     * [carregando] é de tela inteira: usá-lo em cada linha desativava os botões
     * "Gerar" de toda a lista ao emitir para uma pessoa só, e o efeito é o de
     * todos terem sido premidos ao mesmo tempo.
     */
    ocupadoId: String?,
    erro: String?,
    onGerar: (Colaborador, String?) -> Unit,
    onCancelar: (Colaborador) -> Unit,
    onFechar: () -> Unit,
    onAtualizar: () -> Unit,
) {
    var seletorAberto by remember { mutableStateOf(false) }
    var alvo by remember { mutableStateOf<Colaborador?>(null) }

    // A lista de códigos vivos envelhece sozinha (expira, alguém sai, alguém
    // volta). Sem este refresh o Supervisor ficaria a olhar para um estado
    // antigo enquanto decide se emite outro.
    //
    // Preso ao ciclo de vida: sem isto o laço continuava a bater no servidor de
    // vinte em vinte segundos com o app em segundo plano, a gastar bateria e
    // rede para atualizar uma tela que ninguém está a ver.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(20_000L)
                onAtualizar()
            }
        }
    }

    val porColaborador = remember(codigosAtivos) { codigosAtivos.associateBy { it.colaboradorId } }

    // Quem pode receber um passe agora. Sai daqui quem já fechou a pausa deste
    // período — não pode tomar outro café até o próximo — e quem está no café
    // neste momento, porque emitir um segundo código vivo deixaria em aberto
    // qual deles fecha a pausa. Os dois voltam sozinhos: um no próximo período,
    // o outro ao registar o retorno.
    val disponiveis = remember(colaboradores) {
        colaboradores
            .filter { !it.pausaPeriodoConcluida && !it.emPausa }
            .sortedBy { it.nome.lowercase() }
    }

    // A lista fixa da tela é a dos passes vivos, e só ela: é o que muda sozinho
    // e o que exige uma decisão agora. Quem voltou some daqui no refresh
    // seguinte, porque o servidor deixa de devolver o código.
    val vivos = remember(codigosAtivos, colaboradores) {
        val porId = colaboradores.associateBy { it.id }
        codigosAtivos
            .map { item ->
                item to (
                    porId[item.colaboradorId]
                        ?: Colaborador(
                            id = item.colaboradorId,
                            nome = item.nome,
                            setor = item.setor,
                            turno = item.turno,
                        )
                    )
            }
            .sortedWith(
                compareBy<Pair<AccessCodeItem, Colaborador>> {
                    if (it.first.estado == "EM_PAUSA") 0 else 1
                }.thenBy { it.second.nome.lowercase() },
            )
    }

    val emPausaAgora = codigosAtivos.count { it.estado == "EM_PAUSA" }
    val aguardando = codigosAtivos.count { it.estado == "AGUARDANDO_SAIDA" }

    if (seletorAberto) {
        PcCollaboratorPickerSheet(
            pessoas = disponiveis,
            titulo = "Gerar código para",
            onDismiss = { seletorAberto = false },
            onEscolher = { pessoa ->
                seletorAberto = false
                alvo = pessoa
            },
            vazioTitulo = "Ninguém disponível agora",
            vazioTexto = "Ou todos já tomaram café neste período, ou os que faltam estão no café " +
                "neste momento. A lista se refaz sozinha no próximo período.",
        )
    }

    alvo?.let { colaborador ->
        PcIssueCodeDialog(
            colaborador = colaborador,
            carregando = carregando,
            onDismiss = { alvo = null },
            onConfirm = { motivo ->
                alvo = null
                onGerar(colaborador, motivo)
            },
        )
    }

    codigoEmitido?.let { emitido ->
        PcIssuedCodeDialog(codigo = emitido, onDismiss = onFechar)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = PontoCafeSpacing.md),
        verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
    ) {
        Column(
            modifier = Modifier.padding(top = PontoCafeSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                "Códigos de café",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                "$aguardando aguardando saída · $emPausaAgora no café agora · " +
                    "${disponiveis.size} disponíveis",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        erro?.let {
            PcStateBanner(
                title = "Não foi possível concluir",
                supportingText = it,
                tone = PontoCafeTone.DANGER,
            )
        }

        // Seletor fechado em vez dos noventa e seis nomes abertos. A busca vive
        // dentro da folha, onde há uma lista para ela filtrar.
        PcCollaboratorPickerField(
            selecionado = null,
            placeholder = "Escolher pessoa e gerar código",
            onClick = { seletorAberto = true },
            enabled = !carregando && disponiveis.isNotEmpty(),
            grande = true,
        )

        if (vivos.isEmpty()) {
            PcEmptyState(
                title = "Nenhum código vivo",
                supportingText = if (disponiveis.isEmpty() && colaboradores.isEmpty()) {
                    "Cadastre os colaboradores antes de gerar códigos."
                } else {
                    "Escolha uma pessoa acima para emitir o primeiro."
                },
                icon = Icons.Default.Coffee,
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
                contentPadding = PaddingValues(bottom = PontoCafeSpacing.lg),
            ) {
                items(vivos, key = { it.first.id }) { (codigo, pessoa) ->
                    AccessCodeRow(
                        colaborador = pessoa,
                        codigo = codigo,
                        ocupado = ocupadoId == pessoa.id,
                        onGerar = { alvo = pessoa },
                        onCancelar = { onCancelar(pessoa) },
                    )
                }
            }
        }
    }
}

/**
 * Uma linha da lista de códigos.
 *
 * Densa de propósito: numa operação com quase cem pessoas, um cartão alto por
 * pessoa transforma a procura de quem ainda não voltou numa rolagem longa.
 */
@Composable
private fun AccessCodeRow(
    colaborador: Colaborador,
    codigo: AccessCodeItem?,
    // Desta pessoa, e não da tela: só a linha de quem está a receber o código
    // é que reage.
    ocupado: Boolean,
    onGerar: () -> Unit,
    onCancelar: () -> Unit,
) {
    val estado = codigo.state()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = PontoCafeSpacing.sm, vertical = PontoCafeSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
            ) {
                InitialAvatar(name = colaborador.nome, avatarSize = 40.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        colaborador.nome,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        colaboradorDetalhe(colaborador),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (estado == AccessCodeState.NENHUM || estado == AccessCodeState.EXPIRADO) {
                    PcCompactAction(
                        text = "Gerar",
                        icon = Icons.Default.Coffee,
                        onClick = onGerar,
                        enabled = !ocupado,
                        contentDescription = "Gerar código para ${colaborador.nome}",
                    )
                } else {
                    StatusPill(text = estado.label(), tone = estado.tone())
                }
            }

            if (codigo != null && estado != AccessCodeState.EXPIRADO) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
                ) {
                    AccessCodeChip(codigo.codigoFormatado)
                    Text(
                        if (estado == AccessCodeState.EM_PAUSA) {
                            "Válido para o retorno, sem prazo"
                        } else {
                            "Expira em ${expiracaoCurta(codigo.expiraEmSegundos)}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (estado == AccessCodeState.AGUARDANDO_SAIDA) {
                        TextButton(onClick = onCancelar, enabled = !ocupado) { Text("Cancelar") }
                        // Emitir enquanto a pessoa está fora criaria um segundo
                        // código vivo e deixaria em aberto qual deles fecha a
                        // pausa. O servidor recusa; a interface nem oferece.
                        PcCompactAction(
                            text = "Outro",
                            icon = Icons.Default.Coffee,
                            onClick = onGerar,
                            enabled = !ocupado,
                            contentDescription = "Gerar outro código para ${colaborador.nome}",
                        )
                    }
                }
            }
        }
    }
}

/**
 * Atalho de emissão para telas que não são a de códigos.
 *
 * Existe porque o caminho real do Supervisor começa no Início: alguém pede café
 * na frente dele. Obrigá-lo a navegar até outra área para o gesto mais
 * frequente do dia é o que tornava a tela inicial decorativa.
 */
@Composable
internal fun AccessCodeQuickIssueCard(
    colaboradores: List<Colaborador>,
    codigosAtivos: List<AccessCodeItem>,
    carregando: Boolean,
    /** Quem tem emissão em curso. Ver [AccessCodeScreen]. */
    ocupadoId: String?,
    erro: String?,
    maxResultados: Int,
    onGerar: (Colaborador) -> Unit,
    onVerTodos: () -> Unit,
    onTentarNovamente: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var busca by remember { mutableStateOf("") }
    val termo = busca.trim()
    val porColaborador = remember(codigosAtivos) { codigosAtivos.associateBy { it.colaboradorId } }

    val resultados = remember(termo, colaboradores, porColaborador) {
        if (termo.isEmpty()) {
            // Sem busca, a lista útil não é "as primeiras pessoas do alfabeto":
            // é quem tem passe vivo agora, que é onde há decisão a tomar.
            colaboradores
                .filter { porColaborador[it.id].state() != AccessCodeState.NENHUM }
                .sortedBy { it.nome.lowercase() }
        } else {
            // Buscar só oferece quem pode receber um passe agora: quem já fechou
            // a pausa do período não pode tomar outro café até o próximo, e quem
            // está no café tem um código vivo que ainda vai fechar a pausa.
            colaboradores
                .filter { !it.pausaPeriodoConcluida && !it.emPausa }
                .filter {
                    it.nome.contains(termo, ignoreCase = true) ||
                        it.setor.orEmpty().contains(termo, ignoreCase = true)
                }
        }
    }
    val visiveis = resultados.take(maxResultados)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(PontoCafeSpacing.md),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(
                        Icons.Default.Bolt,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(PontoCafeSpacing.xs)
                            .size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Gerar código de café",
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Busque a pessoa e toque em Gerar. O código aparece em letras grandes para ditar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (carregando) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }

            OutlinedTextField(
                value = busca,
                onValueChange = { busca = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Nome ou setor") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (busca.isNotBlank()) {
                        IconButton(onClick = { busca = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Limpar busca")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Search,
                ),
            )

            when {
                erro != null -> {
                    PcStateBanner(
                        title = "Lista indisponível",
                        supportingText = erro,
                        tone = PontoCafeTone.WARNING,
                    )
                    PcSecondaryButton(
                        text = "Tentar novamente",
                        onClick = onTentarNovamente,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                visiveis.isEmpty() -> Text(
                    if (termo.isEmpty()) {
                        "Nenhum código vivo agora. Digite um nome para gerar."
                    } else {
                        "Ninguém corresponde a esta busca."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xxs)) {
                    visiveis.forEach { pessoa ->
                        AccessCodeQuickRow(
                            colaborador = pessoa,
                            codigo = porColaborador[pessoa.id],
                            ocupado = ocupadoId == pessoa.id,
                            onGerar = { onGerar(pessoa) },
                        )
                    }
                    if (resultados.size > visiveis.size) {
                        Text(
                            "… e mais ${resultados.size - visiveis.size}. Refine a busca ou abra a lista completa.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            PcSecondaryButton(
                text = "Abrir códigos de café",
                onClick = onVerTodos,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.Coffee,
            )
        }
    }
}

@Composable
private fun AccessCodeQuickRow(
    colaborador: Colaborador,
    codigo: AccessCodeItem?,
    ocupado: Boolean,
    onGerar: () -> Unit,
) {
    val estado = codigo.state()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PontoCafeDimensions.minimumTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
    ) {
        InitialAvatar(name = colaborador.nome, avatarSize = 34.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                colaborador.nome,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when (estado) {
                    AccessCodeState.EM_PAUSA -> "No café · código válido para o retorno"
                    AccessCodeState.AGUARDANDO_SAIDA ->
                        "${codigo?.codigoFormatado.orEmpty()} · expira em ${expiracaoCurta(codigo?.expiraEmSegundos ?: 0)}"
                    else -> colaboradorDetalhe(colaborador)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (estado == AccessCodeState.EM_PAUSA) {
            StatusPill(text = "Em pausa", tone = PontoCafeTone.INFO)
        } else {
            PcCompactAction(
                text = if (estado == AccessCodeState.AGUARDANDO_SAIDA) "Outro" else "Gerar",
                icon = Icons.Default.Coffee,
                onClick = onGerar,
                enabled = !ocupado,
                contentDescription = "Gerar código para ${colaborador.nome}",
            )
        }
    }
}

/**
 * Botão pequeno para a ação de uma linha — alto o bastante para o dedo, curto o
 * bastante para caber ao lado do nome sem empurrar a lista para baixo.
 */
@Composable
internal fun PcCompactAction(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 40.dp)
            .semantics { this.contentDescription = contentDescription },
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(horizontal = PontoCafeSpacing.sm, vertical = 0.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
        Text(
            text,
            modifier = Modifier.padding(start = 5.dp),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

@Composable
internal fun AccessCodeChip(codigoFormatado: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Text(
            codigoFormatado,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = PontoCafeSpacing.xs, vertical = PontoCafeSpacing.xxs),
        )
    }
}

internal fun colaboradorDetalhe(colaborador: Colaborador): String = listOfNotNull(
    colaborador.setor?.takeIf { it.isNotBlank() },
    colaborador.turno?.takeIf { it.isNotBlank() }?.let { "Turno $it" },
).joinToString(" · ").ifBlank { "Sem setor definido" }

/**
 * "Expira em 0 min" é o pior texto possível para uma janela de dois minutos:
 * quem lê conclui que já perdeu o código quando ainda tem 50 segundos.
 */
internal fun expiracaoCurta(segundos: Int): String = when {
    segundos <= 0 -> "instantes"
    segundos < 60 -> "$segundos s"
    else -> "${segundos / 60} min"
}

@Composable
internal fun PcIssueCodeDialog(
    colaborador: Colaborador,
    carregando: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var motivo by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!carregando) onDismiss() },
        title = { Text("Gerar código para ${colaborador.nome}") },
        text = {
            PcDialogBody {
                Text(
                    "O código vale uma vez na saída e uma vez no retorno, e só serve para esta pessoa.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = motivo,
                    onValueChange = { motivo = it.take(300) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Motivo (opcional)") },
                    supportingText = { Text("Preencha só quando houver algo a registrar.") },
                    singleLine = false,
                    minLines = 2,
                    enabled = !carregando,
                )
            }
        },
        confirmButton = {
            PcPrimaryButton(
                text = "Gerar",
                onClick = { onConfirm(motivo.trim().ifBlank { null }) },
                enabled = !carregando,
                loading = carregando,
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !carregando) { Text("Cancelar") }
        },
    )
}

/**
 * O código em letras grandes.
 *
 * Este diálogo existe porque o Supervisor vai LER o código em voz alta para
 * alguém do outro lado do balcão. Um chip discreto na lista não serve para isso.
 */
@Composable
internal fun PcIssuedCodeDialog(
    codigo: AccessCodeCreatedResponse,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Código de ${codigo.colaboradorNome}") },
        text = {
            PcDialogBody {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(16.dp),
                        )
                        .border(
                            2.dp,
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(16.dp),
                        )
                        .semantics {
                            contentDescription =
                                "Código ${codigo.codigo.toCharArray().joinToString(", ")}"
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        codigo.codigoFormatado,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 44.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Text(
                    "Vale por ${expiracaoCurta(codigo.expiraEmSegundos)} para a SAÍDA. Depois de sair, " +
                        "o mesmo código continua válido para o RETORNO, sem prazo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (codigo.carenciaSegundos > 0) {
                    Text(
                        "A contagem do café começa ${codigo.carenciaSegundos / 60} minuto(s) depois do registro da saída.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { clipboard.setText(AnnotatedString(codigo.codigo)) }
                        .padding(vertical = PontoCafeSpacing.xxs),
                ) {
                    IconButton(onClick = { clipboard.setText(AnnotatedString(codigo.codigo)) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copiar código")
                    }
                    Text("Copiar código", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            PcPrimaryButton(text = "Pronto", onClick = onDismiss)
        },
    )
}
