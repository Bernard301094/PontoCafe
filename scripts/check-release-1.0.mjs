import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'

function read(path) {
  assert.ok(existsSync(path), `Arquivo obrigatório ausente: ${path}`)
  return readFileSync(path, 'utf8')
}

const gradle = read('app/build.gradle.kts')
const backendConfig = read('backend/src/config.ts')
const wrangler = read('backend/wrangler.jsonc')
const migration = read('database/007_ponto_operation_idempotency.sql')
const readinessIndexes = read('database/008_release_readiness_indexes.sql')
const validateWorkflow = read('.github/workflows/validate.yml')
const releaseWorkflow = read('.github/workflows/build-release-unsigned.yml')
const reliability = read('backend/src/routes/reliability-routes.ts')
const telemetry = read('backend/src/routes/device-telemetry-routes.ts')
const maintenance = read('backend/src/maintenance.ts')
const cloudflare = read('backend/src/cloudflare.ts')
const envExample = read('.env.example')
const biometricUi = read('app/src/main/java/com/pontocafe/app/ui/BiometricDiagnosticsScreen.kt')
const systemUi = read('app/src/main/java/com/pontocafe/app/ui/SystemDiagnosticsScreen.kt')

for (const doc of [
  'docs/RELEASE_1_0_CHECKLIST.md',
  'docs/DISASTER_RECOVERY.md',
  'docs/PRIVACIDADE_BIOMETRICA.md',
]) {
  assert.ok(existsSync(doc), `Documento de produção ausente: ${doc}`)
}

assert.match(gradle, /versionCode = 100/)
assert.match(gradle, /versionName = "1\.0\.0"/)
assert.match(gradle, /isMinifyEnabled = true/)
assert.match(gradle, /isShrinkResources = true/)
assert.match(gradle, /compileSdk = 36/)
assert.match(gradle, /targetSdk = 36/)
assert.match(gradle, /faceModelCommit = "289bc10420aad15fed99094eee364eb24f908ecc"/)
assert.match(gradle, /faceModelBlobSha = "8254aabae5cc73b8d2c15e7c589730eb3c264b87"/)
assert.match(gradle, /play-services-tflite-java:16\.5\.0/)
assert.doesNotMatch(gradle, /play-services-tflite-gpu/)

assert.match(backendConfig, /FACE_MATCH_THRESHOLD', 0\.72/)
assert.match(backendConfig, /FACE_IDENTIFICATION_MARGIN', 0\.06/)
assert.match(backendConfig, /PONTO_OPERATION_RETENTION_DAYS', 30/)
assert.match(backendConfig, /DEVICE_HEALTH_RETENTION_DAYS', 30/)
assert.match(backendConfig, /APP_LATEST_ANDROID_VERSION', '1\.0\.0'/)
assert.match(backendConfig, /APP_MIN_ANDROID_VERSION', '0\.15\.0'/)
assert.match(wrangler, /"FACE_MATCH_THRESHOLD": "0\.72"/)
assert.match(wrangler, /"FACE_IDENTIFICATION_MARGIN": "0\.06"/)
assert.match(wrangler, /"PONTO_OPERATION_RETENTION_DAYS": "30"/)
assert.match(wrangler, /"DEVICE_HEALTH_RETENTION_DAYS": "30"/)
assert.match(wrangler, /"APP_LATEST_ANDROID_VERSION": "1\.0\.0"/)
assert.match(wrangler, /"APP_MIN_ANDROID_VERSION": "0\.15\.0"/)

assert.match(migration, /create table if not exists operacoes_ponto_idempotentes/i)
assert.match(migration, /REGISTRO_RAPIDO/)
assert.match(migration, /INICIAR/)
assert.match(migration, /FINALIZAR/)
assert.match(readinessIndexes, /idx_operacoes_ponto_concluido_em/)
assert.match(readinessIndexes, /idx_auditoria_app_health_dispositivo_criado/)
assert.match(readinessIndexes, /where acao='APP_HEALTH' and entidade='DISPOSITIVO'/)

assert.match(validateWorkflow, /npm --workspace backend run validate/)
assert.match(validateWorkflow, /npm run release:check/)
assert.match(validateWorkflow, /:app:testReleaseUnitTest :app:assembleRelease/)
assert.match(validateWorkflow, /8254aabae5cc73b8d2c15e7c589730eb3c264b87/)
assert.match(releaseWorkflow, /:app:testReleaseUnitTest :app:assembleRelease/)
assert.match(releaseWorkflow, /8254aabae5cc73b8d2c15e7c589730eb3c264b87/)

assert.match(reliability, /operacoesProtegidasUltimas24h/)
assert.match(reliability, /comTelemetriaRecente/)
assert.match(reliability, /alertasSaude/)
assert.match(telemetry, /APP_HEALTH/)
assert.doesNotMatch(telemetry, /embedding/i)
assert.doesNotMatch(telemetry, /foto/i)
assert.doesNotMatch(telemetry, /senha/i)
assert.doesNotMatch(telemetry, /unlock.*pin/i)

assert.match(maintenance, /cleanupExpiredPontoOperations/)
assert.match(maintenance, /delete from operacoes_ponto_idempotentes/)
assert.match(maintenance, /cleanupExpiredDeviceHealthTelemetry/)
assert.match(maintenance, /acao='APP_HEALTH'/)
assert.match(cloudflare, /cleanupExpiredPontoOperations/)
assert.match(cloudflare, /cleanupExpiredDeviceHealthTelemetry/)
assert.match(envExample, /PONTO_OPERATION_RETENTION_DAYS=30/)
assert.match(envExample, /DEVICE_HEALTH_RETENTION_DAYS=30/)

assert.match(biometricUi, /Top-1 accuracy/)
assert.match(biometricUi, /FRR/)
assert.match(biometricUi, /FAR/)
assert.match(systemUi, /Integridade do Ponto/)
assert.match(systemUi, /Frota de dispositivos/)
assert.match(systemUi, /minimização de dados/)

console.log('PontoCafe 1.0 release contract: OK')
