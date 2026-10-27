package com.cinch.ui.home

import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinch.shared.budget.BudgetEngine
import com.cinch.shared.budget.ObserveBudgetStatus
import com.cinch.shared.category.Categorizer
import com.cinch.shared.domain.Card
import com.cinch.shared.domain.Expense
import com.cinch.shared.domain.repository.BudgetRepository
import com.cinch.shared.domain.repository.CardRepository
import com.cinch.shared.domain.repository.ExpenseRepository
import com.cinch.shared.parser.QuickEntryParser
import com.cinch.shared.time.LocalClock
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
    cards: CardRepository,
    observeBudgetStatus: ObserveBudgetStatus,
    private val parser: QuickEntryParser,
    private val categorizer: Categorizer,
    private val clock: LocalClock,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val today = MutableStateFlow(clock.today())
    private val _state = MutableStateFlow(HomeState(today = today.value))

    /** Hızlı kayıtta kart adını tanımak ve listede kart adını göstermek için. */
    private val cardList: StateFlow<List<Card>> =
        cards.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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
        cardList,
    ) { current, day, status, week, cards ->
        current.copy(isLoading = false, today = day, status = status, weekExpenses = week, cards = cards)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _state.value)

    private val _effect = Channel<HomeEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    init {
        // Yazdıkça önizleme: tutar, kategori, kart, tarih ve pay.
        viewModelScope.launch {
            combine(snapshotFlow { _state.value.entry.text.toString() }, today, cardList) { text, day, cards ->
                Triple(text.trim(), day, cards)
            }
                .mapLatest { (text, day, cards) ->
                    if (text.isEmpty()) null else withContext(ioDispatcher) { parser.parse(text, day, cards = cards) }
                }
                .collect { parsed ->
                    _state.update {
                        // Alan boşalınca elle yapılan seçimler de sıfırlanır.
                        if (parsed == null) it.copy(parsed = null, categoryOverride = null, cardOverride = null)
                        else it.copy(parsed = parsed)
                    }
                }
        }
    }

    fun onEvent(event: HomeEvent) {
        when (event) {
            HomeEvent.Resumed -> today.value = clock.today()
            is HomeEvent.CategoryPicked -> _state.update { it.copy(categoryOverride = event.category) }
            is HomeEvent.CardPicked -> _state.update { it.copy(cardOverride = CardSelection(event.cardId)) }
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
                val parsed = withContext(ioDispatcher) { parser.parse(text, today.value, cards = cardList.value) }
                // Elle "nakit" seçildiyse metindeki kart adı yok sayılır.
                val cardId = if (current.cardOverride != null) current.cardOverride.cardId else parsed.cardId
                val expense = parsed.toExpense(cardId = cardId)?.takeIf { it.grossAmount.minor > 0 } ?: return@launch
                val category = current.categoryOverride ?: parsed.category
                if (category != parsed.category) {
                    // Düzeltme diske yazılır: aynı kelimeler bir dahaki sefere doğru tahmin edilir.
                    withContext(ioDispatcher) { categorizer.learnFromCorrection(parsed.tokens, category) }
                }
                val saved = expense.copy(category = category)
                // Kayıttan önce bakılır: sonra bakılsa durum çoktan güncellenmiş olabilir, harcama iki kez düşülürdü.
                val onTrack = staysOnTrack(saved)
                expenses.add(saved)
                _effect.send(HomeEffect.EntrySaved(onTrack))
                current.entry.clearText()
                _state.update { it.copy(categoryOverride = null, cardOverride = null) }
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }

    /**
     * Kayıttan önceki duruma göre: bugünkü harcamaysa bugünün payı, başka günse haftanın kalanı hâlâ
     * yetiyor mu. Bütçe yoksa ya da para birimi farklıysa kutlama yok.
     */
    private fun staysOnTrack(expense: Expense): Boolean {
        val status = state.value.status ?: return false
        if (expense.grossAmount.currency != status.weeklyLimit.currency) return false
        val left = if (expense.date == today.value) status.todayRemaining else status.weekRemaining
        return !(left - expense.chargeableAmount).isNegative
    }

    private fun delete(expense: Expense) {
        viewModelScope.launch {
            expenses.delete(expense.id)
            _effect.send(HomeEffect.ExpenseDeleted(expense))
        }
    }
}
