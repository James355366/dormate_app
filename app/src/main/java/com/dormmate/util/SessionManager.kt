package com.dormmate.util

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun saveUserSession(role: String?, roomNumber: String?, name: String?) {
        prefs.edit()
            .putString(KEY_USER_ROLE, role)
            .putString(KEY_ROOM_NUMBER, roomNumber)
            .putString(KEY_USER_NAME, name)
            .apply()
    }

    val userRole: String?
        get() = prefs.getString(KEY_USER_ROLE, null)

    val roomNumber: String?
        get() = prefs.getString(KEY_ROOM_NUMBER, null)

    val userName: String?
        get() = prefs.getString(KEY_USER_NAME, null)

    fun clearSession() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREF_NAME = "dormmate_session"
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_ROOM_NUMBER = "room_number"
        private const val KEY_USER_NAME = "user_name"
    }
}
