package com.pontocafe.app.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong


data class AppHealthSnapshot(
    val lastStartMillis: Long,
    val crashCount: Int,
    val lastCrashMillis: Long,
    val lastCrashType: String?,
    val lastCrashLocation: String?,
    val stallCount: Int,
    val lastStallMillis: Long,
    val lastStallDurationMillis: Long,
)

class AppHealthStore(context: Context) {
    private val prefs = context.getSharedPreferences("pontocafe_app_health", Context.MODE_PRIVATE)

    fun markStart() {
        prefs.edit().putLong(KEY_LAST_START, System.currentTimeMillis()).apply()
    }

    fun recordCrash(error: Throwable) {
        val top = error.stackTrace.firstOrNull()
        prefs.edit()
            .putInt(KEY_CRASH_COUNT, prefs.getInt(KEY_CRASH_COUNT, 0) + 1)
            .putLong(KEY_LAST_CRASH, System.currentTimeMillis())
            .putString(KEY_LAST_CRASH_TYPE, error.javaClass.simpleName.take(80))
            .putString(KEY_LAST_CRASH_LOCATION, top?.let { "${it.className.takeLast(80)}.${it.methodName.take(60)}:${it.lineNumber}" })
            .apply()
    }

    fun recordStall(durationMillis: Long) {
        prefs.edit()
            .putInt(KEY_STALL_COUNT, prefs.getInt(KEY_STALL_COUNT, 0) + 1)
            .putLong(KEY_LAST_STALL, System.currentTimeMillis())
            .putLong(KEY_LAST_STALL_DURATION, durationMillis.coerceAtMost(120_000L))
            .apply()
    }

    fun snapshot(): AppHealthSnapshot = AppHealthSnapshot(
        lastStartMillis = prefs.getLong(KEY_LAST_START, 0L),
        crashCount = prefs.getInt(KEY_CRASH_COUNT, 0),
        lastCrashMillis = prefs.getLong(KEY_LAST_CRASH, 0L),
        lastCrashType = prefs.getString(KEY_LAST_CRASH_TYPE, null),
        lastCrashLocation = prefs.getString(KEY_LAST_CRASH_LOCATION, null),
        stallCount = prefs.getInt(KEY_STALL_COUNT, 0),
        lastStallMillis = prefs.getLong(KEY_LAST_STALL, 0L),
        lastStallDurationMillis = prefs.getLong(KEY_LAST_STALL_DURATION, 0L),
    )

    fun shouldUpload(snapshot: AppHealthSnapshot = snapshot(), nowMillis: Long = System.currentTimeMillis()): Boolean {
        val lastUpload = prefs.getLong(KEY_LAST_UPLOAD, 0L)
        if (lastUpload <= 0L) return true
        if (snapshot.lastCrashMillis > lastUpload || snapshot.lastStallMillis > lastUpload) return true
        return nowMillis - lastUpload >= TELEMETRY_INTERVAL_MS
    }

    fun markUploaded(atMillis: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_UPLOAD, atMillis).apply()
    }

    companion object {
        private const val KEY_LAST_START = "last_start"
        private const val KEY_CRASH_COUNT = "crash_count"
        private const val KEY_LAST_CRASH = "last_crash"
        private const val KEY_LAST_CRASH_TYPE = "last_crash_type"
        private const val KEY_LAST_CRASH_LOCATION = "last_crash_location"
        private const val KEY_STALL_COUNT = "stall_count"
        private const val KEY_LAST_STALL = "last_stall"
        private const val KEY_LAST_STALL_DURATION = "last_stall_duration"
        private const val KEY_LAST_UPLOAD = "last_health_upload"
        private const val TELEMETRY_INTERVAL_MS = 24L * 60L * 60L * 1000L
    }
}

class AppHealthMonitor(context: Context) {
    private val store = AppHealthStore(context.applicationContext)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val watchdog = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "PontoCafe-AppHealth").apply { isDaemon = true }
    }
    private val lastHeartbeat = AtomicLong(System.currentTimeMillis())
    private val stallRecorded = AtomicBoolean(false)
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    private val heartbeat = object : Runnable {
        override fun run() {
            lastHeartbeat.set(System.currentTimeMillis())
            stallRecorded.set(false)
            mainHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
        }
    }

    fun start() {
        store.markStart()
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { store.recordCrash(error) }
            previousHandler?.uncaughtException(thread, error)
        }
        mainHandler.post(heartbeat)
        watchdog.scheduleAtFixedRate(
            {
                val duration = System.currentTimeMillis() - lastHeartbeat.get()
                if (duration >= STALL_THRESHOLD_MS && stallRecorded.compareAndSet(false, true)) {
                    store.recordStall(duration)
                }
            },
            STALL_CHECK_MS,
            STALL_CHECK_MS,
            TimeUnit.MILLISECONDS,
        )
    }

    fun stop() {
        mainHandler.removeCallbacks(heartbeat)
        watchdog.shutdownNow()
        previousHandler?.let { Thread.setDefaultUncaughtExceptionHandler(it) }
    }

    companion object {
        private const val HEARTBEAT_INTERVAL_MS = 1_000L
        private const val STALL_CHECK_MS = 2_000L
        private const val STALL_THRESHOLD_MS = 5_000L
    }
}
