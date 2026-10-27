package com.cinch.shared.prefs

import android.content.Context

class SharedPreferencesStore(context: Context) : KeyValueStore {

    private val prefs = context.getSharedPreferences("cinch", Context.MODE_PRIVATE)

    override fun getLong(key: String): Long? = if (prefs.contains(key)) prefs.getLong(key, 0) else null

    override fun putLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }

    override fun getBoolean(key: String): Boolean = prefs.getBoolean(key, false)

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }
}
