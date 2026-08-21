import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const management = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/AdminManagementScreenV3.kt', import.meta.url),
  'utf8',
)
const compatibility = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/AdminManagementScreenV2.kt', import.meta.url),
  'utf8',
)
const gradle = readFileSync(
  new URL('../../app/build.gradle.kts', import.meta.url),
  'utf8',
)

test('Gestão usa a experiência V3 sem quebrar o shell existente', () => {
  assert.match(compatibility, /AdminManagementScreenV3\(/)
  assert.match(management, /fun AdminManagementScreenV3\(/)
})

test('Gestão organiza ações por operação e confiabilidade', () => {
  assert.match(management, /title = "Operação"/)
  assert.match(management, /title = "Confiabilidade e controle"/)
  assert.match(management, /Dispositivos/)
  assert.match(management, /Sincronização/)
  assert.match(management, /Autorizações/)
  assert.match(management, /Modo terminal/)
  assert.match(management, /Biometria/)
  assert.match(management, /Diagnóstico/)
  assert.match(management, /Auditoria/)
})

test('grid de Gestão responde aos breakpoints compartilhados', () => {
  assert.match(management, /PontoCafeWindowSizeClass\.COMPACT -> 1/)
  assert.match(management, /PontoCafeWindowSizeClass\.MEDIUM -> 2/)
  assert.match(management, /PontoCafeWindowSizeClass\.EXPANDED -> 3/)
  assert.match(management, /responsive\.isExpanded && responsive\.supportsTwoColumns/)
})

test('editor de regras preserva validações e destaca alterações pendentes', () => {
  assert.match(management, /Alterações não salvas/)
  assert.match(management, /Descartar/)
  assert.match(management, /viewModel\.saveRule\(/)
  assert.match(management, /start >= end/)
  assert.match(management, /PontoCafeRules\.durationSeconds/)
  assert.match(management, /listOf\(10, 12, 15\)/)
})

test('cada período distingue horários do tempo máximo de café', () => {
  assert.match(management, /Horários definem quando a pausa pode começar; tempos de café definem quanto ela pode durar/)
  assert.match(management, /CoffeeRuleSummaryCard\(/)
  assert.match(management, /title = "Horários"/)
  assert.match(management, /value = "\$start – \$end"/)
  assert.match(management, /title = "Tempo de café"/)
  assert.match(management, /PontoCafeRules\.formatDuration\(durationSeconds\)/)
  assert.match(management, /Limite máximo entre início e retorno/)
})

test('resumo usa duração configurada de verdade e não um valor fixo', () => {
  assert.match(management, /val durationSummary = remember\(reliability\.rules\)/)
  assert.match(management, /map \{ PontoCafeRules\.formatDuration\(it\.limiteSegundos\) \}/)
  assert.match(management, /durationSummary = durationSummary/)
  assert.doesNotMatch(management, /value = "15 min"/)
})

test('cards comunicam o estado da janela na hora oficial e respondem a telas estreitas', () => {
  assert.match(management, /ZoneId\.of\("America\/Fortaleza"\)/)
  assert.match(management, /label = "Em andamento"/)
  assert.match(management, /label = "Próximo hoje"/)
  assert.match(management, /label = "Encerrado hoje"/)
  assert.match(management, /maxWidth < 520\.dp \|\| LocalDensity\.current\.fontScale >= 1\.3f/)
  assert.match(management, /maxWidth < 420\.dp \|\| LocalDensity\.current\.fontScale >= 1\.3f/)
  assert.match(management, /Editar período/)
})

test('teste operacional deixa de competir com as ações principais', () => {
  assert.match(management, /AnimatedVisibility\(showAdvanced\)/)
  assert.match(management, /Teste operacional/)
  assert.match(management, /PcAdminVisualTestTool/)
})

test('Gestão permanece alinhada à versão 1.0.0 atual', () => {
  assert.match(gradle, /versionCode = 100/)
  assert.match(gradle, /versionName = "1\.0\.0"/)
})
