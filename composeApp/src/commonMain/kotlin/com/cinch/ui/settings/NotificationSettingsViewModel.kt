package com.cinch.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinch.shared.coach.NotificationGroup
import com.cinch.shared.coach.NotificationPreferences
import com.cinch.shared.coach.NotificationPreferencesStore
import com.cinch.shared.coach.QuietHours
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalTime

/** Saat seçicinin açık olduğu alan. */
enum class TimeField { QUIET_START, QUIET_END, MIDDAY }

@Immutable
data class NotificationSettingsState(
    val preferences: NotificationPreferences = NotificationPreferences(),
    val editing: TimeField? = null,
) {
    /** Başlangıç ve bitiş aynıysa sessiz saat yok. */
    val quietEnabled: Boolean get() = preferences.quietHours.start != preferences.quietHours.end

    fun timeOf(field: TimeField): LocalTime = when (field) {
        TimeField.QUIET_START -> preferences.quietHours.start
        TimeField.QUIET_END -> preferences.quietHours.end
        TimeField.MIDDAY -> preferences.middayTime
    }
}

sealed interface NotificationSettingsEvent {
    data class QuietToggled(val enabled: Boolean) : NotificationSettingsEvent
    data class EditTime(val field: TimeField) : NotificationSettingsEvent
    data class TimePicked(val time: LocalTime) : NotificationSettingsEvent
    data object TimeDismissed : NotificationSettingsEvent
    data class MaxPerDayPicked(val count: Int) : NotificationSettingsEvent
    data class GroupToggled(val group: NotificationGroup, val enabled: Boolean) : NotificationSettingsEvent
}

/**
 * Bildirim tercihleri: sessiz saatler, günün payı saati, günde en fazla kaç bildirim, hangi gruplar. Her değişiklik
 * hemen kaydedilir; koç planı tercih akışını izlediği için bekleyen bildirimler kendiliğinden yenilenir.
 */
class NotificationSettingsViewModel(private val store: NotificationPreferencesStore) : ViewModel() {

    private val editing = MutableStateFlow<TimeField?>(null)

    val state: StateFlow<NotificationSettingsState> = combine(store.preferences, editing) { prefs, field ->
        NotificationSettingsState(prefs, field)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationSettingsState(store.current))

    fun onEvent(event: NotificationSettingsEvent) {
        when (event) {
            is NotificationSettingsEvent.QuietToggled -> store.update {
                it.copy(quietHours = if (event.enabled) DEFAULT_QUIET else QuietHours(MIDNIGHT, MIDNIGHT))
            }
            is NotificationSettingsEvent.EditTime -> editing.value = event.field
            is NotificationSettingsEvent.TimePicked -> {
                val field = editing.value ?: return
                editing.value = null
                store.update { prefs ->
                    when (field) {
                        TimeField.QUIET_START -> prefs.copy(quietHours = prefs.quietHours.copy(start = event.time))
                        TimeField.QUIET_END -> prefs.copy(quietHours = prefs.quietHours.copy(end = event.time))
                        TimeField.MIDDAY -> prefs.copy(middayTime = event.time)
                    }
                }
            }
            NotificationSettingsEvent.TimeDismissed -> editing.update { null }
            is NotificationSettingsEvent.MaxPerDayPicked -> store.update { it.copy(maxPerDay = event.count) }
            is NotificationSettingsEvent.GroupToggled -> store.update {
                it.copy(disabled = if (event.enabled) it.disabled - event.group else it.disabled + event.group)
            }
        }
    }

    private companion object {
        val DEFAULT_QUIET = NotificationPreferences().quietHours
        val MIDNIGHT = LocalTime(0, 0)
    }
}
