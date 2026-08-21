import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const route = readFileSync(new URL('../src/routes/device-activation-routes.ts', import.meta.url), 'utf8')
const migration = readFileSync(new URL('../../database/004_device_registration_idempotency.sql', import.meta.url), 'utf8')
const android = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/data/AdminApiClient.kt', import.meta.url),
  'utf8',
)
const pontoAndroid = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/data/ApiClient.kt', import.meta.url),
  'utf8',
)
const androidViewModel = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/AdminDeviceViewModel.kt', import.meta.url),
  'utf8',
)
const activationTokenStore = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/data/SecureAdminDeviceActivationTokenStore.kt', import.meta.url),
  'utf8',
)
const sharedRoute = readFileSync(new URL('../src/routes/shared.ts', import.meta.url), 'utf8')
const application = readFileSync(new URL('../src/application.ts', import.meta.url), 'utf8')
const maintenance = readFileSync(new URL('../src/maintenance.ts', import.meta.url), 'utf8')
const managementRoute = readFileSync(new URL('../src/routes/device-management-routes.ts', import.meta.url), 'utf8')
const reliabilityRoute = readFileSync(new URL('../src/routes/reliability-routes.ts', import.meta.url), 'utf8')
const deviceScreen = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/AdminDevicesScreenV2.kt', import.meta.url),
  'utf8',
)

test('cadastro de dispositivo continua restrito a ADMIN', () => {
  assert.match(route, /requireUser, requireRole\('ADMIN'\)/)
})

test('endpoint e token de 10 caracteres permanecem no contrato atual', () => {
  assert.match(route, /post\('\/device-activation'/)
  assert.match(route, /newDeviceToken\(10\)/)
})

test('cadastro atual exige PIN individual e não mantém fallback sem PIN', () => {
  assert.match(route, /pin:\s*z\.string\(\)\.trim\(\)\.regex\(\/\^\\d\{4,12\}\$\//s)
  assert.doesNotMatch(route, /regex\(\/\^\\d\{4,12\}\$\/\)\.optional\(\)/s)
  assert.match(route, /hashDeviceUnlockPin\(deviceId, body\.data\.pin\)/)
  assert.match(route, /pinConfigurado:\s*true/)
})

test('Idempotency-Key é obrigatória e clientes legados não recebem chave efêmera', () => {
  assert.match(route, /IDEMPOTENCY_KEY_REQUIRED/)
  assert.match(route, /if \(!incomingIdempotencyKey\)/)
  assert.match(route, /const idempotencyKey = incomingIdempotencyKey/)
  assert.doesNotMatch(route, /legacy:\$\{newId\(\)\}/)
})

test('criação idempotente preserva a query CTE validada em PostgreSQL', () => {
  assert.match(route, /with idempotencia as/i)
  assert.match(route, /novo_dispositivo as/i)
  assert.match(route, /auditoria_criacao as/i)
  assert.doesNotMatch(route, /pool\.connect\(/)
  assert.doesNotMatch(route, /client\.query\(['"]BEGIN/i)
})

test('UUIDs permanecem UUID dentro do CTE antes das comparações', () => {
  assert.doesNotMatch(route, /request_nonce::text/i)
  assert.doesNotMatch(route, /dispositivo_id::text,\s*token_ciphertext/i)
  assert.match(route, /where request_nonce=\$4::uuid/i)
  assert.match(route, /\(i\.request_nonce=\$4::uuid\) as criado_agora/i)
})

test('replay não pode criar nova auditoria quando request_nonce não pertence à tentativa', () => {
  assert.match(route, /from idempotencia\s+where request_nonce=\$4::uuid/i)
  assert.match(route, /from novo_dispositivo\s+returning id/i)
})

test('replay confirmado retorna HTTP 200 sem alterar o token original', () => {
  assert.match(route, /registration\.criado_agora \? 201 : 200/)
  assert.match(route, /replayIdempotente:\s*!registration\.criado_agora/)
  assert.match(route, /decryptDeviceRegistrationToken\(/)
})

test('falha 500 informa etapa e classificação segura sem expor segredo', () => {
  assert.match(route, /safeErrorDescriptor\(error\)/)
  assert.match(route, /Diagnóstico \$\{stage\}\/\$\{descriptor\.code\}/)
  assert.match(route, /codigoBanco:\s*descriptor\.databaseCode/)
  assert.doesNotMatch(route, /console\.(?:log|error).*body\.data\.pin/s)
  assert.doesNotMatch(route, /console\.(?:log|error).*responseToken/s)
})

test('migração de idempotência guarda somente token cifrado para replay temporário', () => {
  assert.match(migration, /token_ciphertext bytea not null/i)
  assert.match(migration, /token_iv bytea not null/i)
  assert.match(migration, /token_auth_tag bytea not null/i)
  assert.doesNotMatch(migration, /\bactivation_token\b/i)
  assert.doesNotMatch(migration, /\btoken_plain/i)
})

test('Android envia Idempotency-Key no endpoint administrativo existente', () => {
  assert.match(android, /@POST\("admin\/device-activation"\)/)
  assert.match(android, /@Header\("Idempotency-Key"\) idempotencyKey: String/)
  assert.match(android, /data class CreateDeviceRequest\(val nome: String, val pin: String\)/)
})

test('Android reutiliza a mesma chave enquanto repete o mesmo cadastro após falha transitória', () => {
  assert.match(androidViewModel, /pendingRegistrationKey: String\? = null/)
  assert.match(androidViewModel, /pendingRegistrationFingerprint: String\? = null/)
  assert.match(androidViewModel, /UUID\.randomUUID\(\)\.toString\(\)/)
  assert.match(androidViewModel, /repository\.createDevice\(cleanName, cleanPin, idempotencyKey\)/)
  assert.match(androidViewModel, /shouldDiscardPendingRegistration\(error\)/)
  assert.match(androidViewModel, /error\.code\(\) in setOf\(400, 409, 422\)/)
})

test('token gerado fica associado ao ID exato do dispositivo', () => {
  assert.match(androidViewModel, /val tokenDeviceId: String\? = null/)
  assert.match(androidViewModel, /tokenDeviceId = created\.id/)
  assert.match(androidViewModel, /tokenDeviceId = rotated\.dispositivoId/)
})

test('token pendente é persistido cifrado pelo Android Keystore e vinculado ao deviceId', () => {
  assert.match(activationTokenStore, /AndroidKeyStore/)
  assert.match(activationTokenStore, /AES\/GCM\/NoPadding/)
  assert.match(activationTokenStore, /setKeySize\(256\)/)
  assert.match(activationTokenStore, /cipher\.updateAAD\(cleanDeviceId\.toByteArray/)
  assert.match(activationTokenStore, /TOKEN_PATTERN = Regex\("\^\[A-Za-z0-9\]\{10\}\$"\)/)
  assert.doesNotMatch(activationTokenStore, /Log\./)
})

test('token aparece no diálogo e permanece disponível no cartão enquanto aguarda ativação', () => {
  assert.match(deviceScreen, /state\.tokenGerado\?\.let \{ token ->/)
  assert.match(deviceScreen, /activationTokenStore\.save\(deviceId, token\)/)
  assert.match(deviceScreen, /DeviceTokenPanel\(/)
  assert.match(deviceScreen, /Disponível também no cartão/)
  assert.match(deviceScreen, /Copiar token/)
  assert.match(deviceScreen, /clipboard\.setText\(AnnotatedString\(activationToken\)\)/)
})

test('token de ativação local é removido quando o dispositivo deixa de estar pendente', () => {
  assert.match(deviceScreen, /filter \{ it\.ativo && it\.statusAtivacao != "ATIVADO" \}/)
  assert.match(deviceScreen, /activationTokenStore\.reconcile\(pendingIds\)/)
  assert.match(deviceScreen, /credencial longa do Ponto não é recuperável por segurança/i)
})

test('Android envia versão, modelo real e Android em toda requisição autenticada do Ponto', () => {
  assert.match(pontoAndroid, /import android\.os\.Build/)
  assert.match(pontoAndroid, /X-App-Version/)
  assert.match(pontoAndroid, /X-Device-Model/)
  assert.match(pontoAndroid, /X-Android-Version/)
  assert.match(pontoAndroid, /Build\.MANUFACTURER/)
  assert.match(pontoAndroid, /Build\.MODEL/)
  assert.match(pontoAndroid, /Build\.VERSION\.SDK_INT/)
})

test('Worker aceita metadados reais e registra heartbeat apenas para dispositivo autenticado', () => {
  assert.match(application, /X-Device-Model/)
  assert.match(application, /X-Android-Version/)
  assert.match(sharedRoute, /DEVICE_HEARTBEAT/)
  assert.match(sharedRoute, /recordDeviceHeartbeat/)
  assert.match(sharedRoute, /interval '15 minutes'/)
  const authPosition = sharedRoute.indexOf("c.set('device', device)")
  const heartbeatPosition = sharedRoute.indexOf('recordDeviceHeartbeat', authPosition)
  assert.ok(authPosition >= 0 && heartbeatPosition > authPosition)
})

test('heartbeat tem a mesma retenção da telemetria de saúde', () => {
  assert.match(maintenance, /acao in \('APP_HEALTH','DEVICE_HEARTBEAT'\)/)
  assert.match(maintenance, /deviceHealthRetentionDays/)
})

test('lista administrativa do backend continua sem expor credencial do dispositivo em texto puro', () => {
  const start = managementRoute.indexOf("deviceManagementRoutes.get('/devices'")
  const end = managementRoute.indexOf("deviceManagementRoutes.put('/devices/:id/unlock-pin'", start)
  assert.ok(start >= 0 && end > start)
  const listRoute = managementRoute.slice(start, end)

  assert.doesNotMatch(listRoute, /activationToken|tokenGerado|token_ciphertext/)
  assert.match(listRoute, /statusAtivacao/)
  assert.match(listRoute, /telemetriaEm/)
})

test('status e última atividade são derivados de uso autenticado real, não da data de criação', () => {
  const start = managementRoute.indexOf("deviceManagementRoutes.get('/devices'")
  const end = managementRoute.indexOf("deviceManagementRoutes.put('/devices/:id/unlock-pin'", start)
  const listRoute = managementRoute.slice(start, end)

  assert.match(listRoute, /pause_activity\.ultima_pausa_em/)
  assert.match(listRoute, /DEVICE_HEARTBEAT/)
  assert.match(listRoute, /activation\.ultima_ativacao_em/)
  assert.match(listRoute, /rotation\.ultima_rotacao_em>greatest/)
  assert.doesNotMatch(listRoute, /greatest\(\s*d\.criado_em/)
})

test('modelo, Android e versão preferem heartbeat real sem apagar dados de saúde', () => {
  assert.match(managementRoute, /metadataString\(heartbeatDetails, healthDetails, 'appVersion'\)/)
  assert.match(managementRoute, /metadataString\(heartbeatDetails, healthDetails, 'deviceModel'\)/)
  assert.match(managementRoute, /metadataString\(heartbeatDetails, healthDetails, 'androidVersion'\)/)
  assert.match(managementRoute, /telemetryCounter\(healthDetails\.crashCount\)/)
  assert.match(managementRoute, /hasRecentHealthAlert\(device\.telemetriaDetalhes\)/)
  assert.match(reliabilityRoute, /DEVICE_HEARTBEAT/)
  assert.match(reliabilityRoute, /metadataString\(heartbeatDetails, healthDetails, 'deviceModel'\)/)
})

test('cards expõem edição, bloqueio e exclusão sem esconder as ações em menus', () => {
  assert.match(deviceScreen, /Editar dispositivo/)
  assert.match(deviceScreen, /Bloquear acesso/)
  assert.match(deviceScreen, /Excluir dispositivo/)
  assert.match(deviceScreen, /viewModel\.renomear\(device, newName\)/)
  assert.match(deviceScreen, /viewModel\.desativar\(device\)/)
  assert.match(deviceScreen, /viewModel\.excluirPermanentemente\(device\)/)
})

test('exclusão preserva histórico e remove o dispositivo da gestão por arquivamento auditável', () => {
  assert.match(managementRoute, /ARQUIVAR_DISPOSITIVO/)
  assert.match(managementRoute, /historicoPreservado:\s*true/)
  assert.match(managementRoute, /removidoDaGestao:\s*true/)
  assert.match(managementRoute, /status:\s*'ARCHIVED'/)
  assert.match(managementRoute, /where not exists[\s\S]*ARQUIVAR_DISPOSITIVO/)
})

test('bloqueio é idempotente e alteração de PIN não depende de o aparelho estar ativo', () => {
  const deactivateStart = managementRoute.indexOf("deviceManagementRoutes.post('/devices/:id/desativar'")
  const rotateStart = managementRoute.indexOf("deviceManagementRoutes.post('/devices/:id/novo-token'", deactivateStart)
  const deactivateRoute = managementRoute.slice(deactivateStart, rotateStart)
  assert.match(deactivateRoute, /jaEstavaInativo/)
  assert.doesNotMatch(deactivateRoute, /já está inativo/i)

  const pinStart = managementRoute.indexOf("deviceManagementRoutes.put('/devices/:id/unlock-pin'")
  const renameStart = managementRoute.indexOf("deviceManagementRoutes.put('/devices/:id/nome'", pinStart)
  const pinRoute = managementRoute.slice(pinStart, renameStart)
  assert.doesNotMatch(pinRoute, /ativo=true/)
})

test('cards continuam mostrando ativação, versão, atividade, saúde e estado operacional', () => {
  assert.match(managementRoute, /APP_HEALTH/)
  assert.match(managementRoute, /DEVICE_HEARTBEAT/)
  assert.match(managementRoute, /ATIVAR_DISPOSITIVO/)
  assert.match(managementRoute, /ROTACIONAR_TOKEN_DISPOSITIVO/)
  assert.match(managementRoute, /alertaSaude/)
  assert.match(deviceScreen, /label = "Ativação"/)
  assert.match(deviceScreen, /label = "Aplicativo"/)
  assert.match(deviceScreen, /label = "Última atividade"/)
  assert.match(deviceScreen, /label = "Saúde e segurança"/)
  assert.match(deviceScreen, /Aguardando ativação/)
  assert.match(deviceScreen, /Atualização disponível/)
  assert.match(deviceScreen, /Sem telemetria recente/)
})
