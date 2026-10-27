package com.cinch.ui

import com.cinch.shared.budget.BudgetConfig
import com.cinch.shared.domain.Expense
import com.cinch.shared.domain.repository.BudgetRepository
import com.cinch.shared.domain.repository.ExpenseRepository
import com.cinch.shared.domain.Card
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

    override suspend fun forCard(cardId: Long): List<Expense> = all.value.filter { it.cardId == cardId }

    override suspend fun add(expense: Expense): Long {
        val id = nextId++
        all.update { it + expense.copy(id = id) }
        return id
    }

    override suspend fun delete(id: Long) {
        all.update { list -> list.filterNot { it.id == id } }
    }
}

class FakeCardRepository : CardRepository {
    val all = MutableStateFlow<List<Card>>(emptyList())
    override fun observeAll(): Flow<List<Card>> = all
    override suspend fun all(): List<Card> = all.value
    override suspend fun add(card: Card): Long {
        val id = all.value.size + 1L
        all.update { it + card.copy(id = id) }
        return id
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
