import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const androidApi = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/data/ApiClient.kt', import.meta.url),
  'utf8',
)
const adminApi = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/data/AdminApiClient.kt', import.meta.url),
  'utf8',
)
const adminViewModel = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/AdminDeviceViewModel.kt', import.meta.url),
  'utf8',
)
const pontoViewModel = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/PontoCafeViewModel.kt', import.meta.url),
  'utf8',
)
const adminDevicesUi = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/AdminDevicesScreenV2.kt', import.meta.url),
  'utf8',
)
const kioskUi = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/FaceKioskScreen.kt', import.meta.url),
  'utf8',
)
const createRoute = readFileSync(
  new URL('../src/routes/device-activation-routes.ts', import.meta.url),
  'utf8',
)
const setupRoute = readFileSync(
  new URL('../src/routes/device-setup-routes.ts', import.meta.url),
  'utf8',
)
const managementRoute = readFileSync(
  new URL('../src/routes/device-management-routes.ts', import.meta.url),
  'utf8',
)
const unlockRoute = readFileSync(
  new URL('../src/routes/device-unlock-routes.ts', import.meta.url),
  'utf8',
)

test('metadados Android usados em headers HTTP são sanitizados antes de chegar ao OkHttp', () => {
  assert.match(androidApi, /internal fun safeHttpHeaderValue/)
  assert.doesNotMatch(androidApi, /append\(" · "\)/)
  assert.match(androidApi, /append\(" - "\)/)
  assert.match(androidApi, /header\("X-Android-Version", androidVersion\)/)
})

test('payload legado de dispositivos é normalizado antes de qualquer copy do estado de UI', () => {
  assert.match(adminApi, /data class AdminDeviceWire/)
  assert.match(adminApi, /val statusAtivacao: String\? = null/)
  assert.match(adminApi, /else -> "AGUARDANDO_ATIVACAO"/)
  assert.match(adminApi, /\.map\(AdminDeviceWire::toDomain\)/)
  assert.match(adminViewModel, /item\.copy\(pinConfigurado = true\)/)
})

test('a lista administrativa de dispositivos sempre busca o estado atual do servidor', () => {
  assert.match(adminApi, /suspend fun devices\(\): List<AdminDevice> = api\.devices\(\)\.dispositivos/)
  assert.doesNotMatch(adminApi, /suspend fun devices\(\): List<AdminDevice> = devicesCache \?:/)
  assert.match(adminViewModel, /delay\(12_000L\)/)
  assert.match(adminViewModel, /ensurePendingDeviceRefresh\(\)/)
})

test('ativação só marca o aparelho como configurado depois de confirmar leitura da credencial segura', () => {
  const readback = 'tokenStore.save(deviceToken) && tokenStore.read() == deviceToken'
  const configured = 'deviceConfigured = true'
  assert.ok(pontoViewModel.includes(readback))
  assert.ok(pontoViewModel.indexOf(configured) > pontoViewModel.indexOf(readback))
  assert.match(pontoViewModel, /tokenStore\.clear\(\)/)
})

test('cadastro de dispositivo aceita PIN opcional de ponta a ponta', () => {
  assert.ok(createRoute.includes('pin: z.string().trim().regex(/^\\d{4,12}$/).optional(),'))
  assert.match(adminApi, /data class CreateDeviceRequest\(val nome: String, val pin: String\?\)/)
  assert.match(adminViewModel, /fun criarDispositivo\(nome: String, pin: String\?\)/)
  assert.match(adminDevicesUi, /Configurar PIN agora/)
  assert.match(adminDevicesUi, /pin\.takeIf \{ configurePin \}/)
})

test('dispositivo sem PIN não possui fallback global e segue para login autenticado', () => {
  assert.match(unlockRoute, /DEVICE_PIN_NOT_CONFIGURED/)
  assert.doesNotMatch(unlockRoute, /LEGACY_DEFAULT_PIN_SHA256/)
  assert.doesNotMatch(unlockRoute, /51b6d230c2e8d8a991c525dcd98cc5c2567eb5720336ea62a6e1097ad04fbc3f/)
  assert.match(kioskUi, /PontoCafeRepository\.isDevicePinNotConfigured\(error\)/)
  assert.match(kioskUi, /Text\("Entrar com conta"\)/)
  assert.match(kioskUi, /onLoginModeClick\(\)/)
})

test('metadata da ativação é persistida e usada como fallback no cartão administrativo', () => {
  assert.match(setupRoute, /X-App-Version/)
  assert.match(setupRoute, /X-Device-Model/)
  assert.match(setupRoute, /X-Android-Version/)
  assert.match(managementRoute, /activationDetalhes/)
  assert.match(
    managementRoute,
    /metadataString\(heartbeatDetails, healthDetails, activationDetails, 'appVersion'\)/,
  )
  assert.match(
    managementRoute,
    /metadataString\(heartbeatDetails, healthDetails, activationDetails, 'deviceModel'\)/,
  )
  assert.match(
    managementRoute,
    /metadataString\(heartbeatDetails, healthDetails, activationDetails, 'androidVersion'\)/,
  )
})
