package com.cinch.ui

import com.cinch.shared.budget.BudgetConfig
import com.cinch.shared.domain.Expense
import com.cinch.shared.domain.repository.BudgetRepository
import com.cinch.shared.domain.repository.ExpenseRepository
import com.cinch.shared.domain.Card
import com.cinch.shared.domain.CardStatement
import com.cinch.shared.domain.repository.CardStatementRepository
import com.cinch.shared.domain.FixedExpense
import com.cinch.shared.domain.Income
import com.cinch.shared.domain.repository.FixedExpenseRepository
import com.cinch.shared.domain.repository.IncomeRepository
import com.cinch.shared.domain.repository.CardRepository
import com.cinch.shared.time.LocalClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/** Bellek içi depolar: ViewModel testleri platform veritabanı sürücüsü olmadan koşar. */
class FakeBudgetRepository(initial: BudgetConfig? = null) : BudgetRepository {
    val config = MutableStateFlow(initial)
    override fun observe(): Flow<BudgetConfig?> = config
    override suspend fun get(): BudgetConfig? = config.value
    override suspend fun save(config: BudgetConfig) {
        this.config.value = config
    }
}

class FakeExpenseRepository : ExpenseRepository {
    val all = MutableStateFlow<List<Expense>>(emptyList())
    private var nextId = 1L

    override fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<Expense>> =
        all.map { list -> list.filter { it.date in from..to } }

    override suspend fun between(from: LocalDate, to: LocalDate): List<Expense> =
        all.value.filter { it.date in from..to }

    override fun observeAll(): Flow<List<Expense>> = all.map { list -> list.sortedWith(compareBy({ it.date }, { it.id })) }

    override suspend fun get(id: Long): Expense? = all.value.firstOrNull { it.id == id }

    override suspend fun update(expense: Expense) {
        // Gerçek depo gibi para birimi ve sabit gider bağı korunur.
        all.update { list ->
            list.map {
                if (it.id == expense.id) {
                    expense.copy(
                        grossAmount = expense.grossAmount.copy(currency = it.grossAmount.currency),
                        fixedExpenseId = it.fixedExpenseId,
                    )
                } else {
                    it
                }
            }
        }
    }

    override suspend fun forCard(cardId: Long): List<Expense> = all.value.filter { it.cardId == cardId }

    override fun observeFixedPayments(): Flow<List<Expense>> = all.map { list -> list.filter { it.isFixedPayment } }

    override suspend fun fixedPayments(): List<Expense> = all.value.filter { it.isFixedPayment }

    override fun observeForCardBetween(cardId: Long, from: LocalDate, to: LocalDate): Flow<List<Expense>> =
        all.map { list -> list.filter { it.cardId == cardId && it.date in from..to } }

    override suspend fun add(expense: Expense): Long {
        val id = nextId++
        all.update { it + expense.copy(id = id) }
        return id
    }

    override suspend fun delete(id: Long) {
        all.update { list -> list.filterNot { it.id == id } }
    }
}

class FakeCardRepository(private val expenses: FakeExpenseRepository? = null) : CardRepository {
    val all = MutableStateFlow<List<Card>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<Card>> = all
    override suspend fun all(): List<Card> = all.value
    override fun observe(id: Long): Flow<Card?> = all.map { list -> list.firstOrNull { it.id == id } }
    override suspend fun get(id: Long): Card? = all.value.firstOrNull { it.id == id }

    override suspend fun add(card: Card): Long {
        val id = nextId++
        all.update { it + card.copy(id = id) }
        return id
    }

    override suspend fun update(card: Card) {
        all.update { list -> list.map { if (it.id == card.id) card else it } }
    }

    /** Gerçek depo gibi kartın harcamalarını nakde çevirir. */
    override suspend fun delete(id: Long) {
        all.update { list -> list.filterNot { it.id == id } }
        expenses?.all?.update { list -> list.map { if (it.cardId == id) it.copy(cardId = null) else it } }
    }
}

class FakeCardStatementRepository : CardStatementRepository {
    val all = MutableStateFlow<List<CardStatement>>(emptyList())
    private var nextId = 1L

    override fun observeForCard(cardId: Long): Flow<List<CardStatement>> =
        all.map { list -> list.filter { it.cardId == cardId }.sortedWith(compareByDescending<CardStatement> { it.readOn }.thenByDescending { it.id }) }

    override suspend fun latestPerCard(): List<CardStatement> =
        all.value.sortedWith(compareByDescending<CardStatement> { it.readOn }.thenByDescending { it.id }).distinctBy { it.cardId }

    override suspend fun add(statement: CardStatement): Long {
        val id = nextId++
        all.update { it + statement.copy(id = id) }
        return id
    }
}

class FakeIncomeRepository : IncomeRepository {
    val all = MutableStateFlow<List<Income>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<Income>> = all
    override suspend fun all(): List<Income> = all.value
    override suspend fun get(id: Long): Income? = all.value.firstOrNull { it.id == id }

    override suspend fun add(income: Income): Long {
        val id = nextId++
        all.update { it + income.copy(id = id) }
        return id
    }

    override suspend fun update(income: Income) {
        all.update { list -> list.map { if (it.id == income.id) income else it } }
    }

    override suspend fun delete(id: Long) {
        all.update { list -> list.filterNot { it.id == id } }
    }
}

class FakeFixedExpenseRepository : FixedExpenseRepository {
    val all = MutableStateFlow<List<FixedExpense>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<FixedExpense>> = all
    override suspend fun all(): List<FixedExpense> = all.value
    override suspend fun get(id: Long): FixedExpense? = all.value.firstOrNull { it.id == id }

    override suspend fun add(fixed: FixedExpense): Long {
        val id = nextId++
        all.update { it + fixed.copy(id = id) }
        return id
    }

    override suspend fun update(fixed: FixedExpense) {
        all.update { list -> list.map { if (it.id == fixed.id) fixed else it } }
    }

    override suspend fun delete(id: Long) {
        all.update { list -> list.filterNot { it.id == id } }
    }
}

class FakeClock(var date: LocalDate, var time: LocalTime = LocalTime(12, 0)) : LocalClock {
    override fun now(): LocalDateTime = LocalDateTime(date, time)
}

/**
 * `Dispatchers.Main`'i test dispatcher'ına bağlar; `runTest` aynı zamanlayıcıyı kendiliğinden kullanır.
 * Sıfırlama `runTest` bittikten sonra yapılır: test sonunda iptal edilen abonelikler (ör. `stateIn`)
 * hâlâ Main'e dispatch edebilir, gerçek Android Main'i host testte yoktur.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> withTestMain(block: () -> T): T {
    Dispatchers.setMain(StandardTestDispatcher())
    try {
        return block()
    } finally {
        Dispatchers.resetMain()
    }
}
