package com.cinch.ui.month

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinch.shared.budget.MonthSummary
import com.cinch.shared.budget.ObserveMonthSummary
import com.cinch.shared.domain.Income
import com.cinch.shared.domain.repository.IncomeRepository
import com.cinch.shared.time.LocalClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate

@Immutable
data class MonthState(
    val isLoading: Boolean = true,
    val today: LocalDate,
    val summary: MonthSummary? = null,
    val incomes: List<Income> = emptyList(),
)

sealed interface MonthEvent {
    /** Ekran öne geldi: ay değişmiş olabilir. */
    data object Resumed : MonthEvent
}

/** "Bu ay" sekmesi: gelir − harcama özeti ve gelir listesi. */
@OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest
class MonthViewModel(
    observeMonthSummary: ObserveMonthSummary,
    incomes: IncomeRepository,
    private val clock: LocalClock,
) : ViewModel() {

    private val today = MutableStateFlow(clock.today())

    val state: StateFlow<MonthState> = combine(
        today,
        today.flatMapLatest { observeMonthSummary(it) },
        incomes.observeAll(),
    ) { day, summary, list ->
        MonthState(isLoading = false, today = day, summary = summary, incomes = list)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthState(today = today.value))

    fun onEvent(event: MonthEvent) {
        when (event) {
            MonthEvent.Resumed -> today.value = clock.today()
        }
    }
}
