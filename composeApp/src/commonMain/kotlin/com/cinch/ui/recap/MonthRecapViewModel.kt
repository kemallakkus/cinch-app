package com.cinch.ui.recap

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinch.navigation.MonthRecapRoute
import com.cinch.shared.budget.MonthRecap
import com.cinch.shared.budget.MonthRecaps
import com.cinch.shared.budget.ObserveMonthRecap
import com.cinch.shared.time.LocalClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** @param month gösterilen ayın ilk günü. */
@Immutable
data class MonthRecapState(
    val isLoading: Boolean = true,
    val month: LocalDate,
    val recap: MonthRecap? = null,
    val canGoBack: Boolean = true,
    val canGoNext: Boolean = false,
)

sealed interface MonthRecapEvent {
    data object Previous : MonthRecapEvent
    data object Next : MonthRecapEvent
}

/** Ay özeti; ‹ › ile önceki aylar (en fazla [MONTHS_BACK] ay geri) ve içinde bulunulan ay. */
@OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest
class MonthRecapViewModel(route: MonthRecapRoute, observe: ObserveMonthRecap, clock: LocalClock) : ViewModel() {

    private val today = clock.today()
    private val thisMonth = LocalDate(today.year, today.month, 1)
    private val earliest = thisMonth.minus(DatePeriod(months = MONTHS_BACK))

    private val month = MutableStateFlow(
        if (route.year != null && route.month != null) LocalDate(route.year, route.month, 1) else MonthRecaps.previousMonth(today),
    )

    val state: StateFlow<MonthRecapState> = month.flatMapLatest { m ->
        observe(m).map { recap ->
            MonthRecapState(isLoading = false, month = m, recap = recap, canGoBack = m > earliest, canGoNext = m < thisMonth)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthRecapState(month = month.value))

    fun onEvent(event: MonthRecapEvent) {
        val current = month.value
        when (event) {
            MonthRecapEvent.Previous -> if (current > earliest) month.value = current.minus(DatePeriod(months = 1))
            MonthRecapEvent.Next -> if (current < thisMonth) month.value = current.plus(DatePeriod(months = 1))
        }
    }

    private companion object {
        const val MONTHS_BACK = 24
    }
}
