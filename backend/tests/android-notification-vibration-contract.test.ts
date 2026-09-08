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
const supervisorViewModel = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/SupervisorViewModel.kt', import.meta.url),
  'utf8',
)
const haptics = readFileSync(
  new URL('../../app/src/main/java/com/pontocafe/app/haptics/PontoHaptics.kt', import.meta.url),
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
  assert.match(notifier, /val manager = NotificationManagerCompat\.from\(appContext\)/)
  assert.match(notifier, /manager\.notify\(individualId, notification\)/)
  assert.match(notifier, /manager\.notify\(GROUP_SUMMARY_ID, summary\)/)
  assert.match(notifier, /catch \(error: SecurityException\)/)
})

test('cada evento en vivo publica una sola notificación y no vibra por fuera del canal', () => {
  // Quem dispara a notificação é o monitor de fundo do ViewModel, e não a
  // tela: o alerta precisa sair mesmo com o Supervisor noutra aba.
  assert.match(supervisorViewModel, /SupervisorAlertNotifier\.notify\(/)
  for (const source of [liveAlerts, supervisorViewModel]) {
    assert.doesNotMatch(source, /RingtoneManager/)
    assert.doesNotMatch(source, /VibrationEffect/)
    assert.doesNotMatch(source, /VibratorManager/)
  }
  // Um id derivado do evento evita que dois alertas se sobrescrevam; o id do
  // resumo é reservado e nunca colide (ver stableNotificationId).
  assert.match(notifier, /private const val GROUP_SUMMARY_ID = 4_201/)
  assert.match(notifier, /stableNotificationId\(/)
})

test('o háptico vive num só lugar; nenhuma tela chama a API do Android direto', () => {
  // O contrato deixou de ser um wrapper de compatibilidade por tela e passou a
  // ser a centralização: PontoHaptics é o único arquivo autorizado a tocar
  // performHapticFeedback/VibrationEffect. Espalhar isso pelas telas foi o que
  // produziu, no passado, vibração inconsistente entre Android 11 e 14.
  assert.match(haptics, /object PontoHaptics/)
  assert.match(haptics, /performHapticFeedback\(HapticFeedbackConstants\.VIRTUAL_KEY\)/)
  assert.match(haptics, /performHapticFeedback\(HapticFeedbackConstants\.REJECT\)/)
  assert.match(haptics, /Build\.VERSION\.SDK_INT >= Build\.VERSION_CODES\.S/)
  for (const source of [pointFlow, liveAlerts, operation]) {
    assert.doesNotMatch(source, /performHapticFeedback/)
    assert.doesNotMatch(source, /VibrationEffect/)
  }
})
