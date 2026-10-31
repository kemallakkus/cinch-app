package com.cinch.shared.coach

import com.cinch.shared.prefs.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalTime

/**
 * Kullanıcının açıp kapattığı bildirim grupları. Bildirim türleri teknik ayrıntı; kullanıcı "ödeme günleri"ni
 * kapatır, içindeki dört tür birlikte kapanır.
 */
enum class NotificationGroup(val types: Set<NotificationType>) {
    DAILY_SHARE(setOf(NotificationType.MIDDAY_REMAINING)),
    LOG_REMINDER(setOf(NotificationType.FORGOT_LOG)),
    LIMIT_WARNINGS(setOf(NotificationType.APPROACHING_LIMIT, NotificationType.CATEGORY_SPIKE)),
    WEEKLY(setOf(NotificationType.WEEKLY_CLOSING, NotificationType.WEEK_LEFTOVER)),
    HABITS(setOf(NotificationType.STREAK, NotificationType.HABIT_ALERT)),
    PAYMENTS(setOf(NotificationType.FIXED_DUE, NotificationType.FIXED_UNPAID, NotificationType.CARD_DUE, NotificationType.STATEMENT_DUE)),
    SALARY(setOf(NotificationType.SALARY)),
    BACKUP(setOf(NotificationType.BACKUP_REMINDER)),
    ;

    companion object {
        fun of(type: NotificationType): NotificationGroup = entries.first { type in it.types }
    }
}

/**
 * @param quietHours bu saatlere düşen bildirim sessiz saat bitişine kayar.
 * @param middayTime "Günün payı" bildiriminin saati.
 * @param maxPerDay günde en fazla; önceliği yüksek olanlar kalır.
 * @param disabled kapatılan gruplar; bunlar günlük sayıya da girmez.
 */
data class NotificationPreferences(
    val quietHours: QuietHours = CoachConfig().quietHours,
    val middayTime: LocalTime = CoachConfig().middayTime,
    val maxPerDay: Int = CoachConfig().maxPerDay,
    val disabled: Set<NotificationGroup> = emptySet(),
) {
    fun allows(type: NotificationType): Boolean = NotificationGroup.of(type) !in disabled

    fun applyTo(config: CoachConfig): CoachConfig =
        config.copy(quietHours = quietHours, middayTime = middayTime, maxPerDay = maxPerDay)

    companion object {
        val MAX_PER_DAY_RANGE = 1..4
    }
}

/** Tercihleri küçük tercih deposunda tutar; değişince koç planı yeniden kurulsun diye akış olarak da verir. */
class NotificationPreferencesStore(private val prefs: KeyValueStore) {

    private val state = MutableStateFlow(load())
    val preferences: StateFlow<NotificationPreferences> = state.asStateFlow()

    val current: NotificationPreferences get() = state.value

    fun update(transform: (NotificationPreferences) -> NotificationPreferences) {
        val next = transform(state.value).let {
            it.copy(maxPerDay = it.maxPerDay.coerceIn(NotificationPreferences.MAX_PER_DAY_RANGE))
        }
        save(next)
        state.value = next
    }

    private fun load(): NotificationPreferences {
        val defaults = NotificationPreferences()
        return NotificationPreferences(
            quietHours = QuietHours(
                start = prefs.getLong(QUIET_START)?.toTime() ?: defaults.quietHours.start,
                end = prefs.getLong(QUIET_END)?.toTime() ?: defaults.quietHours.end,
            ),
            middayTime = prefs.getLong(MIDDAY)?.toTime() ?: defaults.middayTime,
            maxPerDay = prefs.getLong(MAX_PER_DAY)?.toInt()?.coerceIn(NotificationPreferences.MAX_PER_DAY_RANGE) ?: defaults.maxPerDay,
            disabled = NotificationGroup.entries.filter { prefs.getBoolean(offKey(it)) }.toSet(),
        )
    }

    private fun save(p: NotificationPreferences) {
        prefs.putLong(QUIET_START, p.quietHours.start.toMinutes())
        prefs.putLong(QUIET_END, p.quietHours.end.toMinutes())
        prefs.putLong(MIDDAY, p.middayTime.toMinutes())
        prefs.putLong(MAX_PER_DAY, p.maxPerDay.toLong())
        NotificationGroup.entries.forEach { prefs.putBoolean(offKey(it), it in p.disabled) }
    }

    private fun LocalTime.toMinutes(): Long = (hour * 60 + minute).toLong()

    private fun Long.toTime(): LocalTime? = toInt().takeIf { it in 0 until 24 * 60 }?.let { LocalTime(it / 60, it % 60) }

    private fun offKey(group: NotificationGroup) = "notif_off_${group.name}"

    private companion object {
        const val QUIET_START = "notif_quiet_start"
        const val QUIET_END = "notif_quiet_end"
        const val MIDDAY = "notif_midday"
        const val MAX_PER_DAY = "notif_max_per_day"
    }
}
