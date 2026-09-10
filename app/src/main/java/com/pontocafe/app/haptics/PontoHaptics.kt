package com.pontocafe.app.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Centralized haptic feedback for PontoCafe, replacing several previously
 * inconsistent per-call-site implementations.
 *
 * Simple UI taps ([tap]/[reject]) go through the system's
 * `performHapticFeedback`, which respects the device's "Touch feedback"
 * toggle and matches platform button-press conventions — unchanged behavior
 * from before, just centralized.
 *
 * Important, business-critical Ponto outcomes ([success]/[warning]: clock-in
 * confirmed, blocked/error, biometric enrollment success) instead use the
 * `Vibrator`/`VibratorManager` API directly. "Touch feedback" is a different
 * setting from general vibration/DND — relying on `performHapticFeedback`
 * exclusively would make these specific, important events silently no-op on
 * devices/configurations where that toggle is off but vibration is otherwise
 * fully enabled. Effects are short and non-repeating by design.
 */
object PontoHaptics {

    fun tap(view: View) {
        runCatching { view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
    }

    fun reject(view: View) {
        runCatching { view.performHapticFeedback(HapticFeedbackConstants.REJECT) }
    }

    fun success(context: Context) {
        vibrate(context, selectSuccessEffectKind(Build.VERSION.SDK_INT))
    }

    fun warning(context: Context) {
        vibrate(context, selectWarningEffectKind(Build.VERSION.SDK_INT))
    }

    private fun vibrate(context: Context, kind: PontoHapticEffectKind) {
        runCatching {
            val vibrator = vibratorFor(context.applicationContext) ?: return
            if (!vibrator.hasVibrator()) return
            vibrator.vibrate(kind.toVibrationEffect())
        }
    }

    private fun vibratorFor(context: Context): Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private fun PontoHapticEffectKind.toVibrationEffect(): VibrationEffect = when (this) {
        PontoHapticEffectKind.PREDEFINED_TICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
        PontoHapticEffectKind.PREDEFINED_DOUBLE_CLICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
        PontoHapticEffectKind.ONE_SHOT_SHORT ->
            VibrationEffect.createOneShot(ONE_SHOT_SHORT_MILLIS, VibrationEffect.DEFAULT_AMPLITUDE)
        PontoHapticEffectKind.ONE_SHOT_LONG ->
            VibrationEffect.createOneShot(ONE_SHOT_LONG_MILLIS, VibrationEffect.DEFAULT_AMPLITUDE)
    }

    private const val ONE_SHOT_SHORT_MILLIS = 20L
    private const val ONE_SHOT_LONG_MILLIS = 60L
}

internal enum class PontoHapticEffectKind {
    PREDEFINED_TICK,
    PREDEFINED_DOUBLE_CLICK,
    ONE_SHOT_SHORT,
    ONE_SHOT_LONG,
}

/**
 * `VibrationEffect.createPredefined` only exists from API 29 (Q) onward;
 * below that, a short one-shot amplitude effect is the safest equivalent
 * still available back to this app's minSdk (26).
 */
internal fun selectSuccessEffectKind(sdkInt: Int): PontoHapticEffectKind =
    if (sdkInt >= Build.VERSION_CODES.Q) PontoHapticEffectKind.PREDEFINED_TICK else PontoHapticEffectKind.ONE_SHOT_SHORT

internal fun selectWarningEffectKind(sdkInt: Int): PontoHapticEffectKind =
    if (sdkInt >= Build.VERSION_CODES.Q) PontoHapticEffectKind.PREDEFINED_DOUBLE_CLICK else PontoHapticEffectKind.ONE_SHOT_LONG
