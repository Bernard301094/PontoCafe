import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const manifest = readFileSync(
  new URL('../../app/src/main/AndroidManifest.xml', import.meta.url),
  'utf8',
)
const notifier = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/notifications/SupervisorAlertNotifier.kt', import.meta.url),
  'utf8',
)
const operation = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/SupervisorOperationScreen.kt', import.meta.url),
  'utf8',
)
const liveAlerts = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/SupervisorLiveAlerts.kt', import.meta.url),
  'utf8',
)
const biometricFeedback = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/BiometricRegistrationSuccessFeedback.kt', import.meta.url),
  'utf8',
)
const pointReceipt = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/PointReceiptScreen.kt', import.meta.url),
  'utf8',
)
const pointFlow = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/ui/PontoFlowHost.kt', import.meta.url),
  'utf8',
)

test('manifiesto declara permisos de notificación y vibración', () => {
  assert.match(manifest, /android\.permission\.POST_NOTIFICATIONS/)
  assert.match(manifest, /android\.permission\.VIBRATE/)
})

test('Android 13 solicita permiso en contexto y maneja denegación y ajustes', () => {
  assert.match(operation, /ActivityResultContracts\.RequestPermission\(\)/)
  assert.match(operation, /Manifest\.permission\.POST_NOTIFICATIONS/)
  assert.match(operation, /notificationPermissionDenied = !granted/)
  assert.match(operation, /Lifecycle\.Event\.ON_RESUME/)
  assert.match(operation, /SupervisorAlertNotifier\.openSettings/)
  assert.match(notifier, /ContextCompat\.checkSelfPermission/)
  assert.match(notifier, /NotificationManagerCompat\.from\(appContext\)\.areNotificationsEnabled\(\)/)
})

test('canal de alta importancia controla sonido y vibración sin sobreescribir ajustes persistidos', () => {
  assert.match(notifier, /CHANNEL_ID = "supervisor_live_alerts_v1"/)
  assert.match(notifier, /NotificationManager\.IMPORTANCE_HIGH/)
  assert.match(notifier, /enableVibration\(true\)/)
  assert.match(notifier, /vibrationPattern = channelVibrationPattern/)
  assert.match(notifier, /AudioAttributes\.USAGE_NOTIFICATION_EVENT/)
  assert.match(notifier, /if \(manager\.getNotificationChannel\(CHANNEL_ID\) != null\) return/)
  assert.match(notifier, /channel\.importance == NotificationManager\.IMPORTANCE_NONE/)
  assert.match(notifier, /channel\.shouldVibrate\(\)/)
})

test('alerta llega a NotificationManager con icono, canal y PendingIntent seguro', () => {
  assert.match(notifier, /NotificationCompat\.Builder\(appContext, CHANNEL_ID\)/)
  assert.match(notifier, /setSmallIcon\(R\.drawable\.ic_launcher_foreground\)/)
  assert.match(notifier, /PendingIntent\.FLAG_UPDATE_CURRENT or PendingIntent\.FLAG_IMMUTABLE/)
  assert.match(notifier, /setCategory\(NotificationCompat\.CATEGORY_EVENT\)/)
  assert.match(notifier, /setVisibility\(NotificationCompat\.VISIBILITY_PRIVATE\)/)
  assert.match(notifier, /setAutoCancel\(true\)/)
  assert.match(notifier, /NotificationManagerCompat\.from\(appContext\)\.notify\(NOTIFICATION_ID, notification\)/)
  assert.match(notifier, /catch \(error: SecurityException\)/)
})

test('cada evento en vivo publica una sola notificación y no vibra por fuera del canal', () => {
  assert.match(liveAlerts, /SupervisorAlertNotifier\.notify\(/)
  assert.doesNotMatch(liveAlerts, /RingtoneManager/)
  assert.doesNotMatch(liveAlerts, /VibrationEffect/)
  assert.doesNotMatch(liveAlerts, /VibratorManager/)
  assert.match(notifier, /private const val NOTIFICATION_ID = 4_201/)
})

test('hápticos de confirmación y rechazo usan compatibilidad anterior a API 30', () => {
  for (const source of [biometricFeedback, pointReceipt, pointFlow]) {
    assert.match(source, /HapticFeedbackConstantsCompat/)
    assert.doesNotMatch(source, /import android\.view\.HapticFeedbackConstants/)
  }
  assert.match(biometricFeedback, /HapticFeedbackConstantsCompat\.CONFIRM/)
  assert.match(pointReceipt, /HapticFeedbackConstantsCompat\.REJECT/)
  assert.match(pointFlow, /HapticFeedbackConstantsCompat\.REJECT/)
})
