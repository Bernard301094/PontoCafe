package com.pontocafe.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pontocafe.app.data.KioskModeStore

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val settings = KioskModeStore(context.applicationContext).read()
        if (!settings.enabled || !settings.autoStartAfterBoot) return

        val launch = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_BOOT_KIOSK, true)
        }
        runCatching { context.startActivity(launch) }
    }

    companion object {
        const val EXTRA_BOOT_KIOSK = "pontocafe_boot_kiosk"
    }
}
