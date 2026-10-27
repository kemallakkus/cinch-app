package com.cinch.ui.cards

import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinch.navigation.CardDetailRoute
import com.cinch.shared.debt.PayoffSimulator
import com.cinch.shared.domain.Card
import com.cinch.shared.domain.Category
import com.cinch.shared.domain.Expense
import com.cinch.shared.domain.repository.CardRepository
import com.cinch.shared.domain.repository.ExpenseRepository
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.shared.money.MoneyInput
import com.cinch.shared.reconcile.ReconciliationKind
import com.cinch.shared.reconcile.Reconciler
import com.cinch.shared.time.LocalClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

/**
 * Kart detayı: bu ekstre dönemindeki harcamalar, bankayla mutabakat ve devreden borcun kapanış planı.
 * Tutarlar kart para birimindedir; başka birimdeki harcamalar mutabakata girmez.
 */
@OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest
class CardDetailViewModel(
    private val route: CardDetailRoute,
    cards: CardRepository,
    private val expenses: ExpenseRepository,
    private val reconciler: Reconciler,
    private val simulator: PayoffSimulator,
    private val clock: LocalClock,
) : ViewModel() {

    private val today = clock.today()
    private val _state = MutableStateFlow(CardDetailState(today = today))

    /** Kullanıcının yazdıkları, minor unit olarak; para birimi karttan gelir. */
    private val paymentMinor = MutableStateFlow<Long?>(null)
    private val statedMinor = MutableStateFlow<Long?>(null)

    /** Kart ve dönem harcamaları tek akışta: kart değişirse (ör. ekstre günü) dönem de yeniden kurulur. */
    private val cardWithExpenses = cards.observe(route.cardId).flatMapLatest { card ->
        if (card == null) {
            flowOf<Pair<Card?, List<Expense>>>(null to emptyList())
        } else {
            val period = card.currentPeriod(today)
            expenses.observeForCardBetween(card.id, period.start, period.endInclusive).map { card to it }
        }
    }

    val state: StateFlow<CardDetailState> = combine(
        _state,
        cardWithExpenses,
        paymentMinor,
        statedMinor,
    ) { current, (card, list), payment, stated ->
        if (card == null) return@combine current.copy(isLoading = false, card = null)
        val inCurrency = list.filter { it.grossAmount.currency == card.currency }
        current.copy(
            isLoading = false,
            card = card,
            period = card.currentPeriod(today),
            periodExpenses = list.sortedWith(compareByDescending<Expense> { it.date }.thenByDescending { it.id }),
            periodTotal = Money.sum(inCurrency.map { it.grossAmount }, card.currency),
            payoff = payment?.let {
                simulator.simulateCard(card.carriedBalance, card.monthlyRatePercent, Money(it, card.currency))
            },
            firstMonthInterest = firstMonthInterest(card),
            reconciliation = stated?.let {
                reconciler.reconcile(inCurrency, Money(it, card.currency), tolerance = Money.of(1, card.currency))
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _state.value)

    init {
        viewModelScope.launch {
            snapshotFlow { _state.value.monthlyPayment.text.toString() }.collect { text ->
                paymentMinor.value = MoneyInput.parse(text, Currency.TRY)?.takeIf { !it.isZero }?.minor
            }
        }
        viewModelScope.launch {
            // Banka tutarı değişince eski sonuç gizlenir; yeniden "Karşılaştır" beklenir.
            snapshotFlow { _state.value.statedSpending.text.toString() }.collect {
                statedMinor.value = null
                _state.update { it.copy(statedSpendingError = false) }
            }
        }
    }

    fun onEvent(event: CardDetailEvent) {
        when (event) {
            CardDetailEvent.Reconcile -> {
                val stated = MoneyInput.parse(_state.value.statedSpending.text.toString(), Currency.TRY)
                if (stated == null) _state.update { it.copy(statedSpendingError = true) }
                else statedMinor.value = stated.minor
            }
            CardDetailEvent.AddMissingDifference -> addMissingDifference()
        }
    }

    private fun addMissingDifference() {
        val result = state.value.reconciliation ?: return
        val card = state.value.card ?: return
        if (result.kind != ReconciliationKind.MISSING_EXPENSE) return
        viewModelScope.launch {
            expenses.add(
                Expense(
                    date = today,
                    grossAmount = result.difference,
                    category = Category.DIGER,
                    cardId = card.id,
                    rawText = "Mutabakat farkı",
                ),
            )
        }
    }

    private fun firstMonthInterest(card: Card): Money? {
        if (card.carriedBalance.isZero) return null
        val minor = (card.carriedBalance.minor * card.monthlyRatePercent / 100.0).roundToLong()
        return Money(minor, card.currency)
    }
}
