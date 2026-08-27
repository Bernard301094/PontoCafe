package com.pontocafe.app.camera

import kotlin.math.sqrt

/**
 * Traduz a banda de tamanho de rosto aceita por [FaceCapturePolicy] para o tamanho
 * que a retícula precisa ter NA TELA. Existe para que o guia e a política não possam
 * mais divergir em silêncio: o guia deixou de ser uma fração literal da largura da
 * tela e passou a ser derivado das constantes da política.
 *
 * ── Por que o guia mentia ──────────────────────────────────────────────────────
 *
 * O guia é um alvo de enquadramento: quem não cabe nele dá um passo para trás. Se o
 * alvo é menor que o rosto que a política aceita, a pessoa se afasta até sair da
 * banda e o reconhecimento falha sem explicação. Antes desta mudança o guia era
 * `min(largura*0.72, altura*0.58)` e o desenho era um CÍRCULO de raio
 * `0.42 * minDimension`, ou seja, extensão vertical de apenas `0.84 * guideWidth`.
 * A calibração registrada em MIN_FACE_HEIGHT_RATIO ("~0.32 de altura") foi medida
 * quando o desenho ainda era o retângulo de cantos que ocupava a caixa inteira
 * (`1.25 * guideWidth` de altura). O redesenho para anel circular encolheu o alvo
 * vertical em 0.84/1.25 = 0.672 sem tocar na política: 0.32 * 0.672 ≈ 0.215, ABAIXO
 * do piso de 0.24 — e o pulso ambiente (0.86..1.0) levava a 0.185.
 *
 * ── Cadeia de conversão (razão da política → pixels na tela) ───────────────────
 *
 * 1. O buffer de análise é 4:3 (ImageAnalysis.setTargetResolution(960x720)); de pé
 *    ele vira 3:4, logo [ANALYSIS_UPRIGHT_ASPECT] = 0.75. Só a PROPORÇÃO importa —
 *    as razões da política são normalizadas —, então um device que devolva outro
 *    tamanho 4:3 suportado não muda nada aqui.
 * 2. A PreviewView usa ScaleType.FILL_CENTER, e o ViewPort compartilhado recorta a
 *    mesma região para a análise. Num celular alto (1080x2340 → 0.4615) o recorte é
 *    HORIZONTAL: sobra 0.4615/0.75 = 0.615 da largura do buffer e a altura inteira.
 *    Por isso um rosto aparece MAIOR na tela do que sua razão contra o buffer sugere
 *    — mas só na horizontal. Na vertical a razão da política e a fração da tela
 *    coincidem 1:1, e é justamente a altura que reprova as pessoas.
 * 3. As razões são medidas contra o buffer COMPLETO (FaceObservation.fullImageWidth/
 *    fullImageHeight), não contra a região visível — daí o fator do item 2 entrar.
 *
 * ── O que é medido e o que é estimado ─────────────────────────────────────────
 *
 * MEDIDO: as constantes da política; a proporção 4:3 do buffer; a matemática de
 * recorte do FILL_CENTER; e a âncora "~0.32 de altura" observada em device com a
 * geometria antiga (commit d8c15a5).
 *
 * ESTIMADO — recalibre estes dois se o guia voltar a mentir:
 *  • [FACE_BOX_ASPECT]: a caixa do ML Kit é ~1.15x mais alta que larga. Só afeta
 *    qual portão de largura limita o topo da banda; varrer 1.05..1.25 move o alvo
 *    final apenas entre 0.358 e 0.391 (±5%).
 *  • [GUIDE_FILL_FACTOR]: quanto da altura do guia a caixa do ML Kit ocupa de fato,
 *    já embutindo cabelo/testa fora da caixa e a folga que as pessoas deixam.
 *    0.77 vem de duas rotas independentes que concordam: a âncora medida
 *    (0.32 / 0.4154 = 0.770) e a anatomia (a caixa cobre ~3/4 da altura da cabeça).
 */
object FaceGuideGeometry {

    /** Largura/altura do frame de análise já de pé: 960x720 girado 90° → 720x960. */
    const val ANALYSIS_UPRIGHT_ASPECT = 0.75f

    /**
     * Largura/altura do oval do guia. Fonte unica: KIOSK_GUIDE_CANVAS_ASPECT e
     * FacePositionGuide leem daqui, para o quiosque e o cadastro nao divergirem.
     */
    const val GUIDE_OVAL_ASPECT = 0.80f

    /** ESTIMADO: altura/largura da bounding box do ML Kit. */
    const val FACE_BOX_ASPECT = 1.15f

    /** ESTIMADO: altura da caixa do ML Kit ÷ extensão vertical do guia. */
    const val GUIDE_FILL_FACTOR = 0.77f

    /**
     * Piso e teto efetivos em razão de ALTURA, depois de traduzir também os portões
     * de largura. Com aspecto 1.15 quem limita embaixo é a altura (0.24 > o
     * equivalente 0.190 da largura mínima) e quem limita em cima é a largura
     * (0.5865 < o teto de altura 0.90).
     */
    val minAcceptedHeightRatio: Float
        get() = maxOf(
            FaceCapturePolicy.MIN_FACE_HEIGHT_RATIO,
            FaceCapturePolicy.MIN_FACE_WIDTH_RATIO * FACE_BOX_ASPECT * ANALYSIS_UPRIGHT_ASPECT,
        )

    val maxAcceptedHeightRatio: Float
        get() = minOf(
            FaceCapturePolicy.MAX_FACE_HEIGHT_RATIO,
            FaceCapturePolicy.MAX_FACE_WIDTH_RATIO * FACE_BOX_ASPECT * ANALYSIS_UPRIGHT_ASPECT,
        )

    /**
     * Alvo: a MÉDIA GEOMÉTRICA da banda, não a aritmética. Tamanho de rosto é
     * inversamente proporcional à distância, então a média geométrica é que dá folga
     * multiplicativa igual para os dois lados — aqui, 1.56x de aproximação e 1.56x
     * de afastamento antes de encostar em qualquer portão. A média aritmética
     * (0.413) empurraria a pessoa para perto e gastaria a folga de baixo, que é
     * exatamente a que hoje reprova.
     */
    val targetFaceHeightRatio: Float
        get() = sqrt(minAcceptedHeightRatio * maxAcceptedHeightRatio)

    /**
     * Fração da ALTURA do buffer que sobrevive ao FILL_CENTER. Tela mais estreita que
     * o buffer (celular de pé) → recorte horizontal, altura inteira preservada.
     * Tela mais larga (tablet deitado) → recorte vertical, e aí sim a altura encolhe.
     */
    fun visibleBufferHeightFraction(previewAspect: Float): Float =
        if (previewAspect <= 0f || previewAspect <= ANALYSIS_UPRIGHT_ASPECT) 1f
        else ANALYSIS_UPRIGHT_ASPECT / previewAspect

    /** Fração da LARGURA do buffer que sobrevive ao FILL_CENTER (diagnóstico/testes). */
    fun visibleBufferWidthFraction(previewAspect: Float): Float =
        if (previewAspect <= 0f || previewAspect >= ANALYSIS_UPRIGHT_ASPECT) 1f
        else previewAspect / ANALYSIS_UPRIGHT_ASPECT

    /**
     * Altura que o oval precisa ter, como fração da altura do preview, para que quem
     * o preencher caia em [targetFaceHeightRatio].
     *
     * Pode passar de 1.0 em preview deitado (o recorte vertical do FILL_CENTER é tão
     * agressivo que um rosto do tamanho da política ficaria mais alto que a tela).
     * Não tratamos isso aqui: quem chama já limita pela altura disponível e o guia
     * fica do maior tamanho que couber — subdimensionado, mas nunca maior que a tela.
     */
    fun ovalHeightFractionOfPreview(previewAspect: Float): Float =
        targetFaceHeightRatio / visibleBufferHeightFraction(previewAspect) / GUIDE_FILL_FACTOR

    /**
     * Razão de altura que um rosto encaixado num oval de [ovalHeightFraction] da tela
     * produz. Inverso exato de [ovalHeightFractionOfPreview] — existe para o teste
     * fechar o laço contra [FaceCapturePolicy.evaluate] e travar a correspondência.
     */
    fun faceHeightRatioForOval(ovalHeightFraction: Float, previewAspect: Float): Float =
        ovalHeightFraction * GUIDE_FILL_FACTOR * visibleBufferHeightFraction(previewAspect)

    /** Razão de largura correspondente, para checar o portão de largura. */
    fun faceWidthRatioFor(faceHeightRatio: Float): Float =
        faceHeightRatio / (FACE_BOX_ASPECT * ANALYSIS_UPRIGHT_ASPECT)
}
