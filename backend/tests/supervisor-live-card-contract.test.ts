import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const screen = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/SupervisorLiveScreenV2.kt', import.meta.url),
  'utf8',
)

test('Supervisor ao vivo usa um relógio compartilhado para todos os cards', () => {
  assert.match(screen, /var liveNow by remember \{ mutableLongStateOf\(System\.currentTimeMillis\(\)\) \}/)
  assert.match(screen, /LivePauseCard\(pause = pause, now = liveNow\)/)
  assert.doesNotMatch(screen, /remember\(pause\.id\) \{ mutableLongStateOf/)
})

test('casos acima do limite aparecem primeiro', () => {
  assert.match(screen, /compareByDescending<PausaSupervisor> \{ supervisorLiveSeconds\(it, liveNow\) > it\.limiteSegundos \}/)
  assert.match(screen, /Casos acima do limite aparecem primeiro/)
})

test('card de pausa comunica progresso, proximidade e excesso', () => {
  assert.match(screen, /LinearProgressIndicator/)
  assert.match(screen, /Próximo do limite/)
  assert.match(screen, /Limite excedido/)
  assert.match(screen, /Ação necessária/)
  assert.match(screen, /Tempo da pausa/)
  assert.match(screen, /pause\.setor/)
})
