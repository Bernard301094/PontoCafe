package com.pontocafe.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class OperationalAlertHistoryItem(
    val id: Long,
    val type: String,
    val title: String,
    val message: String,
    val createdAtMillis: Long,
    val read: Boolean,
)

/**
 * Histórico local dos avisos operacionais mostrados ao Supervisor.
 *
 * Ele serve somente à UX do aparelho: não altera auditoria, banco, pausa,
 * biometria ou regras de negócio. O conteúdo é limitado e pode ser limpo a
 * qualquer momento.
 */
class OperationalAlertHistoryStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    @Synchronized
    fun record(
        id: Long,
        type: String,
        title: String,
        message: String,
        createdAtMillis: Long = System.currentTimeMillis(),
    ) {
        val current = snapshot().toMutableList()
        val duplicate = current.any {
            it.type == type && it.title == title && it.message == message &&
                createdAtMillis - it.createdAtMillis in 0..DEDUP_WINDOW_MILLIS
        }
        if (duplicate) return

        current.add(
            0,
            OperationalAlertHistoryItem(
                id = id,
                type = type,
                title = title,
                message = message,
                createdAtMillis = createdAtMillis,
                read = false,
            ),
        )
        save(current.take(MAX_ITEMS))
    }

    @Synchronized
    fun snapshot(): List<OperationalAlertHistoryItem> {
        val raw = preferences.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        OperationalAlertHistoryItem(
                            id = item.optLong("id"),
                            type = item.optString("type"),
                            title = item.optString("title"),
                            message = item.optString("message"),
                            createdAtMillis = item.optLong("createdAtMillis"),
                            read = item.optBoolean("read", false),
                        ),
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    @Synchronized
    fun markAllRead() {
        save(snapshot().map { it.copy(read = true) })
    }

    @Synchronized
    fun clear() {
        preferences.edit().remove(KEY_ITEMS).apply()
    }

    private fun save(items: List<OperationalAlertHistoryItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("type", item.type)
                    .put("title", item.title)
                    .put("message", item.message)
                    .put("createdAtMillis", item.createdAtMillis)
                    .put("read", item.read),
            )
        }
        preferences.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "pontocafe_operational_alert_history"
        private const val KEY_ITEMS = "items"
        private const val MAX_ITEMS = 40
        private const val DEDUP_WINDOW_MILLIS = 30_000L
    }
}
