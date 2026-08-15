package com.pontocafe.app.data

import android.content.Context


data class KioskModeSettings(
    val enabled: Boolean = false,
    val keepScreenOn: Boolean = true,
    val lockTask: Boolean = false,
    val autoStartAfterBoot: Boolean = false,
)

class KioskModeStore(context: Context) {
    private val prefs = context.getSharedPreferences("pontocafe_kiosk_mode", Context.MODE_PRIVATE)

    fun read(): KioskModeSettings = KioskModeSettings(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        keepScreenOn = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true),
        lockTask = prefs.getBoolean(KEY_LOCK_TASK, false),
        autoStartAfterBoot = prefs.getBoolean(KEY_AUTO_START, false),
    )

    fun save(settings: KioskModeSettings) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putBoolean(KEY_KEEP_SCREEN_ON, settings.keepScreenOn)
            .putBoolean(KEY_LOCK_TASK, settings.lockTask)
            .putBoolean(KEY_AUTO_START, settings.autoStartAfterBoot)
            .apply()
    }

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_LOCK_TASK = "lock_task"
        private const val KEY_AUTO_START = "auto_start_after_boot"
    }
}
