import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import test from 'node:test'

const read = (path: string) => readFileSync(new URL(`../../${path}`, import.meta.url), 'utf8')

const gradle = read('app/build.gradle.kts')
const backendPackage = read('backend/package.json')
const config = read('backend/src/config.ts')
const application = read('backend/src/application.ts')
const deployProduction = read('backend/scripts/deploy-production.mjs')
const deployCloudflare = read('scripts/deploy-cloudflare.mjs')
const wrangler = read('backend/wrangler.jsonc')
const reliability = read('backend/src/routes/reliability-routes.ts')
const telemetry = read('backend/src/routes/device-telemetry-routes.ts')
const diagnosticClient = read('app/src/main/java/com/pontocafe/app/data/AdminReliabilityApiClient.kt')
const diagnosticUi = read('app/src/main/java/com/pontocafe/app/ui/SystemDiagnosticsScreen.kt')
const manifest = read('app/src/main/AndroidManifest.xml')
const scannerSheet = read('app/src/main/java/com/pontocafe/app/ui/QrScannerSheet.kt')
const flowHost = read('app/src/main/java/com/pontocafe/app/ui/PontoFlowHost.kt')
const workflow = read('.github/workflows/validate.yml')
const releaseGate = read('scripts/check-release-1.0.mjs')
const migration = read('database/007_ponto_operation_idempotency.sql')
const readinessIndexes = read('database/008_release_readiness_indexes.sql')

test('Android candidata 1.0 mantém identidade Release e nenhum vestígio de biometria', () => {
  assert.match(gradle, /versionCode = 110/)
  assert.match(gradle, /versionName = "1\.1\.0"/)
  assert.match(gradle, /isMinifyEnabled = true/)
  assert.match(gradle, /isShrinkResources = true/)

  // O APK deixou de reconhecer rostos, e isso não mudou.
  //
  // O que mudou foi o alcance da guarda. Enquanto não havia uso legítimo de
  // câmera, proibi-la inteira era a forma barata de proibir a biometria. A
  // leitura do QR do café trouxe um uso legítimo, e a guarda estreitou-se para
  // o que sempre quis dizer: nada que reconheça pessoas.
  //
  // A distinção não é retórica. O caminho do QR lê um quadrado preto e branco e
  // devolve 52 caracteres de texto; não extrai template, não guarda imagem, não
  // carrega modelo. Um motor de rosto faz as três coisas -- e é por isso que
  // são estes nomes que continuam banidos, e não a palavra "câmera".
  assert.doesNotMatch(gradle, /play-services-tflite/)
  assert.doesNotMatch(gradle, /mlkit/)
  assert.doesNotMatch(gradle, /facenet/i)
  assert.doesNotMatch(gradle, /face-detection|face_detection|facedetect/i)
})

test('a câmera do APK só sabe ler QR, e só quando alguém a pede', () => {
  // O decodificador é o ZXing: Java puro, sem modelo e sem Play Services. Trocá-lo
  // por um SDK de visão seria o caminho por onde a biometria voltaria.
  assert.match(gradle, /com\.google\.zxing:core/)
  assert.match(gradle, /androidx\.camera:camera-core/)

  // Opcional no manifesto: um totem de parede sem lente continua a bater ponto
  // pelo código digitado e não pode ficar fora da loja.
  assert.match(manifest, /android:name="android\.hardware\.camera\.any" android:required="false"/)

  // A permissão é pedida no toque em "Ler meu QR", nunca no arranque, e o botão
  // só existe onde a operação liberou a leitura.
  assert.match(scannerSheet, /pedirPermissao\.launch\(Manifest\.permission\.CAMERA\)/)
  assert.match(scannerSheet, /if \(!temPermissao\) pedirPermissao\.launch/)
  assert.match(flowHost, /if \(state\.qrHabilitado\)/)

  // Nenhum quadro é guardado: o analisador lê o plano de luminância, decodifica
  // e fecha a imagem. Se isto virasse uma gravação, seria aqui.
  assert.match(scannerSheet, /image\.close\(\)/)
  assert.doesNotMatch(scannerSheet, /MediaStore|FileOutputStream|ImageCapture|takePicture|toBitmap\(\)/)
})

test('backend e os dois caminhos de deploy publicam a mesma versão 1.0.0', () => {
  assert.match(backendPackage, /"version"\s*:\s*"1\.1\.0"/)
  assert.match(application, /const API_VERSION = '1\.1\.0'/)
  assert.match(application, /apiVersion: API_VERSION/)
  assert.match(deployProduction, /const expectedApiVersion = String\(backendPackage\.version/)
  assert.match(deployProduction, /status\.apiVersion !== expectedApiVersion/)
  assert.match(deployCloudflare, /const expectedApiVersion = String\(backendPackage\.version/)
  assert.match(deployCloudflare, /status\.apiVersion !== expectedApiVersion/)
  assert.match(deployCloudflare, /status\.latestAndroidVersion !== expectedAndroidVersion/)
  assert.match(deployCloudflare, /status\.minimumAndroidVersion !== expectedMinimumAndroidVersion/)
  assert.doesNotMatch(application, /apiVersion:\s*'0\.7\.0'/)
  assert.doesNotMatch(deployProduction, /status\.apiVersion !== '0\.7\.0'/)
})

test('os dois caminhos de deploy carimbam a revisão e conferem se ela chegou', () => {
  // O wrangler.jsonc usa keep_vars, então um BACKEND_REVISION já gravado
  // sobrevive a qualquer publicação que não o reescreva. Foi assim que o
  // /app-status passou a descrever um commit de duas semanas antes do código
  // que estava realmente a correr: o deploy do backend carimbava só a tag da
  // versão, e a verificação final só olhava para essa tag.
  for (const [label, source] of [
    ['deploy-production', deployProduction],
    ['deploy-cloudflare', deployCloudflare],
  ] as const) {
    assert.match(
      source,
      /'--var',\s*\r?\n?\s*`BACKEND_REVISION:\$\{backendRevision\}`/,
      `${label} precisa publicar BACKEND_REVISION`,
    )
    assert.match(
      source,
      /status\.backendRevision !== backendRevision/,
      `${label} precisa confirmar a revisão publicada`,
    )
    // O SHA inteiro nos dois: uma revisão curta num e longa no outro faria o
    // mesmo commit aparecer com dois nomes conforme o caminho usado.
    assert.match(
      source,
      /rev-parse', 'HEAD'\]/,
      `${label} precisa usar o SHA completo na revisão`,
    )
    // O retry tem de esperar a versão certa, não só uma resposta que funcione:
    // um 200 vindo da versão anterior é indistinguível de sucesso, e foi assim
    // que um deploy bom foi anunciado como falha.
    assert.match(
      source,
      /if \(!until \|\| until\(payload\)\) return payload/,
      `${label} precisa repetir enquanto a resposta for de uma versão anterior`,
    )
    assert.match(
      source,
      /until: \(payload\) => payload\.(workerVersionTag|backendRevision) ===/,
      `${label} precisa dizer qual versão está a esperar`,
    )
  }
})

test('deploy Cloudflare exige validação, release contract e dry-run antes de publicar', () => {
  assert.match(deployCloudflare, /\['--workspace', 'backend', 'run', 'validate'\]/)
  assert.match(deployCloudflare, /\['run', 'release:check'\]/)
  assert.match(deployCloudflare, /\['deploy', '--dry-run'\]/)
  assert.match(deployCloudflare, /cwd: backendDir/)
})

test('a política do código de acesso é explícita no backend e no Worker', () => {
  assert.match(config, /accessCodeTtlSeconds/)
  assert.match(config, /accessCodeMaxAttempts/)
  assert.match(config, /coffeeGraceSeconds/)
  assert.match(wrangler, /"ACCESS_CODE_TTL_SECONDS"/)
  assert.match(wrangler, /"COFFEE_GRACE_SECONDS": "60"/)
  // Nenhum threshold facial pode sobreviver à remoção do reconhecimento.
  assert.doesNotMatch(config, /FACE_MATCH_THRESHOLD/)
  assert.doesNotMatch(wrangler, /FACE_/)
})

test('diagnóstico 1.0 expõe integridade exactly-once sem chamar operações de duplicidades', () => {
  assert.match(reliability, /operacoesProtegidasUltimas24h/)
  assert.match(reliability, /registros_codigo_24h/)
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
  assert.match(config, /APP_LATEST_ANDROID_VERSION', '1\.1\.0'/)
  assert.match(config, /APP_MIN_ANDROID_VERSION', '1\.1\.0'/)
  assert.match(wrangler, /"APP_LATEST_ANDROID_VERSION": "1\.1\.0"/)
  assert.match(wrangler, /"APP_MIN_ANDROID_VERSION": "1\.1\.0"/)
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
  assert.doesNotMatch(workflow, /RUNNER_OK/)
})

test('governança operacional é parte verificável da Release', () => {
  assert.match(releaseGate, /RELEASE_1_0_CHECKLIST/)
  assert.match(releaseGate, /DISASTER_RECOVERY/)
  assert.match(releaseGate, /PRIVACIDADE_DADOS/)

  for (const relative of [
    '../../docs/RELEASE_1_0_CHECKLIST.md',
    '../../docs/DISASTER_RECOVERY.md',
    '../../docs/PRIVACIDADE_DADOS.md',
  ]) {
    assert.ok(existsSync(new URL(relative, import.meta.url)))
  }
})
