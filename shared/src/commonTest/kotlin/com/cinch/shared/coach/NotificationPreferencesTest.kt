package com.cinch.shared.coach

import com.cinch.shared.prefs.InMemoryKeyValueStore
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationPreferencesTest {

    @Test
    fun everyTypeBelongsToExactlyOneGroup() {
        NotificationType.entries.forEach { type ->
            assertEquals(1, NotificationGroup.entries.count { type in it.types }, type.name)
        }
    }

    @Test
    fun defaultsMatchTheCoachDefaults() {
        val prefs = NotificationPreferencesStore(InMemoryKeyValueStore()).current
        val config = CoachConfig()
        assertEquals(config.quietHours, prefs.quietHours)
        assertEquals(config.middayTime, prefs.middayTime)
        assertEquals(config.maxPerDay, prefs.maxPerDay)
        assertTrue(NotificationType.entries.all { prefs.allows(it) })
    }

    @Test
    fun changesAreSaved_andReadBack() {
        val kv = InMemoryKeyValueStore()
        val store = NotificationPreferencesStore(kv)
        store.update {
            it.copy(
                quietHours = QuietHours(LocalTime(23, 30), LocalTime(7, 15)),
                middayTime = LocalTime(11, 45),
                maxPerDay = 9, // aralık dışı: 4'e iner
                disabled = setOf(NotificationGroup.PAYMENTS, NotificationGroup.BACKUP),
            )
        }

        val reloaded = NotificationPreferencesStore(kv).current
        assertEquals(QuietHours(LocalTime(23, 30), LocalTime(7, 15)), reloaded.quietHours)
        assertEquals(LocalTime(11, 45), reloaded.middayTime)
        assertEquals(4, reloaded.maxPerDay)
        assertFalse(reloaded.allows(NotificationType.CARD_DUE))
        assertFalse(reloaded.allows(NotificationType.FIXED_UNPAID))
        assertTrue(reloaded.allows(NotificationType.MIDDAY_REMAINING))
        assertEquals(reloaded, store.preferences.value)
    }
}
