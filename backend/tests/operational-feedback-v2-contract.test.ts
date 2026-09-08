import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const supervisorAlerts = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/SupervisorLiveAlerts.kt', import.meta.url),
  'utf8',
)
const supervisorNotifier = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/notifications/SupervisorAlertNotifier.kt', import.meta.url),
  'utf8',
)
const pauseFeed = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/OperationalPauseFeed.kt', import.meta.url),
  'utf8',
)
const pontoFlow = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/PontoFlowHost.kt', import.meta.url),
  'utf8',
)
const material = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/MaterialDesignSystem.kt', import.meta.url),
  'utf8',
)
const voice = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/voice/PontoNeuralVoice.kt', import.meta.url),
  'utf8',
)
const capturePolicy = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/domain/AccessCode.kt', import.meta.url),
  'utf8',
)

test('Supervisor recebe progressão visual antes do limite sem notificação repetitiva', () => {
  assert.match(supervisorAlerts, /WARNING_THRESHOLD_SECONDS = 60/)
  assert.match(supervisorAlerts, /CRITICAL_THRESHOLD_SECONDS = 15/)
  assert.match(supervisorAlerts, /PROXIMO_LIMITE/)
  assert.match(supervisorAlerts, /CRITICO/)
  assert.match(supervisorAlerts, /EXCESSO/)
  assert.match(supervisorAlerts, /warningBaseline/)
  assert.match(supervisorAlerts, /criticalBaseline/)
  assert.match(supervisorAlerts, /overdueBaseline/)

  assert.match(pauseFeed, /OPERATIONAL_WARNING_SECONDS = 60/)
  assert.match(pauseFeed, /OPERATIONAL_CRITICAL_SECONDS = 15/)
  assert.match(pauseFeed, /Crítico/)
  assert.match(pauseFeed, /Excedido/)
})

test('notificações do Supervisor não sobrescrevem eventos diferentes e permitem autoteste', () => {
  assert.match(supervisorNotifier, /GROUP_KEY/)
  assert.match(supervisorNotifier, /stableNotificationId/)
  assert.match(supervisorNotifier, /setGroupSummary\(true\)/)
  assert.match(supervisorNotifier, /sendSelfTest/)
})

test('feedback do Ponto diferencia confirmado, offline, limite excedido e recusa', () => {
  // O comprovante não pode dizer a mesma coisa nos quatro casos: quem
  // excedeu, quem registrou sem rede e quem foi recusado precisam de ações
  // diferentes ao sair do quiosque.
  assert.match(pontoFlow, /if \(comprovante\.excedeuLimite\) Icons\.Default\.Warning else Icons\.Default\.CheckCircle/)
  assert.match(pontoFlow, /comprovante\.excedeuLimite -> PontoCafeTone\.WARNING/)
  assert.match(pontoFlow, /Retorno acima do limite/)
  assert.match(pontoFlow, /Registrado sem conexão/)
  assert.match(pontoFlow, /comprovante\.pendenteSincronizacao/)
  assert.match(pontoFlow, /Fora do horário habitual/)
  assert.match(pontoFlow, /Código não aceito/)
  assert.match(pontoFlow, /tone = PontoCafeTone\.DANGER/)
})

test('microinterações permanecem curtas e acessíveis', () => {
  assert.match(material, /MotionReveal/)
  // O háptico dos botões passa pelo objeto central, não pela API do Android.
  assert.match(material, /PontoHaptics\.tap\(view\)/)
  assert.doesNotMatch(material, /performHapticFeedback/)
})

test('o toque é mola, e vive na fase de desenho', () => {
  const motion = readFileSync(
    new URL('../../app/src/main/java/com/pontocafe/app/ui/PontoCafeMotion.kt', import.meta.url),
    'utf8',
  )

  // Era um tween: interrompido a meio -- que é o caso normal, o dedo sai antes
  // dos 150 ms -- dava um degrau visível. A mola é interrompível por natureza.
  assert.match(motion, /object PontoSprings/)
  assert.match(motion, /dampingRatio = Spring\.DampingRatioLowBouncy/)
  assert.match(motion, /stiffness = Spring\.StiffnessMediumLow/)
  assert.match(motion, /animationSpec = if \(pressed\) PontoSprings\.PressDown else PontoSprings\.PressRelease/)
  assert.doesNotMatch(material, /targetValue = if \(pressed\)[\s\S]{0,120}tween\(/)

  // A escala tem de sair pelo graphicsLayer. Animar tamanho ou padding daria o
  // mesmo efeito visual recriando o layout a cada frame.
  assert.match(motion, /fun Modifier\.pontoPressScale\(scale: \(\) -> Float\): Modifier = graphicsLayer/)
  assert.match(material, /internal fun Modifier\.pcPressScale\(scale: Float\): Modifier = graphicsLayer/)

  // O ponto único: todos os botões e cartões do app passam por aqui.
  assert.match(material, /rememberPontoPressScale\(interactionSource, PontoPressScale\.Button\)/)
})

test('o alvo tocável cresce do telefone para o quiosque', () => {
  const responsive = readFileSync(
    new URL('../../app/src/main/java/com/pontocafe/app/ui/ResponsiveLayout.kt', import.meta.url),
    'utf8',
  )
  // 48dp servem um polegar a 30 cm. Um quiosque na parede é operado de pé e a
  // mais de um metro, e o mesmo botão passa a ser difícil de acertar.
  assert.match(responsive, /fun pontoTouchTarget\(\): Dp/)
  assert.match(responsive, /PontoCafeWindowSizeClass\.COMPACT -> 48\.dp/)
  assert.match(responsive, /PontoCafeWindowSizeClass\.MEDIUM -> 56\.dp/)
  assert.match(responsive, /PontoCafeWindowSizeClass\.EXPANDED -> 64\.dp/)
  // E os botões do sistema têm de usá-lo, senão o token não vale nada.
  assert.match(material, /defaultMinSize\(minHeight = pontoTouchTarget\(\)\)/)
})

test('as telas têm preview nos dois aparelhos que existem em produção', () => {
  const previews = readFileSync(
    new URL('../../app/src/main/java/com/pontocafe/app/ui/PontoPreviews.kt', import.meta.url),
    'utf8',
  )
  // Um preview só de telefone deixa passar layouts que esticam feio a 1200dp;
  // um só de tablet deixa passar texto cortado a 411dp. Sempre os dois.
  assert.ok(previews.includes('const val PREVIEW_PHONE = "spec:width=411dp'))
  assert.ok(previews.includes('const val PREVIEW_KIOSK = "spec:width=1280dp'))
  assert.match(previews, /fun PontoPreviewSurface/)
  assert.match(previews, /PontoCafeTheme \{/)
})

test('estado da voz neural fica diagnosticável sem retirar fallback Android', () => {
  assert.match(voice, /PontoNeuralVoiceDiagnostics/)
  assert.match(voice, /usingAndroidFallback/)
  assert.match(voice, /lastFailureReason/)
  assert.match(voice, /retryAvailableInMillis/)
  assert.match(voice, /retryNow/)
  assert.match(voice, /RETRY_AFTER_MILLIS = 30_000L/)
  assert.match(voice, /VOICE_PLAYBACK_FAILED/)
})

test('o alfabeto do código de acesso evita os caracteres que se confundem', () => {
  // I, L, O e U ficam de fora: os três primeiros somem contra 1 e 0 num papel
  // escrito à pressa, e o U evita que um sorteio produza palavra ofensiva.
  assert.match(capturePolicy, /const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"/)
  assert.match(capturePolicy, /'I', 'L' -> '1'/)
  assert.match(capturePolicy, /'O' -> '0'/)
  assert.doesNotMatch(supervisorAlerts, /faceThreshold|cosine|embedding/)
  assert.doesNotMatch(material, /faceThreshold|cosine|embedding/)
})

test('o brilho do esqueleto é um só, e não recompõe a cada frame', () => {
  const skeleton = readFileSync(
    new URL('../../app/src/main/java/com/pontocafe/app/ui/PontoCafeSkeleton.kt', import.meta.url),
    'utf8',
  )

  // Havia duas implementações do mesmo brilho, em arquivos diferentes, com
  // curvas e intervalos diferentes: duas telas a carregar lado a lado pulsavam
  // fora de fase. Agora só existe uma.
  assert.match(skeleton, /fun PontoCafeLoadingSkeleton\(/)
  assert.match(skeleton, /fun PontoCafeListSkeletonScreen\(/)
  assert.equal(
    (skeleton.match(/rememberInfiniteTransition\(label/g) ?? []).length,
    1,
    'só pode existir uma animação de brilho no projeto',
  )

  // E a opacidade é lida na fase de desenho. Lida durante a composição, cada
  // frame do brilho recompunha a árvore inteira do esqueleto -- exatamente
  // enquanto a tela ainda está a buscar dados.
  assert.match(skeleton, /private fun rememberSkeletonAlpha\(\): State<Float>/)
  assert.match(skeleton, /graphicsLayer \{ this\.alpha = 0\.06f \+ 0\.08f \* alpha\.value \}/)
})

test('o shell chama as telas direto, sem invólucros de compatibilidade', () => {
  const area = readFileSync(
    new URL('../../app/src/main/java/com/pontocafe/app/ui/AdminArea.kt', import.meta.url),
    'utf8',
  )
  // `AdminPanelScreen` e `AdminManagementScreenV2` eram arquivos inteiros cuja
  // única função era reencaminhar a chamada para a versão real.
  assert.doesNotMatch(area, /AdminPanelScreen/)
  assert.doesNotMatch(area, /AdminManagementScreenV2/)
  assert.doesNotMatch(area, /AdminManagementScreenV3/)
  assert.match(area, /AdminManagementScreen\(/)
})
