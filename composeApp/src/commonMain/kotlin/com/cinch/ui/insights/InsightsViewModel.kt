package com.cinch.ui.insights

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinch.navigation.InsightsRoute
import com.cinch.shared.budget.ObserveSpendingBreakdown
import com.cinch.shared.budget.SpendingBreakdown
import com.cinch.shared.time.LocalClock
import com.cinch.ui.common.monthFromRoute
import com.cinch.ui.common.monthStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** @param month gösterilen ayın ilk günü. */
@Immutable
data class InsightsState(
    val isLoading: Boolean = true,
    val today: LocalDate,
    val month: LocalDate,
    val breakdown: SpendingBreakdown? = null,
) {
    val canGoNext: Boolean get() = month < today.monthStart()
}

sealed interface InsightsEvent {
    data object Resumed : InsightsEvent
    data object PreviousMonth : InsightsEvent
    data object NextMonth : InsightsEvent
}

/** "Nereye gitti?": seçili ayın kategori dağılımı, geçen ayla kıyas ve en çok para giden kelimeler. */
@OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest
class InsightsViewModel(
    route: InsightsRoute,
    observeSpendingBreakdown: ObserveSpendingBreakdown,
    private val clock: LocalClock,
) : ViewModel() {

    private val today = MutableStateFlow(clock.today())
    private val month = MutableStateFlow(monthFromRoute(route.year, route.month, today.value))

    val state: StateFlow<InsightsState> = combine(today, month, ::Pair)
        .flatMapLatest { (day, m) ->
            observeSpendingBreakdown(m, day).map { InsightsState(isLoading = false, today = day, month = m, breakdown = it) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsState(today = today.value, month = month.value))

    fun onEvent(event: InsightsEvent) {
        when (event) {
            InsightsEvent.Resumed -> today.value = clock.today()
            InsightsEvent.PreviousMonth -> month.update { it.minus(DatePeriod(months = 1)) }
            InsightsEvent.NextMonth -> month.update { m ->
                val next = m.plus(DatePeriod(months = 1))
                if (next <= today.value.monthStart()) next else m
            }
        }
    }
}
