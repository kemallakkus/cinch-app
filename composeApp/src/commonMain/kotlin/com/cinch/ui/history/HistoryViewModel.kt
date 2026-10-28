package com.cinch.ui.history

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinch.navigation.HistoryRoute
import com.cinch.shared.category.TextNormalizer
import com.cinch.shared.domain.Category
import com.cinch.shared.domain.Expense
import com.cinch.shared.domain.repository.CardRepository
import com.cinch.shared.domain.repository.ExpenseRepository
import com.cinch.shared.money.Money
import com.cinch.shared.time.LocalClock
import com.cinch.ui.common.label
import com.cinch.ui.common.monthFromRoute
import com.cinch.ui.common.monthStart
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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Bir günün kayıtları; [total] kullanıcının payları toplamı. */
@Immutable
data class DayGroup(val date: LocalDate, val total: Money, val expenses: List<Expense>)

/**
 * @param month gösterilen ayın ilk günü (arama yaparken kullanılmaz: arama tüm geçmişte).
 * @param category yalnız bu kategori; null = hepsi.
 * @param searchText aramadaki metin (boşsa ay görünümü).
 * @param days en yeni gün üstte.
 * @param total listedekilerin payları toplamı; liste boşsa null.
 */
@Immutable
data class HistoryState(
    val isLoading: Boolean = true,
    val today: LocalDate,
    val month: LocalDate,
    val category: Category? = null,
    val query: TextFieldState = TextFieldState(),
    val searchText: String = "",
    val days: List<DayGroup> = emptyList(),
    val count: Int = 0,
    val total: Money? = null,
    val cardNames: Map<Long, String> = emptyMap(),
) {
    val isSearching: Boolean get() = searchText.isNotEmpty()

    /** İleri ay yalnız içinde bulunulan aya kadar. */
    val canGoNext: Boolean get() = month < today.monthStart()
}

sealed interface HistoryEvent {
    data object Resumed : HistoryEvent
    data object PreviousMonth : HistoryEvent
    data object NextMonth : HistoryEvent
    data class CategoryPicked(val category: Category?) : HistoryEvent
    data class DeleteExpense(val expense: Expense) : HistoryEvent
    data class UndoDelete(val expense: Expense) : HistoryEvent
}

sealed interface HistoryEffect {
    data class ExpenseDeleted(val expense: Expense) : HistoryEffect
}

/**
 * Harcama geçmişi. Arama kutusu boşken seçili ayın kayıtları, doluysa tüm geçmişte açıklama, not,
 * kategori ve kart adında arama yapılır (Türkçe harf farkı gözetmez: "gida" "Gıda"yı bulur).
 * Sabit gider ödemeleri de listede durur.
 */
@OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest
class HistoryViewModel(
    route: HistoryRoute,
    private val expenses: ExpenseRepository,
    cards: CardRepository,
    private val clock: LocalClock,
) : ViewModel() {

    private val today = MutableStateFlow(clock.today())
    private val month = MutableStateFlow(monthFromRoute(route.year, route.month, today.value))
    private val category = MutableStateFlow(route.categoryName?.let { name -> Category.entries.firstOrNull { it.name == name } })
    private val query = TextFieldState()

    private val searchText: Flow<String> = snapshotFlow { query.text.toString().trim() }.distinctUntilChanged()

    /** Kaynak yalnız ay ya da arama moduna geçişte değişir; her tuşta veritabanı yeniden sorgulanmaz. */
    private val source: Flow<List<Expense>> =
        combine(month, searchText.map { it.isNotEmpty() }.distinctUntilChanged()) { m, searching -> m to searching }
            .flatMapLatest { (m, searching) ->
                if (searching) expenses.observeAll() else expenses.observeBetween(m, monthEnd(m))
            }

    val state: StateFlow<HistoryState> = combine(
        combine(today, month, category, ::Triple),
        searchText,
        source,
        cards.observeAll(),
    ) { (day, m, cat), text, list, cardList ->
        val cardNames = cardList.associate { it.id to it.name }
        val needle = TextNormalizer.normalize(text).split(' ').filter { it.isNotBlank() }
        val shown = list
            .filter { cat == null || it.category == cat }
            .filter { needle.isEmpty() || it.matches(needle, cardNames) }
            .sortedWith(compareByDescending<Expense> { it.date }.thenByDescending { it.id })
        HistoryState(
            isLoading = false,
            today = day,
            month = m,
            category = cat,
            query = query,
            searchText = text,
            days = shown.groupBy { it.date }.map { (date, items) -> DayGroup(date, sumShares(items), items) },
            count = shown.size,
            total = shown.takeIf { it.isNotEmpty() }?.let { sumShares(it) },
            cardNames = cardNames,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HistoryState(today = today.value, month = month.value, category = category.value, query = query),
    )

    private val _effect = Channel<HistoryEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    fun onEvent(event: HistoryEvent) {
        when (event) {
            HistoryEvent.Resumed -> today.value = clock.today()
            HistoryEvent.PreviousMonth -> month.update { it.minus(DatePeriod(months = 1)) }
            HistoryEvent.NextMonth -> month.update { m ->
                val next = m.plus(DatePeriod(months = 1))
                if (next <= today.value.monthStart()) next else m
            }
            is HistoryEvent.CategoryPicked -> category.value = event.category
            is HistoryEvent.DeleteExpense -> viewModelScope.launch {
                expenses.delete(event.expense.id)
                _effect.send(HistoryEffect.ExpenseDeleted(event.expense))
            }
            is HistoryEvent.UndoDelete -> viewModelScope.launch { expenses.add(event.expense.copy(id = 0)) }
        }
    }
}

private fun monthEnd(monthStart: LocalDate): LocalDate =
    monthStart.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))

/** Birden çok para birimi varsa ilk kaydın birimindekiler toplanır (pratikte hep TL). */
private fun sumShares(items: List<Expense>): Money {
    val currency = items.first().grossAmount.currency
    return Money.sum(items.filter { it.grossAmount.currency == currency }.map { it.chargeableAmount }, currency)
}

/** Her kelime açıklama, not, kategori ya da kart adında geçmeli ("migros qnb"). */
internal fun Expense.matches(words: List<String>, cardNames: Map<Long, String>): Boolean {
    val haystack = TextNormalizer.normalize(
        listOf(rawText, note, category.label, cardId?.let { cardNames[it] }.orEmpty()).joinToString(" "),
    )
    return words.all { it in haystack }
}
