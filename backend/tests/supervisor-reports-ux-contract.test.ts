import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const reports = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/SupervisorReportsScreenV2.kt', import.meta.url),
  'utf8',
)
const gradle = readFileSync(
  new URL('../../app/build.gradle.kts', import.meta.url),
  'utf8',
)

test('relatórios mantêm seleção de período e consulta por calendário', () => {
  assert.match(reports, /Período do relatório/)
  assert.match(reports, /Hoje/)
  assert.match(reports, /7 dias/)
  assert.match(reports, /30 dias/)
  assert.match(reports, /viewModel::abrirRelatorios/)
  assert.match(reports, /viewModel\.carregarRelatorio\(date\.toString\(\), date\.toString\(\)\)/)
})

test('emissão de relatório é ação principal e preserva PDF e CSV', () => {
  assert.match(reports, /Emitir relatório/)
  assert.match(reports, /ModalBottomSheet/)
  assert.match(reports, /Gerar e compartilhar PDF/)
  assert.match(reports, /Exportar e compartilhar CSV/)
  assert.match(reports, /createSupervisorPdfReportV2/)
  assert.match(reports, /viewModel\.baixarRelatorioCsv\(\)/)
  assert.match(reports, /shareSupervisorReportV2/)
  assert.match(reports, /FileProvider\.getUriForFile/)
})

test('dashboard de relatórios adapta métricas sem remover análise operacional', () => {
  assert.match(reports, /ReportMetricsGrid/)
  assert.match(reports, /responsive\.isExpanded/)
  assert.match(reports, /PcReportComparisonCard/)
  assert.match(reports, /PcReportTrendChart/)
  assert.match(reports, /Registros por data/)
  assert.match(reports, /Excessos que pedem atenção/)
  assert.match(reports, /viewModel\.abrirHistorico\(day\.data\)/)
})

test('release da experiência de contas e relatórios é 0.14.0', () => {
  assert.match(gradle, /versionCode = 35/)
  assert.match(gradle, /versionName = "0\.14\.0"/)
  assert.match(gradle, /isMinifyEnabled = true/)
  assert.match(gradle, /isShrinkResources = true/)
})
