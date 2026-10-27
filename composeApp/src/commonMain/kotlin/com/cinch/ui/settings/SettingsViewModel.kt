package com.cinch.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinch.notifications.NotificationAccess
import com.cinch.notifications.NotificationAccessChecker
import com.cinch.shared.budget.BudgetConfig
import com.cinch.shared.domain.repository.BudgetRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class SettingsState(
    val budget: BudgetConfig? = null,
    val notifications: NotificationAccess? = null,
)

sealed interface SettingsEvent {
    /** Ekran öne geldi: kullanıcı telefon ayarlarından bildirimi açıp dönmüş olabilir. */
    data object Refresh : SettingsEvent
    data object NotificationsClicked : SettingsEvent
}

sealed interface SettingsEffect {
    data object RequestPermission : SettingsEffect
    data object OpenSystemSettings : SettingsEffect
}

class SettingsViewModel(
    budgets: BudgetRepository,
    private val access: NotificationAccessChecker,
) : ViewModel() {

    private val notifications = MutableStateFlow<NotificationAccess?>(null)

    val state: StateFlow<SettingsState> = combine(budgets.observe(), notifications) { budget, access ->
        SettingsState(budget = budget, notifications = access)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    private val _effect = Channel<SettingsEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    init {
        refresh()
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            SettingsEvent.Refresh -> refresh()
            SettingsEvent.NotificationsClicked -> when (notifications.value) {
                NotificationAccess.ASKABLE -> {
                    access.markRequested()
                    _effect.trySend(SettingsEffect.RequestPermission)
                }
                // Açıkken de telefon ayarlarına gider: orada kapatılabilir.
                NotificationAccess.GRANTED, NotificationAccess.DENIED -> _effect.trySend(SettingsEffect.OpenSystemSettings)
                null -> Unit
            }
        }
    }

    private fun refresh() {
        viewModelScope.launch { notifications.value = access.current() }
    }
}
