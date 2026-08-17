package com.pontocafe.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pontocafe.app.PontoCafeViewModel

private val IdentityMint = Color(0xFF79E5C2)
private val IdentityMintSoft = Color(0xFF9AF1D4)
private val IdentityWarning = Color(0xFFFFB35C)
private val IdentityBlocked = Color(0xFFFF8A80)
private val IdentityBlockedSurface = Color(0x331F0909)

@Composable
fun IdentityConfirmationScreen(viewModel: PontoCafeViewModel) {
    val state = viewModel.state
    val identificacao = state.identificacao ?: return
    val colaborador = identificacao.colaborador ?: return
    val finalizando = identificacao.acaoSugerida == "FINALIZAR"
    val pausaJaUtilizada = identificacao.motivo == "PAUSA_PERIODO_JA_UTILIZADA" ||
        identificacao.acaoSugerida == "BLOQUEADO"
    val liberadaForaHorario = !finalizando && !pausaJaUtilizada && identificacao.motivo == "AUTORIZACAO_PREVIA"
    val bloqueadaForaHorario = !finalizando && !pausaJaUtilizada &&
        identificacao.dentroHorario != true && !liberadaForaHorario
    val detalhe = listOfNotNull(colaborador.setor, colaborador.turno)
        .filter { it.isNotBlank() }
        .joinToString(" · ")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF071713), Color(0xFF06100E), Color(0xFF030706)),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MotionReveal { IdentityBrand() }
            Spacer(Modifier.height(22.dp))
            MotionReveal {
                RecognitionBadge(
                    finalizando = finalizando,
                    pausaJaUtilizada = pausaJaUtilizada,
                )
            }
            Spacer(Modifier.height(22.dp))
            MotionReveal { VerifiedIdentityAvatar() }
            Spacer(Modifier.height(18.dp))

            MotionReveal {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = colaborador.nome,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.headlineLarge,
                        color = PontoCafePremium.textPrimary,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (detalhe.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = detalhe,
                            style = MaterialTheme.typography.titleMedium,
                            color = IdentityMint,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            Spacer(Modifier.height(26.dp))
            MotionReveal {
                IdentityStatusCard(
                    finalizando = finalizando,
                    pausaJaUtilizada = pausaJaUtilizada,
                    liberadaForaHorario = liberadaForaHorario,
                    bloqueadaForaHorario = bloqueadaForaHorario,
                    periodo = identificacao.periodoAtual,
                    mensagem = identificacao.mensagem,
                    inicioLocal = identificacao.pausaAberta?.inicioLocal,
                    tempoDecorrido = identificacao.pausaAberta?.tempoDecorridoSegundos?.let(viewModel::formatarTempo),
                )
            }

            state.erro?.let { error ->
                Spacer(Modifier.height(12.dp))
                ErrorCard(error)
            }

            Spacer(Modifier.height(18.dp))
            IdentityActions(
                finalizando = finalizando,
                pausaJaUtilizada = pausaJaUtilizada,
                liberadaForaHorario = liberadaForaHorario,
                bloqueadaForaHorario = bloqueadaForaHorario,
                loading = state.carregando,
                onReject = viewModel::rejeitarIdentidade,
                onConfirm = viewModel::confirmarIdentidade,
            )
        }
    }
}

@Composable
private fun IdentityBrand() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(CircleShape).border(1.5.dp, IdentityMint, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Check, null, tint = IdentityMint, modifier = Modifier.size(19.dp))
        }
        Column {
            Text("PONTO", style = MaterialTheme.typography.titleMedium, color = IdentityMintSoft, fontWeight = FontWeight.Medium)
            Text("C A F É", style = MaterialTheme.typography.labelMedium, color = IdentityMint, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun RecognitionBadge(finalizando: Boolean, pausaJaUtilizada: Boolean) {
    val accent = if (pausaJaUtilizada) IdentityWarning else IdentityMint
    Surface(
        color = Color(0xB3132822),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.34f)),
        shadowElevation = 5.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Icon(
                imageVector = if (pausaJaUtilizada) Icons.Default.Info else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = when {
                    pausaJaUtilizada -> "Tentativa registrada"
                    finalizando -> "Retorno identificado"
                    else -> "Rosto identificado"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = accent,
            )
        }
    }
}

@Composable
private fun VerifiedIdentityAvatar() {
    Box(modifier = Modifier.size(126.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(116.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFF213630), Color(0xFF101B18))))
                .border(1.5.dp, IdentityMint.copy(alpha = 0.78f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Person, null, tint = Color(0xFF82948E), modifier = Modifier.size(76.dp))
        }
        Surface(
            modifier = Modifier.size(40.dp).align(Alignment.BottomEnd),
            shape = CircleShape,
            color = IdentityMint,
            shadowElevation = 8.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, "Identidade confirmada", tint = Color(0xFF05251D), modifier = Modifier.size(23.dp))
            }
        }
    }
}

@Composable
private fun IdentityStatusCard(
    finalizando: Boolean,
    pausaJaUtilizada: Boolean,
    liberadaForaHorario: Boolean,
    bloqueadaForaHorario: Boolean,
    periodo: String?,
    mensagem: String?,
    inicioLocal: String?,
    tempoDecorrido: String?,
) {
    val accent = when {
        pausaJaUtilizada -> IdentityWarning
        bloqueadaForaHorario -> IdentityBlocked
        else -> IdentityMint
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xE512211D),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.30f)),
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = when {
                    finalizando -> "Confirmar retorno"
                    pausaJaUtilizada -> "Pausa da ${periodLabel(periodo).lowercase()} já utilizada"
                    bloqueadaForaHorario -> "Pausa não liberada"
                    liberadaForaHorario -> "Pausa liberada"
                    else -> "É você?"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = PontoCafePremium.textPrimary,
            )

            when {
                pausaJaUtilizada -> {
                    Text(
                        text = mensagem ?: "Você já realizou esta pausa hoje. Uma segunda saída não será aberta.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = PontoCafePremium.textSecondary,
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = IdentityWarning.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, IdentityWarning.copy(alpha = 0.34f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Default.Info, null, tint = IdentityWarning)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Nova pausa bloqueada",
                                    color = IdentityWarning,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    "A tentativa ficou registrada para consulta e auditoria do Supervisor/Administrador.",
                                    color = PontoCafePremium.textSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
                finalizando -> {
                    Text("É você e deseja registrar seu retorno agora?", color = PontoCafePremium.textSecondary)
                    Text(
                        "Início: ${inicioLocal ?: "--:--"}${tempoDecorrido?.let { " · Tempo: $it" } ?: ""}",
                        color = IdentityMint,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                bloqueadaForaHorario -> {
                    Text(
                        "Você está fora do horário permitido e não possui uma liberação prévia ativa. Procure seu Supervisor.",
                        color = PontoCafePremium.textSecondary,
                    )
                }
                liberadaForaHorario -> {
                    Text(
                        "Sua pausa foi liberada previamente pelo Supervisor. Período: ${periodLabel(periodo)} · uso único.",
                        color = PontoCafePremium.textSecondary,
                    )
                }
                else -> {
                    Text(
                        "Ao confirmar, o início da sua pausa será registrado imediatamente.",
                        color = PontoCafePremium.textSecondary,
                    )
                    Text(
                        "Você está dentro do horário permitido.",
                        color = IdentityMint,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(error: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.84f),
        shape = RoundedCornerShape(18.dp),
    ) {
        Text(
            text = error,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun IdentityActions(
    finalizando: Boolean,
    pausaJaUtilizada: Boolean,
    liberadaForaHorario: Boolean,
    bloqueadaForaHorario: Boolean,
    loading: Boolean,
    onReject: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (pausaJaUtilizada || bloqueadaForaHorario) {
        Button(
            onClick = onReject,
            modifier = Modifier.fillMaxWidth().height(62.dp),
            enabled = !loading,
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.buttonColors(containerColor = IdentityMint, contentColor = Color(0xFF05251D)),
        ) {
            Text("Voltar ao Ponto", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        return
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = onReject,
            modifier = Modifier.weight(1f).height(62.dp),
            enabled = !loading,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, IdentityMint.copy(alpha = 0.58f)),
        ) {
            Text("Não sou eu", fontWeight = FontWeight.SemiBold)
        }
        Button(
            onClick = onConfirm,
            modifier = Modifier.weight(1f).height(62.dp),
            enabled = !loading,
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.buttonColors(containerColor = IdentityMint, contentColor = Color(0xFF05251D)),
        ) {
            Text(
                text = when {
                    loading && finalizando -> "Finalizando..."
                    loading -> "Iniciando..."
                    finalizando -> "Finalizar pausa"
                    liberadaForaHorario -> "Iniciar pausa"
                    else -> "Sim, sou eu"
                },
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun periodLabel(period: String?): String = when (period) {
    "MANHA" -> "Manhã"
    "TARDE" -> "Tarde"
    else -> "Exceção"
}
