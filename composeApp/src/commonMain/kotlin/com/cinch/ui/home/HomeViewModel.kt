package com.cinch.ui.home

import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinch.shared.budget.BudgetEngine
import com.cinch.shared.budget.ObserveBudgetStatus
import com.cinch.shared.category.Categorizer
import com.cinch.shared.domain.Expense
import com.cinch.shared.domain.repository.BudgetRepository
import com.cinch.shared.domain.repository.ExpenseRepository
import com.cinch.shared.parser.QuickEntryParser
import com.cinch.ui.common.TodayProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.plus

/**
 * Ana ekran: bütçe durumu, bu haftanın harcamaları ve hızlı kayıt.
 * Durum ve liste veritabanını izler; kayıt/silme sonrası ekran kendiliğinden güncellenir.
 *
 * @param ioDispatcher ayrıştırma da buradan yapılır: ilk çağrı kategori düzeltmelerini diskten yükler.
 */
@OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest, mapLatest
class HomeViewModel(
    budgets: BudgetRepository,
    private val expenses: ExpenseRepository,
    observeBudgetStatus: ObserveBudgetStatus,
    private val parser: QuickEntryParser,
    private val categorizer: Categorizer,
    private val todayProvider: TodayProvider,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val today = MutableStateFlow(todayProvider.today())
    private val _state = MutableStateFlow(HomeState(today = today.value))

    private val weekExpenses: Flow<List<Expense>> = today.flatMapLatest { day ->
        budgets.observe()
            .map { it?.weekStart ?: DayOfWeek.MONDAY }
            .distinctUntilChanged()
            .flatMapLatest { weekStart ->
                val first = BudgetEngine.startOfWeek(day, weekStart)
                expenses.observeBetween(first, first.plus(DatePeriod(days = 6)))
            }
    }.map { list -> list.sortedWith(compareByDescending<Expense> { it.date }.thenByDescending { it.id }) }

    val state: StateFlow<HomeState> = combine(
        _state,
        today,
        today.flatMapLatest { observeBudgetStatus(it) },
        weekExpenses,
    ) { current, day, status, week ->
        current.copy(isLoading = false, today = day, status = status, weekExpenses = week)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _state.value)

    private val _effect = Channel<HomeEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    init {
        // Yazdıkça önizleme: tutar, kategori, tarih ve pay.
        viewModelScope.launch {
            combine(snapshotFlow { _state.value.entry.text.toString() }, today) { text, day -> text.trim() to day }
                .mapLatest { (text, day) ->
                    if (text.isEmpty()) null else withContext(ioDispatcher) { parser.parse(text, day) }
                }
                .collect { parsed ->
                    _state.update {
                        it.copy(parsed = parsed, categoryOverride = it.categoryOverride.takeIf { parsed != null })
                    }
                }
        }
    }

    fun onEvent(event: HomeEvent) {
        when (event) {
            HomeEvent.Resumed -> today.value = todayProvider.today()
            is HomeEvent.CategoryPicked -> _state.update { it.copy(categoryOverride = event.category) }
            HomeEvent.SaveEntry -> saveEntry()
            is HomeEvent.DeleteExpense -> delete(event.expense)
            is HomeEvent.UndoDelete -> viewModelScope.launch { expenses.add(event.expense.copy(id = 0)) }
        }
    }

    private fun saveEntry() {
        val current = _state.value
        val text = current.entry.text.toString().trim()
        if (current.isSaving || text.isEmpty()) return

        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                // Önizleme bir adım geride olabilir; kaydedilen her zaman alandaki son metindir.
                val parsed = withContext(ioDispatcher) { parser.parse(text, today.value) }
                val expense = parsed.toExpense()?.takeIf { it.grossAmount.minor > 0 } ?: return@launch
                val category = current.categoryOverride ?: parsed.category
                if (category != parsed.category) {
                    // Düzeltme diske yazılır: aynı kelimeler bir dahaki sefere doğru tahmin edilir.
                    withContext(ioDispatcher) { categorizer.learnFromCorrection(parsed.tokens, category) }
                }
                expenses.add(expense.copy(category = category))
                current.entry.clearText()
                _state.update { it.copy(categoryOverride = null) }
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun delete(expense: Expense) {
        viewModelScope.launch {
            expenses.delete(expense.id)
            _effect.send(HomeEffect.ExpenseDeleted(expense))
        }
    }
}
