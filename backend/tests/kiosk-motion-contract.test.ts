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

test('todo número de métrica rola, e um alerta pode ser dispensado', () => {
  const home = read('app/src/main/java/com/pontocafe/app/ui/AdminHomeScreenV2.kt')
  const alerts = read('app/src/main/java/com/pontocafe/app/ui/SupervisorLiveAlerts.kt')
  const operacao = read('app/src/main/java/com/pontocafe/app/ui/SupervisorOperationScreen.kt')

  // Três dos quatro tiles já rolavam; o quarto escrevia o número cru, e a
  // diferença aparecia com eles lado a lado na mesma tela.
  assert.match(home, /Text\(animatedMetricValue\(value\), style = MaterialTheme\.typography\.titleMedium/)

  // O alerta é derivado do estado ao vivo e não tinha como ser dispensado:
  // quem já leu "Maria excedeu o limite" continuava com o aviso no topo
  // enquanto tratava do assunto.
  assert.match(alerts, /SwipeToDismissBox\(/)
  assert.match(alerts, /onDispensar: \(\) -> Unit = \{\}/)
  assert.match(alerts, /dismissState\.progress\.coerceIn\(0f, 1f\)/)

  // A dispensa é da sessão, não persistida: o alerta descreve uma condição que
  // pode voltar a acontecer amanhã, e escondê-la para sempre seria pior.
  assert.match(operacao, /var alertasDispensados by remember/)
  assert.match(operacao, /it\.id !in alertasDispensados/)
})

test('a telemetria mostra estado, e nenhuma animação fica em laço', () => {
  const sync = read('app/src/main/java/com/pontocafe/app/ui/SyncCenterScreen.kt')
  const diag = read('app/src/main/java/com/pontocafe/app/ui/SystemDiagnosticsScreen.kt')
  const central = read('app/src/main/java/com/pontocafe/app/ui/OperationalAlertCenter.kt')

  // A fila era só uma contagem em texto: "7" não diz se aquilo anda ou está
  // parado, que é a pergunta de quem abre a tela depois de uma manhã sem rede.
  assert.match(sync, /private fun SyncProgressRing\(/)
  assert.match(sync, /sweepAngle = 360f \* fracao/)
  // Chegar a zero e ficar a pulsar seria movimento permanente a dizer que já
  // não há nada a fazer: a onda corre uma vez, na transição.
  assert.match(sync, /LaunchedEffect\(concluido\)/)

  // O flash não dispara na primeira leitura -- abrir a tela com o sistema em
  // atenção não é uma mudança de estado, e piscar aí seria alarme falso.
  assert.match(diag, /var primeiraLeitura by remember/)
  assert.match(diag, /Modifier\.drawWithContent \{/)

  // O sino balança quando chega aviso, e só então.
  assert.match(central, /LaunchedEffect\(unread\)/)
  assert.match(central, /transformOrigin = TransformOrigin\(0\.5f, 0\.1f\)/)

  for (const [label, fonte] of [['sync', sync], ['diagnóstico', diag], ['alertas', central]] as const) {
    assert.doesNotMatch(
      fonte,
      /rememberInfiniteTransition|infiniteRepeatable/,
      `${label} não pode animar em laço: são telas que ficam abertas por minutos`,
    )
  }
})

test('a força da senha é medida uma vez só, e responde "já chega?"', () => {
  const forca = read('app/src/main/java/com/pontocafe/app/ui/PasswordStrength.kt')
  const admin = read('app/src/main/java/com/pontocafe/app/ui/FirstAdminSetupScreen.kt')
  const supervisor = read('app/src/main/java/com/pontocafe/app/ui/SupervisorInitialPasswordChangeScreen.kt')

  // Eram duas implementações: quatro itens com visto no primeiro Administrador,
  // três linhas de texto na troca do Supervisor. Nenhuma dizia QUÃO forte a
  // senha estava -- respondiam "falta quê", não "já chega?".
  assert.match(forca, /fun PontoPasswordStrength\(/)
  assert.match(admin, /PontoPasswordStrength\(/)
  assert.match(supervisor, /PontoPasswordStrength\(/)
  assert.doesNotMatch(admin, /private fun PasswordStrengthChecklist/)
  assert.doesNotMatch(supervisor, /private fun PasswordRule/)

  // Cor e barra interpolam: um salto seco de vermelho para âmbar lê-se como
  // erro, não como progresso.
  assert.match(forca, /animateColorAsState/)
  assert.match(forca, /animationSpec = PontoSprings\.Surface/)
  assert.match(forca, /rotationZ = \(1f - progresso\) \* -90f/)

  // Coincidir não é força: uma senha fraca digitada duas vezes iguais continua
  // fraca, e somá-la à barra inflaria a medida.
  assert.match(forca, /fun PontoPasswordConfirmation\(/)
  assert.match(supervisor, /PontoPasswordConfirmation\(/)
})
