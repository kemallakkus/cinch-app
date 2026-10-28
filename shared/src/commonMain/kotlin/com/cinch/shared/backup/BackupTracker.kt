package com.cinch.shared.backup

import com.cinch.shared.prefs.KeyValueStore
import kotlinx.datetime.LocalDate

/** Son yedeğin alındığı gün: Ayarlar'da gösterilir, uzun süre alınmadıysa koç hatırlatır. */
class BackupTracker(private val prefs: KeyValueStore) {

    fun lastBackup(): LocalDate? = prefs.getLong(KEY)?.let { LocalDate.fromEpochDays(it) }

    fun markBackedUp(date: LocalDate) {
        prefs.putLong(KEY, date.toEpochDays())
    }

    private companion object {
        const val KEY = "last_backup_epoch_day"
    }
}
