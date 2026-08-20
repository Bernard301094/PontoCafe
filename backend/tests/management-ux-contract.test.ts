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
  assert.match(management, /if \(responsive\.isExpanded\) 2 else 1/)
})

test('editor de regras preserva validações e destaca alterações pendentes', () => {
  assert.match(management, /Alterações não salvas/)
  assert.match(management, /Descartar/)
  assert.match(management, /viewModel\.saveRule\(/)
  assert.match(management, /start >= end/)
  assert.match(management, /PontoCafeRules\.durationSeconds/)
  assert.match(management, /listOf\(10, 12, 15\)/)
})

test('teste operacional deixa de competir com as ações principais', () => {
  assert.match(management, /AnimatedVisibility\(showAdvanced\)/)
  assert.match(management, /Teste operacional/)
  assert.match(management, /PcAdminVisualTestTool/)
})

test('release da atualização de Gestão é 0.11.0', () => {
  assert.match(gradle, /versionCode = 32/)
  assert.match(gradle, /versionName = "0\.11\.0"/)
})
