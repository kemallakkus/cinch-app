package com.cinch.shared.prefs

/**
 * Küçük kullanıcı tercihleri (ör. "bildirim istemi ertelendi"). Veritabanına girmeyecek kadar küçük ve
 * şema değişikliği gerektirmemesi gereken değerler için. Android'de SharedPreferences, iOS'ta NSUserDefaults.
 */
interface KeyValueStore {
    fun getLong(key: String): Long?
    fun putLong(key: String, value: Long)
    fun getBoolean(key: String): Boolean
    fun putBoolean(key: String, value: Boolean)
}

/** Testler ve JVM hedefi için. */
class InMemoryKeyValueStore : KeyValueStore {
    private val values = mutableMapOf<String, Any>()
    override fun getLong(key: String): Long? = values[key] as? Long
    override fun putLong(key: String, value: Long) {
        values[key] = value
    }
    override fun getBoolean(key: String): Boolean = values[key] as? Boolean ?: false
    override fun putBoolean(key: String, value: Boolean) {
        values[key] = value
    }
}
