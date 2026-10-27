package com.cinch.shared.prefs

import platform.Foundation.NSUserDefaults

class UserDefaultsStore : KeyValueStore {

    private val defaults = NSUserDefaults.standardUserDefaults

    override fun getLong(key: String): Long? =
        if (defaults.objectForKey(key) != null) defaults.integerForKey(key) else null

    override fun putLong(key: String, value: Long) {
        defaults.setInteger(value, forKey = key)
    }

    override fun getBoolean(key: String): Boolean = defaults.boolForKey(key)

    override fun putBoolean(key: String, value: Boolean) {
        defaults.setBool(value, forKey = key)
    }
}
