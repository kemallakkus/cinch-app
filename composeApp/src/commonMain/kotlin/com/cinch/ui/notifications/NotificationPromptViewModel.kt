package com.cinch.ui.notifications

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinch.notifications.NotificationAccess
import com.cinch.notifications.NotificationAccessChecker
import com.cinch.shared.prefs.KeyValueStore
import com.cinch.shared.time.LocalClock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus

enum class NotificationPrompt {
    /** Ön açıklama + sistem izin penceresi. */
    ASK,

    /** İzin reddedilmiş; sistem ayarlarına yönlendirilir. */
    OPEN_SETTINGS,
}

/** @param prompt gösterilecek kart; null ise kart yok (izin var ya da ertelendi). */
@Immutable
data class NotificationPromptState(val prompt: NotificationPrompt? = null)

sealed interface NotificationPromptEvent {
    /** Ekran öne geldi: kullanıcı ayarlardan dönmüş olabilir. */
    data object Refresh : NotificationPromptEvent
    data object Accept : NotificationPromptEvent
    data object Snooze : NotificationPromptEvent
    data object PermissionResult : NotificationPromptEvent
}

sealed interface NotificationPromptEffect {
    data object RequestPermission : NotificationPromptEffect
    data object OpenSettings : NotificationPromptEffect
}

/**
 * Ana ekrandaki bildirim izni kartı. İzin, sistem penceresinden önce ne işe yarayacağı anlatılarak
 * istenir; "Şimdi değil" kartı [SNOOZE_DAYS] gün gizler.
 */
class NotificationPromptViewModel(
    private val access: NotificationAccessChecker,
    private val prefs: KeyValueStore,
    private val clock: LocalClock,
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationPromptState())
    val state: StateFlow<NotificationPromptState> = _state.asStateFlow()

    private val _effect = Channel<NotificationPromptEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    init {
        refresh()
    }

    fun onEvent(event: NotificationPromptEvent) {
        when (event) {
            NotificationPromptEvent.Refresh, NotificationPromptEvent.PermissionResult -> refresh()
            NotificationPromptEvent.Accept -> accept()
            NotificationPromptEvent.Snooze -> snooze()
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            val prompt = when (access.current()) {
                NotificationAccess.GRANTED -> null
                NotificationAccess.ASKABLE -> NotificationPrompt.ASK
                NotificationAccess.DENIED -> NotificationPrompt.OPEN_SETTINGS
            }
            _state.update { it.copy(prompt = prompt.takeUnless { isSnoozed() }) }
        }
    }

    private fun accept() {
        when (_state.value.prompt) {
            NotificationPrompt.ASK -> {
                access.markRequested()
                _effect.trySend(NotificationPromptEffect.RequestPermission)
            }
            NotificationPrompt.OPEN_SETTINGS -> _effect.trySend(NotificationPromptEffect.OpenSettings)
            null -> Unit
        }
    }

    private fun snooze() {
        val until = clock.today().plus(DatePeriod(days = SNOOZE_DAYS))
        prefs.putLong(KEY_SNOOZED_UNTIL, until.toEpochDays())
        _state.update { it.copy(prompt = null) }
    }

    private fun isSnoozed(): Boolean =
        prefs.getLong(KEY_SNOOZED_UNTIL)?.let { clock.today().toEpochDays() < it } ?: false

    companion object {
        const val SNOOZE_DAYS = 7
        const val KEY_SNOOZED_UNTIL = "notification_prompt_snoozed_until"
    }
}
