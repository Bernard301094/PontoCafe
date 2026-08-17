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
private val IdentityWarningSurface = Color(0x33291508)

@Composable
fun IdentityConfirmationScreen(viewModel: PontoCafeViewModel) {
    val state = viewModel.state
    val identificacao = state.identificacao ?: return
    val colaborador = identificacao.colaborador ?: return
    val finalizando = identificacao.acaoSugerida == "FINALIZAR"
    val foraDoHorario = !finalizando && identificacao.dentroHorario != true
    val detalhe = listOfNotNull(colaborador.setor, colaborador.turno)
        .filter { it.isNotBlank() }
        .joinToString(" · ")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF071713),
                        Color(0xFF06100E),
                        Color(0xFF030706),
                    ),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .size(320.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            IdentityMint.copy(alpha = 0.10f),
                            Color.Transparent,
                        ),
                    ),
                    CircleShape,
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 22.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MotionReveal {
                PontoCafeIdentityBrand()
            }

            Spacer(Modifier.height(22.dp))

            MotionReveal {
                RecognitionBadge(finalizando = finalizando)
            }

            Spacer(Modifier.height(22.dp))

            MotionReveal {
                VerifiedIdentityAvatar()
            }

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
                IdentityQuestionCard(
                    finalizando = finalizando,
                    foraDoHorario = foraDoHorario,
                    inicioLocal = identificacao.pausaAberta?.inicioLocal,
                    tempoDecorrido = identificacao.pausaAberta?.tempoDecorridoSegundos?.let(viewModel::formatarTempo),
                )
            }

            state.erro?.let { error ->
                Spacer(Modifier.height(12.dp))
                MotionReveal {
                    IdentityErrorCard(error)
                }
            }

            Spacer(Modifier.height(18.dp))

            MotionReveal {
                IdentityActions(
                    finalizando = finalizando,
                    loading = state.carregando,
                    onReject = viewModel::rejeitarIdentidade,
                    onConfirm = viewModel::confirmarIdentidade,
                )
            }
        }
    }
}

@Composable
private fun PontoCafeIdentityBrand() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .border(1.5.dp, IdentityMint, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = IdentityMint,
                modifier = Modifier.size(19.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Text(
                text = "PONTO",
                style = MaterialTheme.typography.titleMedium,
                color = IdentityMintSoft,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "C A F É",
                style = MaterialTheme.typography.labelMedium,
                color = IdentityMint,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun RecognitionBadge(finalizando: Boolean) {
    Surface(
        color = Color(0xB3132822),
        contentColor = IdentityMint,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, IdentityMint.copy(alpha = 0.34f)),
        shadowElevation = 5.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = IdentityMint,
            )
            Text(
                text = if (finalizando) "Retorno identificado" else "Rosto identificado",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = IdentityMint,
            )
        }
    }
}

@Composable
private fun VerifiedIdentityAvatar() {
    Box(
        modifier = Modifier.size(126.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(116.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF213630),
                            Color(0xFF101B18),
                        ),
                    ),
                )
                .border(1.5.dp, IdentityMint.copy(alpha = 0.78f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = Color(0xFF82948E),
                modifier = Modifier.size(76.dp),
            )
        }

        Surface(
            modifier = Modifier
                .size(40.dp)
                .align(Alignment.BottomEnd),
            shape = CircleShape,
            color = IdentityMint,
            contentColor = Color(0xFF05251D),
            shadowElevation = 8.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Identidade confirmada",
                    modifier = Modifier.size(23.dp),
                    tint = Color(0xFF05251D),
                )
            }
        }
    }
}

@Composable
private fun IdentityQuestionCard(
    finalizando: Boolean,
    foraDoHorario: Boolean,
    inicioLocal: String?,
    tempoDecorrido: String?,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xE512211D),
        contentColor = PontoCafePremium.textPrimary,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, IdentityMint.copy(alpha = 0.24f)),
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = IdentityMint.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, IdentityMint.copy(alpha = 0.52f)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = IdentityMint,
                            modifier = Modifier.size(25.dp),
                        )
                    }
                }
                Text(
                    text = if (finalizando) "Confirmar retorno" else "É você?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = PontoCafePremium.textPrimary,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.16f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(IdentityMint),
            )

            if (finalizando) {
                Text(
                    text = "É você e deseja registrar seu retorno agora?",
                    style = MaterialTheme.typography.bodyLarge,
                    color = PontoCafePremium.textSecondary,
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0x99101A17),
                    border = BorderStroke(1.dp, PontoCafePremium.borderSoft),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Pausa em andamento",
                            style = MaterialTheme.typography.labelLarge,
                            color = IdentityMint,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Início registrado às ${inicioLocal ?: "--:--"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PontoCafePremium.textPrimary,
                        )
                        tempoDecorrido?.let {
                            Text(
                                text = "Tempo decorrido: $it",
                                style = MaterialTheme.typography.bodyMedium,
                                color = PontoCafePremium.textSecondary,
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = if (foraDoHorario) {
                        "Ao confirmar, será solicitada a autorização temporária do supervisor porque você está fora do horário normal."
                    } else {
                        "Ao confirmar, o início da sua pausa será registrado imediatamente."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = PontoCafePremium.textSecondary,
                )

                if (foraDoHorario) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = IdentityWarningSurface,
                        border = BorderStroke(1.dp, IdentityWarning.copy(alpha = 0.38f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(25.dp),
                                tint = IdentityWarning,
                            )
                            Text(
                                text = "Você está fora do seu horário normal de trabalho.",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFFFC77F),
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IdentityErrorCard(error: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.84f),
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.28f)),
    ) {
        Text(
            text = error,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun IdentityActions(
    finalizando: Boolean,
    loading: Boolean,
    onReject: () -> Unit,
    onConfirm: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = onReject,
            modifier = Modifier
                .weight(1f)
                .height(62.dp),
            enabled = !loading,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, IdentityMint.copy(alpha = 0.58f)),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = PontoCafePremium.textPrimary,
                disabledContentColor = PontoCafePremium.textSecondary.copy(alpha = 0.48f),
            ),
        ) {
            Text(
                text = "Não sou eu",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Button(
            onClick = onConfirm,
            modifier = Modifier
                .weight(1f)
                .height(62.dp),
            enabled = !loading,
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = IdentityMint,
                contentColor = Color(0xFF05251D),
                disabledContainerColor = IdentityMint.copy(alpha = 0.40f),
                disabledContentColor = Color(0xFF05251D).copy(alpha = 0.55f),
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 8.dp,
                pressedElevation = 2.dp,
            ),
        ) {
            Text(
                text = when {
                    loading && finalizando -> "Finalizando..."
                    loading -> "Confirmando..."
                    finalizando -> "Finalizar pausa"
                    else -> "Sim, sou eu"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
