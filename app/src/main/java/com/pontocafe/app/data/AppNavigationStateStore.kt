package com.pontocafe.app.data

import android.content.Context

/**
 * Persiste somente estado de navegação e bloqueio da interface.
 * Não armazena senha, PIN, biometria nem bearer token.
 */
class AppNavigationStateStore(context: Context) {
    private val prefs = context.getSharedPreferences("pontocafe_navigation_state", Context.MODE_PRIVATE)

    fun readArea(): String? = prefs.getString(KEY_AREA, null)

    fun saveArea(area: String?) {
        prefs.edit().apply {
            if (area == null) remove(KEY_AREA) else putString(KEY_AREA, area)
        }.apply()
    }

    fun isRestrictedLocked(): Boolean = prefs.getBoolean(KEY_RESTRICTED_LOCKED, false)
    fun setRestrictedLocked(locked: Boolean) {
        prefs.edit().putBoolean(KEY_RESTRICTED_LOCKED, locked).apply()
    }

    fun readAdminDestination(): String? = prefs.getString(KEY_ADMIN_DESTINATION, null)
    fun readAdminUserId(): String? = prefs.getString(KEY_ADMIN_USER_ID, null)
    fun readAdminCollaboratorId(): String? = prefs.getString(KEY_ADMIN_COLLABORATOR_ID, null)
    fun isAdminDevicesOpen(): Boolean = prefs.getBoolean(KEY_ADMIN_DEVICES_OPEN, false)

    fun saveAdminState(
        destination: String,
        userId: String?,
        collaboratorId: String?,
    ) {
        prefs.edit().apply {
            putString(KEY_ADMIN_DESTINATION, destination)
            if (userId == null) remove(KEY_ADMIN_USER_ID) else putString(KEY_ADMIN_USER_ID, userId)
            if (collaboratorId == null) remove(KEY_ADMIN_COLLABORATOR_ID) else putString(KEY_ADMIN_COLLABORATOR_ID, collaboratorId)
        }.apply()
    }

    fun setAdminDevicesOpen(open: Boolean) {
        prefs.edit().putBoolean(KEY_ADMIN_DEVICES_OPEN, open).apply()
    }

    fun clearAdminNavigation() {
        prefs.edit()
            .remove(KEY_ADMIN_DESTINATION)
            .remove(KEY_ADMIN_USER_ID)
            .remove(KEY_ADMIN_COLLABORATOR_ID)
            .remove(KEY_ADMIN_DEVICES_OPEN)
            .apply()
    }

    companion object {
        private const val KEY_AREA = "last_restricted_area"
        private const val KEY_RESTRICTED_LOCKED = "restricted_area_locked"
        private const val KEY_ADMIN_DESTINATION = "admin_destination"
        private const val KEY_ADMIN_USER_ID = "admin_user_id"
        private const val KEY_ADMIN_COLLABORATOR_ID = "admin_collaborator_id"
        private const val KEY_ADMIN_DEVICES_OPEN = "admin_devices_open"
    }
}
