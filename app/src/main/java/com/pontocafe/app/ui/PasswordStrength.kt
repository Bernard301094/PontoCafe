package com.pontocafe.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Uma exigência de senha e se ela já foi cumprida. */
data class PontoPasswordRule(val label: String, val met: Boolean)

/**
 * Força da senha, em segmentos, com as exigências abaixo.
 *
 * Existiam duas implementações disto: a lista de quatro itens do primeiro
 * Administrador e as três linhas de texto da troca de senha do Supervisor.
 * Diziam a mesma coisa com formatos diferentes, e nenhuma das duas dizia o
 * essencial — *quão* forte a senha está. Uma lista de vistos responde "falta
 * quê"; a barra responde "já chega?", que é a pergunta de quem está a escolher.
 *
 * A barra e as cores interpolam em vez de saltar. Uma senha que passa de fraca
 * a média com uma tecla dava um pisca vermelho→âmbar seco, e o olho lia aquilo
 * como erro em vez de progresso.
 */
@Composable
fun PontoPasswordStrength(
    rules: List<PontoPasswordRule>,
    modifier: Modifier = Modifier,
) {
    val semantic = LocalPontoCafeSemanticColors.current
    val cumpridas = rules.count { it.met }
    val total = rules.size.coerceAtLeast(1)

    val alvo = when {
        cumpridas <= total / 3 -> semantic.critical
        cumpridas < total -> semantic.warning
        else -> semantic.success
    }
    val cor by animateColorAsState(alvo, label = "forca-cor")
    val preenchidos by animateFloatAsState(
        targetValue = cumpridas.toFloat(),
        animationSpec = PontoSprings.Surface,
        label = "forca-segmentos",
    )

    val rotulo = when {
        cumpridas <= total / 3 -> "Fraca"
        cumpridas < total -> "Média"
        else -> "Forte"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { stateDescription = "Senha $rotulo, $cumpridas de $total exigências cumpridas" },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(total) { indice ->
                // A opacidade de cada segmento é lida na fase de desenho: a
                // barra anda a cada tecla, e recompor a linha inteira por isso
                // seria pagar caro por um traço de 6dp.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .graphicsLayer {
                            alpha = (preenchidos - indice).coerceIn(0f, 1f) * 0.85f + 0.15f
                        }
                        .background(cor, RoundedCornerShape(3.dp)),
                )
            }
        }

        Text(
            rotulo,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = cor,
        )

        rules.forEach { rule -> PontoPasswordRuleRow(rule) }
    }
}

@Composable
private fun PontoPasswordRuleRow(rule: PontoPasswordRule, modifier: Modifier = Modifier) {
    val semantic = LocalPontoCafeSemanticColors.current
    // O visto entra a rodar e a crescer no momento em que a regra passa. Sem
    // isso, escrever o décimo caractere mudava um cinzento para verde algures
    // na lista, e ninguém repara em qual.
    val progresso by animateFloatAsState(
        targetValue = if (rule.met) 1f else 0f,
        animationSpec = PontoSprings.Pop,
        label = "regra-${rule.label}",
    )
    val cor by animateColorAsState(
        if (rule.met) semantic.success else MaterialTheme.colorScheme.outlineVariant,
        label = "regra-cor",
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            Icons.Default.Check,
            contentDescription = if (rule.met) "Atendido" else "Ainda não atendido",
            tint = cor,
            modifier = Modifier
                .size(15.dp)
                .graphicsLayer {
                    val escala = 0.7f + progresso * 0.3f
                    scaleX = escala
                    scaleY = escala
                    rotationZ = (1f - progresso) * -90f
                },
        )
        Text(
            rule.label,
            style = MaterialTheme.typography.bodySmall,
            color = if (rule.met) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/**
 * Confirmação de que os dois campos batem.
 *
 * Fica fora de [PontoPasswordStrength] porque não é força de senha: uma senha
 * fraca digitada duas vezes iguais continua fraca, e misturar as duas coisas
 * na mesma barra faria a confirmação empurrar a medida para cima.
 */
@Composable
fun PontoPasswordConfirmation(label: String, modifier: Modifier = Modifier) {
    PontoPasswordRuleRow(PontoPasswordRule(label, met = true), modifier)
}
