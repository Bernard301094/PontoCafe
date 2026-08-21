package com.pontocafe.app.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.pontocafe.app.MainActivity
import com.pontocafe.app.R

enum class SupervisorNotificationAvailability(val canPost: Boolean) {
    ENABLED(true),
    VIBRATION_DISABLED(true),
    PERMISSION_REQUIRED(false),
    APP_DISABLED(false),
    CHANNEL_DISABLED(false),
}

/**
 * Entrega os alertas operacionais pelo sistema Android. Som e vibração ficam
 * sob controle do canal para respeitar as escolhas do usuário, DND e ajustes do
 * fabricante, inclusive em aparelhos Samsung recentes.
 */
object SupervisorAlertNotifier {
    const val CHANNEL_ID = "supervisor_live_alerts_v1"

    private const val CHANNEL_NAME = "Alertas ao vivo do Supervisor"
    private const val CHANNEL_DESCRIPTION = "Saídas, retornos e pausas acima do limite"
    private const val NOTIFICATION_ID = 4_201
    private const val CONTENT_REQUEST_CODE = 4_201
    private const val LOG_TAG = "SupervisorAlerts"
    private val channelVibrationPattern = longArrayOf(0L, 180L, 90L, 260L)

    fun ensureChannel(context: Context) {
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .build()
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = CHANNEL_DESCRIPTION
            enableVibration(true)
            vibrationPattern = channelVibrationPattern
            setSound(sound, audioAttributes)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(channel)
        Log.i(LOG_TAG, "Canal criado: id=$CHANNEL_ID importance=${channel.importance} vibration=${channel.shouldVibrate()}")
    }

    fun availability(context: Context): SupervisorNotificationAvailability {
        val appContext = context.applicationContext
        ensureChannel(appContext)

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return SupervisorNotificationAvailability.PERMISSION_REQUIRED
        }

        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) {
            return SupervisorNotificationAvailability.APP_DISABLED
        }

        val manager = appContext.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(CHANNEL_ID)
            ?: return SupervisorNotificationAvailability.CHANNEL_DISABLED
        if (channel.importance == NotificationManager.IMPORTANCE_NONE) {
            return SupervisorNotificationAvailability.CHANNEL_DISABLED
        }
        return if (channel.shouldVibrate()) {
            SupervisorNotificationAvailability.ENABLED
        } else {
            SupervisorNotificationAvailability.VIBRATION_DISABLED
        }
    }

    @SuppressLint("MissingPermission")
    fun notify(
        context: Context,
        eventType: String,
        title: String,
        message: String,
    ): Boolean {
        val appContext = context.applicationContext
        val availability = availability(appContext)
        val channel = appContext.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(CHANNEL_ID)
        Log.d(
            LOG_TAG,
            "Evento=$eventType permission=${runtimePermissionGranted(appContext)} " +
                "enabled=${NotificationManagerCompat.from(appContext).areNotificationsEnabled()} " +
                "channel=$CHANNEL_ID importance=${channel?.importance} vibration=${channel?.shouldVibrate()}",
        )
        if (!availability.canPost) {
            Log.w(LOG_TAG, "Notificação não enviada: event=$eventType availability=$availability")
            return false
        }

        val contentIntent = Intent(appContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            CONTENT_REQUEST_CODE,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .build()

        return try {
            NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
            Log.i(LOG_TAG, "notify() concluído: event=$eventType channel=$CHANNEL_ID id=$NOTIFICATION_ID")
            true
        } catch (error: SecurityException) {
            Log.e(LOG_TAG, "Permissão recusada ao notificar event=$eventType channel=$CHANNEL_ID", error)
            false
        } catch (error: RuntimeException) {
            Log.e(LOG_TAG, "Falha ao notificar event=$eventType channel=$CHANNEL_ID", error)
            false
        }
    }

    fun openSettings(context: Context, channelSpecific: Boolean) {
        val appContext = context.applicationContext
        val primary = if (channelSpecific) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, appContext.packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
            }
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, appContext.packageName)
            }
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        runCatching { appContext.startActivity(primary) }
            .onFailure { error ->
                Log.w(LOG_TAG, "Falha ao abrir ajustes de notificação; usando detalhes do app.", error)
                val fallback = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    "package:${appContext.packageName}".toUri(),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { appContext.startActivity(fallback) }
                    .onFailure { fallbackError ->
                        Log.e(LOG_TAG, "Falha ao abrir ajustes do aplicativo.", fallbackError)
                    }
            }
    }

    private fun runtimePermissionGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
