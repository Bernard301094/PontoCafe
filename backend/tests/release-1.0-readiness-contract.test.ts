import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import test from 'node:test'

const read = (path: string) => readFileSync(new URL(`../../${path}`, import.meta.url), 'utf8')

const gradle = read('app/build.gradle.kts')
const backendPackage = read('backend/package.json')
const config = read('backend/src/config.ts')
const application = read('backend/src/application.ts')
const deployProduction = read('backend/scripts/deploy-production.mjs')
const wrangler = read('backend/wrangler.jsonc')
const reliability = read('backend/src/routes/reliability-routes.ts')
const telemetry = read('backend/src/routes/device-telemetry-routes.ts')
const diagnosticClient = read('app/src/main/java/com/pontocafe/app/data/AdminReliabilityApiClient.kt')
const diagnosticUi = read('app/src/main/java/com/pontocafe/app/ui/SystemDiagnosticsScreen.kt')
const biometricUi = read('app/src/main/java/com/pontocafe/app/ui/BiometricDiagnosticsScreen.kt')
const workflow = read('.github/workflows/validate.yml')
const releaseGate = read('scripts/check-release-1.0.mjs')
const migration = read('database/007_ponto_operation_idempotency.sql')
const readinessIndexes = read('database/008_release_readiness_indexes.sql')

test('Android candidata 1.0 mantém identidade Release e FaceNet compatível', () => {
  assert.match(gradle, /versionCode = 100/)
  assert.match(gradle, /versionName = "1\.0\.0"/)
  assert.match(gradle, /isMinifyEnabled = true/)
  assert.match(gradle, /isShrinkResources = true/)
  assert.match(gradle, /289bc10420aad15fed99094eee364eb24f908ecc/)
  assert.match(gradle, /8254aabae5cc73b8d2c15e7c589730eb3c264b87/)
  assert.match(gradle, /play-services-tflite-java:16\.5\.0/)
  assert.doesNotMatch(gradle, /play-services-tflite-gpu/)
})

test('backend e deploy publicam a mesma versão 1.0.0', () => {
  assert.match(backendPackage, /"version"\s*:\s*"1\.0\.0"/)
  assert.match(application, /const API_VERSION = '1\.0\.0'/)
  assert.match(application, /apiVersion: API_VERSION/)
  assert.match(deployProduction, /const expectedApiVersion = String\(backendPackage\.version/)
  assert.match(deployProduction, /status\.apiVersion !== expectedApiVersion/)
  assert.doesNotMatch(application, /apiVersion:\s*'0\.7\.0'/)
  assert.doesNotMatch(deployProduction, /status\.apiVersion !== '0\.7\.0'/)
})

test('thresholds biométricos não são relaxados para a 1.0', () => {
  assert.match(config, /FACE_MATCH_THRESHOLD', 0\.72/)
  assert.match(config, /FACE_IDENTIFICATION_MARGIN', 0\.06/)
  assert.match(wrangler, /"FACE_MATCH_THRESHOLD": "0\.72"/)
  assert.match(wrangler, /"FACE_IDENTIFICATION_MARGIN": "0\.06"/)
  assert.match(biometricUi, /Top-1 accuracy/)
  assert.match(biometricUi, /FRR/)
  assert.match(biometricUi, /FAR/)
})

test('diagnóstico 1.0 expõe integridade exactly-once sem chamar operações de duplicidades', () => {
  assert.match(reliability, /operacoesProtegidasUltimas24h/)
  assert.match(reliability, /registroRapidoUltimas24h/)
  assert.match(reliability, /iniciosUltimas24h/)
  assert.match(reliability, /retornosUltimas24h/)
  assert.match(diagnosticClient, /data class DiagnosticIntegrity/)
  assert.match(diagnosticUi, /Integridade do Ponto/)
  assert.match(diagnosticUi, /O contador não significa duplicidade/)
  assert.doesNotMatch(diagnosticUi, /duplicidades evitadas/i)
})

test('frota usa somente telemetria técnica e sinaliza versão/saúde', () => {
  assert.match(reliability, /APP_HEALTH/)
  assert.match(reliability, /comTelemetriaRecente/)
  assert.match(reliability, /semTelemetriaRecente/)
  assert.match(reliability, /desatualizados/)
  assert.match(reliability, /alertasSaude/)
  assert.match(diagnosticClient, /data class DiagnosticFleetDevice/)
  assert.match(diagnosticUi, /Frota de dispositivos/)
  assert.match(diagnosticUi, /Atualização disponível/)

  assert.doesNotMatch(telemetry, /embedding/i)
  assert.doesNotMatch(telemetry, /foto/i)
  assert.doesNotMatch(telemetry, /password/i)
  assert.doesNotMatch(telemetry, /verificacaoToken/i)
})

test('política de versão 1.0 mantém piso compatível com integridade 0.15 no código e no Worker', () => {
  assert.match(config, /APP_LATEST_ANDROID_VERSION', '1\.0\.0'/)
  assert.match(config, /APP_MIN_ANDROID_VERSION', '0\.15\.0'/)
  assert.match(wrangler, /"APP_LATEST_ANDROID_VERSION": "1\.0\.0"/)
  assert.match(wrangler, /"APP_MIN_ANDROID_VERSION": "0\.15\.0"/)
  assert.match(wrangler, /"PONTO_OPERATION_RETENTION_DAYS": "30"/)
  assert.match(wrangler, /"DEVICE_HEALTH_RETENTION_DAYS": "30"/)
})

test('migrações exactly-once e de retenção continuam obrigatórias na 1.0', () => {
  assert.match(migration, /create table if not exists operacoes_ponto_idempotentes/i)
  assert.match(migration, /REGISTRO_RAPIDO/)
  assert.match(migration, /INICIAR/)
  assert.match(migration, /FINALIZAR/)
  assert.match(readinessIndexes, /idx_operacoes_ponto_concluido_em/)
  assert.match(readinessIndexes, /idx_auditoria_app_health_dispositivo_criado/)
  assert.match(readinessIndexes, /idx_auditoria_app_health_criado/)
})

test('CI deixa de ser canary e executa gates reais de backend e Android', () => {
  assert.match(workflow, /npm --workspace backend run validate/)
  assert.match(workflow, /npm run release:check/)
  assert.match(workflow, /:app:testReleaseUnitTest :app:assembleRelease/)
  assert.match(workflow, /8254aabae5cc73b8d2c15e7c589730eb3c264b87/)
  assert.doesNotMatch(workflow, /RUNNER_OK/)
})

test('governança operacional é parte verificável da Release', () => {
  assert.match(releaseGate, /RELEASE_1_0_CHECKLIST/)
  assert.match(releaseGate, /DISASTER_RECOVERY/)
  assert.match(releaseGate, /PRIVACIDADE_BIOMETRICA/)

  for (const relative of [
    '../../docs/RELEASE_1_0_CHECKLIST.md',
    '../../docs/DISASTER_RECOVERY.md',
    '../../docs/PRIVACIDADE_BIOMETRICA.md',
  ]) {
    assert.ok(existsSync(new URL(relative, import.meta.url)))
  }
})
