package com.cinch.shared.data

import com.cinch.shared.db.CinchDatabase
import com.cinch.shared.db.DatabaseDriverFactory
import com.cinch.shared.db.createDatabase
import com.cinch.shared.domain.Card
import com.cinch.shared.domain.Category
import com.cinch.shared.domain.Expense
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal fun newDatabase(): CinchDatabase = createDatabase(DatabaseDriverFactory())

@OptIn(ExperimentalCoroutinesApi::class) // runCurrent
class SqlExpenseRepositoryTest {

    private val day = LocalDate(2025, 1, 13)
    private fun tl(units: Long) = Money.of(units, Currency.TRY)
    private fun TestScope.repo(db: CinchDatabase = newDatabase()) =
        SqlExpenseRepository(db, StandardTestDispatcher(testScheduler))

    @Test
    fun add_roundTripsEveryField() = runTest {
        val repo = repo()
        val expense = Expense(
            date = day,
            grossAmount = Money(62_550, Currency.TRY),
            chargeableFraction = 0.5,
            category = Category.YEMEK,
            note = "akşam",
            tokens = listOf("yemek", "qnb"),
            rawText = "625,50 yemek qnb yarısı geri geldi",
        )
        val id = repo.add(expense)

        assertEquals(listOf(expense.copy(id = id)), repo.between(day, day))
    }

    @Test
    fun add_returnsDistinctIds() = runTest {
        val repo = repo()
        val a = repo.add(Expense(date = day, grossAmount = tl(10)))
        val b = repo.add(Expense(date = day, grossAmount = tl(20)))
        assertTrue(a != b)
    }

    @Test
    fun between_isInclusiveAndOrderedByDate() = runTest {
        val repo = repo()
        val dates = listOf(LocalDate(2025, 1, 15), LocalDate(2025, 1, 12), day, LocalDate(2025, 1, 16))
        dates.forEach { repo.add(Expense(date = it, grossAmount = tl(10))) }

        val result = repo.between(LocalDate(2025, 1, 12), LocalDate(2025, 1, 15)).map { it.date }

        assertEquals(listOf(LocalDate(2025, 1, 12), day, LocalDate(2025, 1, 15)), result)
    }

    @Test
    fun delete_removesOnlyThatExpense() = runTest {
        val repo = repo()
        val keep = repo.add(Expense(date = day, grossAmount = tl(10)))
        val drop = repo.add(Expense(date = day, grossAmount = tl(20)))

        repo.delete(drop)

        assertEquals(listOf(keep), repo.between(day, day).map { it.id })
    }

    @Test
    fun forCard_returnsOnlyThatCardsExpenses() = runTest {
        val db = newDatabase()
        val io = StandardTestDispatcher(testScheduler)
        val cardId = SqlCardRepository(db, io).add(Card(name = "QNB", carriedBalance = tl(0)))
        val repo = SqlExpenseRepository(db, io)
        repo.add(Expense(date = day, grossAmount = tl(100), cardId = cardId))
        repo.add(Expense(date = day, grossAmount = tl(50))) // nakit

        val result = repo.forCard(cardId)

        assertEquals(listOf(tl(100)), result.map { it.grossAmount })
    }

    @Test
    fun observeBetween_emitsAfterEachChange() = runTest {
        val repo = repo()
        val sizes = mutableListOf<Int>()
        backgroundScope.launch { repo.observeBetween(day, day).collect { sizes += it.size } }
        runCurrent()

        val id = repo.add(Expense(date = day, grossAmount = tl(10)))
        runCurrent()
        repo.add(Expense(date = LocalDate(2025, 2, 1), grossAmount = tl(10))) // aralık dışı
        runCurrent()
        repo.delete(id)
        runCurrent()

        // Aralık dışı kayıt da sorguyu tetikler; liste aynı kalır (1).
        assertEquals(listOf(0, 1, 1, 0), sizes)
    }
}
