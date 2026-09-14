package com.pontocafe.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pontocafe.app.R

/**
 * O vocabulário visual do painel web, em Compose.
 *
 * O painel (`server.ts`) e o app são dois programas que não partilham uma linha
 * de interface: um é HTML com Tailwind, o outro é Kotlin. Durante semanas o
 * design novo existiu só no painel, e o app recebia apenas a paleta -- o que não
 * o fazia parecer-se com nada. Este ficheiro traduz as peças que dão ao painel a
 * sua cara: a tarjeta branca de canto largo com borda fina, o selo de ícone
 * âmbar, o cabeçalho com o logótipo num quadrado, os botões de canto arredondado
 * (e não em pílula), as teclas e as caixas do código em fonte monoespaçada.
 *
 * As cores são os valores exatos das classes Tailwind que o painel usa. Os nomes
 * seguem as classes (`stone200`, `amber50`) de propósito: quem compara uma tela
 * com a outra encontra a mesma palavra dos dois lados.
 */
internal object Painel {
    val stone50 = Color(0xFFFAFAF9)
    val stone100 = Color(0xFFF5F5F4)
    val stone200 = Color(0xFFE7E5E4)
    val stone300 = Color(0xFFD6D3D1)
    val stone400 = Color(0xFFA8A29E)
    val stone500 = Color(0xFF78716C)
    val stone600 = Color(0xFF57534E)
    val stone700 = Color(0xFF44403C)

    val amber50 = Color(0xFFFFFBEB)
    val amber100 = Color(0xFFFEF3C7)
    val amber200 = Color(0xFFFDE68A)
    val amber300 = Color(0xFFFCD34D)
    val amber400 = Color(0xFFFBBF24)
    val amber600 = Color(0xFFD97706)
    val amber800 = Color(0xFF92400E)

    val emerald50 = Color(0xFFECFDF5)
    val emerald200 = Color(0xFFA7F3D0)
    val emerald500 = Color(0xFF10B981)
    val emerald600 = Color(0xFF059669)

    val red50 = Color(0xFFFEF2F2)
    val red200 = Color(0xFFFECACA)
    val red600 = Color(0xFFDC2626)

    val coffee700 = PontoCafeBrand.coffee700
    val coffee800 = PontoCafeBrand.coffee800
    val coffee900 = PontoCafeBrand.coffee900
    val coffee950 = PontoCafeBrand.coffee950

    /** rounded-xl, rounded-2xl e rounded-3xl do Tailwind. */
    val cantoXl = RoundedCornerShape(12.dp)
    val canto2xl = RoundedCornerShape(16.dp)
    val canto3xl = RoundedCornerShape(24.dp)
}

/**
 * JetBrains Mono, a monoespaçada do painel: códigos, teclas e relógio.
 *
 * Num código de acesso a largura fixa não é enfeite -- é o que alinha as seis
 * caixas e faz o 0 e o O, o 1 e o I, parecerem diferentes à distância a que se
 * lê um totem.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
internal val JetBrainsMono = FontFamily(
    listOf(500, 700).map { peso ->
        Font(
            resId = R.font.jetbrains_mono,
            weight = FontWeight(peso),
            variationSettings = FontVariation.Settings(FontVariation.weight(peso)),
        )
    },
)

internal enum class PainelTom { AMBAR, VERDE, VERMELHO, NEUTRO }

/**
 * A tarjeta do painel: branca, canto de 24dp, borda stone-200 e sombra larga.
 * É ela que faz o conteúdo existir sobre o fundo cinzento -- sem ela o totem
 * volta a ser texto solto no ecrã, que era exatamente o estilo antigo.
 */
@Composable
internal fun PainelCartao(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .shadow(elevation = 16.dp, shape = Painel.canto3xl, ambientColor = Color(0x14000000), spotColor = Color(0x1F000000))
            .background(Color.White, Painel.canto3xl)
            .border(1.dp, Painel.stone200, Painel.canto3xl)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        content = content,
    )
}

/** O selo quadrado de ícone que abre cada passo do painel. */
@Composable
internal fun PainelSeloIcone(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tom: PainelTom = PainelTom.AMBAR,
    tamanho: Dp = 56.dp,
) {
    val (fundo, borda, tinta) = when (tom) {
        PainelTom.AMBAR -> Triple(Painel.amber50, Painel.amber200, Painel.coffee700)
        PainelTom.VERDE -> Triple(Painel.emerald50, Painel.emerald200, Painel.emerald600)
        PainelTom.VERMELHO -> Triple(Painel.red50, Painel.red200, Painel.red600)
        PainelTom.NEUTRO -> Triple(Painel.stone50, Painel.stone200, Painel.stone600)
    }
    val canto = if (tamanho > 60.dp) Painel.canto3xl else Painel.canto2xl
    Box(
        modifier = modifier
            .size(tamanho)
            .shadow(1.dp, canto)
            .background(fundo, canto)
            .border(1.dp, borda, canto),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tinta, modifier = Modifier.size(tamanho * 0.57f))
    }
}

/** Título centrado de passo: text-2xl, bold, coffee-950. */
@Composable
internal fun PainelTitulo(texto: String, modifier: Modifier = Modifier, grande: Boolean = true) {
    Text(
        texto,
        modifier = modifier.fillMaxWidth(),
        style = TextStyle(
            fontFamily = PontoCafeFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = if (grande) 24.sp else 20.sp,
            lineHeight = if (grande) 32.sp else 28.sp,
            letterSpacing = (-0.2).sp,
        ),
        color = Painel.coffee950,
        textAlign = TextAlign.Center,
    )
}

/** Subtítulo centrado: text-sm, stone-500. */
@Composable
internal fun PainelSubtitulo(texto: String, modifier: Modifier = Modifier, cor: Color = Painel.stone500) {
    Text(
        texto,
        modifier = modifier.fillMaxWidth(),
        style = TextStyle(fontFamily = PontoCafeFontFamily, fontSize = 14.sp, lineHeight = 20.sp),
        color = cor,
        textAlign = TextAlign.Center,
    )
}

/** Linha de retorno do painel: text-xs semibold, centrada. */
@Composable
internal fun PainelRetorno(texto: String?, erro: Boolean, modifier: Modifier = Modifier) {
    Text(
        texto.orEmpty(),
        modifier = modifier.fillMaxWidth().heightIn(min = 20.dp),
        style = TextStyle(fontFamily = PontoCafeFontFamily, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp),
        color = if (erro) Painel.red600 else Painel.stone500,
        textAlign = TextAlign.Center,
    )
}

/**
 * Botão cheio do painel: `bg-coffee-900 text-white rounded-2xl`.
 *
 * Canto de 16dp, e não pílula. O pill era do DESIGN.md; no painel todo botão de
 * ação é um retângulo de canto largo, e é uma das diferenças que mais se notam
 * entre as duas telas lado a lado.
 */
@Composable
internal fun PainelBotaoPrimario(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) {
    PainelBotaoBase(
        texto = texto,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled && !loading,
        fundo = Painel.coffee900,
        conteudo = Color.White,
        borda = null,
        forma = Painel.canto2xl,
        altura = 54.dp,
        negrito = true,
        loading = loading,
        icon = icon,
    )
}

/** Botão de contorno do painel: branco, borda stone-300, texto stone-700. */
@Composable
internal fun PainelBotaoSecundario(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    PainelBotaoBase(
        texto = texto,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        fundo = Color.White,
        conteudo = Painel.stone700,
        borda = Painel.stone300,
        forma = Painel.canto2xl,
        altura = 48.dp,
        negrito = false,
        loading = false,
        icon = icon,
    )
}

/** O "Apagar" do painel: fundo stone-100, sem borda, canto 12dp. */
@Composable
internal fun PainelBotaoSuave(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    PainelBotaoBase(
        texto = texto,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        fundo = Painel.stone100,
        conteudo = Painel.stone700,
        borda = null,
        forma = Painel.cantoXl,
        altura = 44.dp,
        negrito = false,
        loading = false,
        icon = null,
    )
}

@Composable
private fun PainelBotaoBase(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    fundo: Color,
    conteudo: Color,
    borda: Color?,
    forma: RoundedCornerShape,
    altura: Dp,
    negrito: Boolean,
    loading: Boolean,
    icon: ImageVector?,
) {
    val interacao = remember { MutableInteractionSource() }
    val escala = rememberPontoPressScale(interacao, PontoPressScale.Button)
    Box(
        modifier = modifier
            .pontoPressScale { escala }
            .height(altura)
            // disabled:opacity-40, como no painel: o botão continua no lugar e
            // com a mesma cor, só que apagado -- não vira um cinzento qualquer.
            .alpha(if (enabled || loading) 1f else 0.4f)
            .background(fundo, forma)
            .then(if (borda != null) Modifier.border(1.dp, borda, forma) else Modifier)
            .clickable(
                enabled = enabled,
                onClick = onClick,
                interactionSource = interacao,
                indication = LocalIndication.current,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(color = conteudo, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = conteudo, modifier = Modifier.size(20.dp))
                }
                Text(
                    texto,
                    style = TextStyle(
                        fontFamily = PontoCafeFontFamily,
                        fontSize = if (negrito) 16.sp else 14.sp,
                        fontWeight = if (negrito) FontWeight.Bold else FontWeight.SemiBold,
                    ),
                    color = conteudo,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Campo do painel que abre o seletor: `rounded-2xl border-stone-300`, ícone à
 * esquerda e texto de ajuda stone-500. Parece um campo porque é assim que o
 * painel o desenha; tocar-lhe abre a folha com a lista e a busca.
 */
@Composable
internal fun PainelCampoToque(
    placeholder: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Color.White, Painel.canto2xl)
            .border(1.dp, Painel.stone300, Painel.canto2xl)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Painel.stone400, modifier = Modifier.size(20.dp))
        Text(
            placeholder,
            style = TextStyle(fontFamily = PontoCafeFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Medium),
            color = Painel.stone500,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * O cabeçalho do painel: barra branca com borda inferior, o logótipo num
 * quadrado coffee-800 com a xícara em âmbar, o nome com o selo da versão, e o
 * relógio numa caixa stone-50 em fonte mono.
 */
@Composable
internal fun PainelCabecalho(
    versao: String,
    subtitulo: String,
    relogio: String?,
    sincronizado: Boolean,
    acaoDireita: @Composable () -> Unit,
) {
    Surface(color = Color.White, modifier = Modifier.fillMaxWidth(), shadowElevation = 1.dp) {
        Column {
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Painel.coffee800, Painel.cantoXl),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Coffee, contentDescription = null, tint = Painel.amber400, modifier = Modifier.size(24.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Ponto Café",
                            style = TextStyle(
                                fontFamily = PontoCafeFontFamily,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.3).sp,
                            ),
                            color = Painel.coffee950,
                            maxLines = 1,
                        )
                        Text(
                            "v$versao",
                            modifier = Modifier
                                .background(Painel.amber100, CircleShape)
                                .border(1.dp, Painel.amber200, CircleShape)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            style = TextStyle(fontFamily = PontoCafeFontFamily, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                            color = Painel.amber800,
                            maxLines = 1,
                        )
                    }
                    Text(
                        subtitulo,
                        style = TextStyle(fontFamily = PontoCafeFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        color = Painel.stone500,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // `hidden sm:flex`, como no painel: abaixo de 640dp o relógio sai e o
                // subtítulo deixa de ser cortado. O estado da ligação continua
                // visível na linha por cima da tarjeta.
                if (relogio != null && LocalConfiguration.current.screenWidthDp >= 640) Row(
                    modifier = Modifier
                        .background(Painel.stone50, Painel.cantoXl)
                        .border(1.dp, Painel.stone200, Painel.cantoXl)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        relogio,
                        style = TextStyle(fontFamily = JetBrainsMono, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        color = Painel.coffee900,
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(if (sincronizado) Painel.emerald500 else Painel.amber400, CircleShape),
                    )
                }
                acaoDireita()
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Painel.stone200))
        }
    }
}

/** A linha "● Aparelho: …" que o painel põe por cima da tarjeta do totem. */
@Composable
internal fun PainelLinhaEstado(texto: String, cor: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).background(cor, CircleShape))
        Text(
            texto,
            style = TextStyle(fontFamily = PontoCafeFontFamily, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            color = Painel.stone600,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Aviso dentro da tarjeta, no tom do painel, para o que não cabe numa linha. */
@Composable
internal fun PainelAviso(
    titulo: String,
    texto: String,
    tom: PainelTom,
    modifier: Modifier = Modifier,
) {
    val (fundo, borda, cor) = when (tom) {
        PainelTom.AMBAR -> Triple(Painel.amber50, Painel.amber200, Painel.amber800)
        PainelTom.VERDE -> Triple(Painel.emerald50, Painel.emerald200, Painel.emerald600)
        PainelTom.VERMELHO -> Triple(Painel.red50, Painel.red200, Painel.red600)
        PainelTom.NEUTRO -> Triple(Painel.stone50, Painel.stone200, Painel.stone700)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(fundo, Painel.cantoXl)
            .border(1.dp, borda, Painel.cantoXl)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            titulo,
            style = TextStyle(fontFamily = PontoCafeFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold),
            color = cor,
        )
        Text(
            texto,
            style = TextStyle(fontFamily = PontoCafeFontFamily, fontSize = 12.sp, lineHeight = 16.sp),
            color = Painel.stone600,
        )
    }
}

internal val PainelBordaSuave = BorderStroke(1.dp, Painel.stone200)

/**
 * O mesmo aviso, a partir do tom semântico que o resto do app já usa.
 *
 * Os estados do totem -- código recusado, retorno acima do limite, registo sem
 * rede -- já eram decididos em [PontoCafeTone]. Traduzir aqui, num só lugar,
 * evita que cada passo escolha à mão uma cor do painel e as duas acabem por
 * discordar.
 */
@Composable
internal fun PainelAvisoTom(
    title: String,
    supportingText: String,
    tone: PontoCafeTone,
    modifier: Modifier = Modifier,
) {
    PainelAviso(
        titulo = title,
        texto = supportingText,
        tom = when (tone) {
            PontoCafeTone.DANGER -> PainelTom.VERMELHO
            PontoCafeTone.WARNING -> PainelTom.AMBAR
            PontoCafeTone.SUCCESS -> PainelTom.VERDE
            PontoCafeTone.INFO, PontoCafeTone.NEUTRAL -> PainelTom.NEUTRO
        },
        modifier = modifier,
    )
}

/**
 * "PASSO 1 DE 2", na pílula do painel -- a mesma do selo de versão do
 * cabeçalho. O painel não tem indicador de etapa; o totem tem, porque quem chega
 * a ele de pé precisa de saber, sem ler mais nada, que isto tem dois passos e que
 * o segundo é o código.
 */
@Composable
internal fun PainelEtapa(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto,
        modifier = modifier
            .background(Painel.amber50, CircleShape)
            .border(1.dp, Painel.amber200, CircleShape)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        style = TextStyle(
            fontFamily = PontoCafeFontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
        ),
        color = Painel.coffee700,
    )
}
