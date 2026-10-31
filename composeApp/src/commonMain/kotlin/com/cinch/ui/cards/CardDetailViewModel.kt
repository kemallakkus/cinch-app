package com.cinch.ui.cards

import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinch.shared.cards.CardOverviews
import com.cinch.navigation.CardDetailRoute
import com.cinch.shared.debt.PayoffSimulator
import com.cinch.shared.domain.Card
import com.cinch.shared.domain.CardStatement
import com.cinch.shared.domain.Category
import com.cinch.shared.domain.Expense
import com.cinch.shared.domain.repository.CardRepository
import com.cinch.shared.domain.repository.CardStatementRepository
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
 * Kart detayı üç soruyu cevaplar: ne kadar, ne zaman ödenecek (okunan son ekstre, "Ödedim"); sıradaki ekstreye ne
 * girdi ve bankaya göre eksik var mı; tamamı ödenemeyecekse borç ne zaman biter. Tutarlar kart para birimindedir;
 * başka birimdeki harcamalar mutabakata girmez.
 */
@OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest
class CardDetailViewModel(
    private val route: CardDetailRoute,
    cards: CardRepository,
    private val expenses: ExpenseRepository,
    private val statements: CardStatementRepository,
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
        statements.observeForCard(route.cardId),
    ) { current, (card, list), payment, stated, readings ->
        if (card == null) return@combine current.copy(isLoading = false, card = null)
        val inCurrency = list.filter { it.grossAmount.currency == card.currency }
        val latest = readings.firstOrNull()
        val overview = CardOverviews.of(card, list, readings, today)
        // Borç planı okunan kalan borçla; okuma yoksa eskiden elle girilmiş borçla.
        val debt = overview.toPay ?: card.carriedBalance.takeIf { card.isCredit && !it.isZero && !it.isNegative }
        current.copy(
            statement = latest,
            // Aynı ekstrenin okumaları: borç ödendikçe azalır, en yüksek okuma başlangıçtır.
            statementStartBalance = latest?.dueDate?.let { due ->
                readings.filter { it.dueDate == due }.mapNotNull { it.statementBalance }.maxOrNull()
            },
            statementHistory = statementHistory(readings),
            isLoading = false,
            card = card,
            period = card.currentPeriod(today),
            periodExpenses = list.sortedWith(compareByDescending<Expense> { it.date }.thenByDescending { it.id }),
            periodTotal = Money.sum(inCurrency.map { it.grossAmount }, card.currency),
            overview = overview,
            payoffDebt = debt,
            payoff = if (payment == null || debt == null) null else {
                simulator.simulateCard(debt, card.monthlyRatePercent, Money(payment, debt.currency))
            },
            firstMonthInterest = debt?.let { firstMonthInterest(it, card.monthlyRatePercent) },
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
            CardDetailEvent.AddStatementDifference -> state.value.overview?.missing?.let { addDifference(it) }
            CardDetailEvent.MarkPaidClicked -> if (state.value.statement != null) _state.update { it.copy(confirmingPaid = true) }
            CardDetailEvent.MarkPaidDismissed -> _state.update { it.copy(confirmingPaid = false) }
            CardDetailEvent.MarkPaidConfirmed -> markPaid()
            CardDetailEvent.PayoffToggled -> _state.update { it.copy(payoffChoice = !state.value.payoffExpanded) }
            CardDetailEvent.ManualCompareToggled -> _state.update { it.copy(manualCompareOpen = !it.manualCompareOpen) }
        }
    }

    /** Son okumanın sıfır borçlu kopyası: geçmiş ve ilerleme bozulmaz, hatırlatmalar susar. */
    private fun markPaid() {
        _state.update { it.copy(confirmingPaid = false) }
        val latest = state.value.statement ?: return
        viewModelScope.launch { statements.add(CardOverviews.paidReading(latest, today)) }
    }

    private fun addMissingDifference() {
        val result = state.value.reconciliation ?: return
        if (result.kind != ReconciliationKind.MISSING_EXPENSE) return
        addDifference(result.difference)
    }

    /** Neye harcandığı hatırlanmayan fark: bu karta bugünün tarihiyle "Diğer" harcaması; bütçeye de sayılır. */
    private fun addDifference(amount: Money) {
        val card = state.value.card ?: return
        viewModelScope.launch {
            expenses.add(
                Expense(
                    date = today,
                    grossAmount = amount,
                    category = Category.DIGER,
                    cardId = card.id,
                    rawText = "Mutabakat farkı",
                ),
            )
        }
    }

    /** Son 6 ekstre, eskiden yeniye: her son ödeme tarihi için en yüksek (ilk) okunan borç. */
    private fun statementHistory(readings: List<CardStatement>): List<StatementPoint> =
        readings
            .filter { it.dueDate != null && it.statementBalance != null }
            .groupBy { it.dueDate!! }
            .map { (due, list) -> StatementPoint(due, list.mapNotNull { it.statementBalance }.max()) }
            .sortedBy { it.dueDate }
            .takeLast(HISTORY_SIZE)

    private fun firstMonthInterest(debt: Money, monthlyRatePercent: Double): Money =
        Money((debt.minor * monthlyRatePercent / 100.0).roundToLong(), debt.currency)
}

private const val HISTORY_SIZE = 6
