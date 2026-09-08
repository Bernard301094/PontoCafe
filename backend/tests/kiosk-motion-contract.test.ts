import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const read = (p: string) => readFileSync(new URL(`../../${p}`, import.meta.url), 'utf8')

const motion = read('app/src/main/java/com/pontocafe/app/ui/PontoCafeMotion.kt')
const kiosk = read('app/src/main/java/com/pontocafe/app/ui/PontoFlowHost.kt')
const lock = read('app/src/main/java/com/pontocafe/app/ui/RestrictedAreaLockScreen.kt')

test('o dígito que entra salta, e a tecla comprime mais que um botão', () => {
  // O dedo tapa a caixa no instante do toque. Sem o salto, a pessoa só descobre
  // que a tecla pegou ao levantar a mão -- e no quiosque isso gera o toque
  // duplo que preenche duas casas.
  assert.match(kiosk, /rememberPopOnChange\(gatilho = char, ativo = preenchido\)/)
  assert.match(kiosk, /scaleX = pop\.value/)

  // A tecla comprime 0,92 contra os 0,96 de um botão comum: aqui a compressão
  // é o próprio recibo do toque.
  assert.match(motion, /const val Key = 0\.92f/)
  assert.match(kiosk, /rememberPontoPressScale\(interactionSource, PontoPressScale\.Key\)/)
})

test('o comprovante marca o instante do registro', () => {
  // Era um círculo estático: aparecia já pronto e não dizia quando aconteceu.
  assert.match(kiosk, /rememberPopOnChange\(gatilho = comprovante, de = 0\.6f\)/)
  assert.match(kiosk, /drawCircle\(/)
  assert.match(kiosk, /alpha = \(1f - progresso\) \* 0\.45f/)

  // Uma vez só: a onda é um animateTo e não um laço infinito. Num quiosque
  // ligado 24 h, uma animação que não termina é uma que nunca liberta a CPU.
  const receipt = kiosk.slice(kiosk.indexOf('private fun ReceiptStep'))
  const corpo = receipt.slice(0, receipt.indexOf('\n@Composable'))
  assert.doesNotMatch(corpo, /infiniteRepeatable|rememberInfiniteTransition/)
})

test('uma recusa treme, e a mola diz "não" melhor que um tween linear', () => {
  // O tween linear tinha velocidade constante e parecia um deslize. A mola
  // rígida desacelera, e é essa desaceleração que o olho lê como recusa.
  assert.match(motion, /val Shake: SpringSpec<Float> = spring\(/)
  assert.match(motion, /stiffness = Spring\.StiffnessHigh/)
  assert.match(motion, /repeat\(3\) \{ volta ->/)
  assert.doesNotMatch(motion, /shake\.animateTo\(1f, tween\(420/)

  // E chega onde a recusa acontece: o campo de PIN do quiosque e o bloqueio da
  // área restrita, que antes só mudavam um texto.
  assert.match(kiosk, /\.shakeOnChange\(error\)/)
  assert.match(lock, /\.shakeOnChange\(message\)/)
})

test('a lista operacional tem um relógio, não um por cartão', () => {
  const feed = read('app/src/main/java/com/pontocafe/app/ui/OperationalPauseFeed.kt')
  const home = read('app/src/main/java/com/pontocafe/app/ui/AdminHomeScreenV2.kt')
  const operacao = read('app/src/main/java/com/pontocafe/app/ui/SupervisorOperationScreen.kt')

  // Cada cartão mantinha o seu ticker: com vinte pessoas em pausa eram vinte
  // corrotinas e vinte recomposições por segundo, todas a calcular o mesmo
  // instante. E acordava a cada 1000 ms, derivando ao longo do dia.
  assert.match(feed, /val LocalOperationalNow = staticCompositionLocalOf<State<Long>>/)
  assert.match(feed, /delay\(1_000L - instante % 1_000L\)/)
  assert.doesNotMatch(feed, /LaunchedEffect\(pause\.id, pause\.clienteAtualizadoEmMillis\)/)

  // As duas telas que mostram a lista precisam fornecer o relógio, senão os
  // cartões recebem o padrão parado.
  assert.match(home, /OperationalClockProvider \{/)
  assert.match(operacao, /OperationalClockProvider \{/)
})

test('o arco de contagem é desenhado, não recomposto', () => {
  const feed = read('app/src/main/java/com/pontocafe/app/ui/OperationalPauseFeed.kt')

  // O arco diz quanto resta sem obrigar a ler um número. O valor do relógio é
  // lido DENTRO do drawBehind: o traço muda a cada segundo e o cartão não
  // recompõe -- que é o ponto de o ter tirado do corpo do composable.
  assert.match(feed, /Modifier\.drawBehind \{/)
  assert.match(feed, /operationalPauseElapsed\(pause, relogio\.value\)/)
  assert.match(feed, /sweepAngle = 360f \* \(1f - fracao\)/)
  assert.match(feed, /style = Stroke\(width = traco, cap = StrokeCap\.Round\)/)
})
