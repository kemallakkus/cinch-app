package com.cinch.shared.data

import com.cinch.shared.budget.BudgetConfig
import com.cinch.shared.domain.Card
import com.cinch.shared.domain.Expense
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SqlBudgetAndCardRepositoryTest {

    private fun tl(units: Long) = Money.of(units, Currency.TRY)

    @Test
    fun budget_isNullUntilSaved() = runTest {
        val repo = SqlBudgetRepository(newDatabase(), StandardTestDispatcher(testScheduler))
        assertNull(repo.get())
        assertNull(repo.observe().first())
    }

    @Test
    fun budget_roundTripsAndOverwritesSingleRow() = runTest {
        val repo = SqlBudgetRepository(newDatabase(), StandardTestDispatcher(testScheduler))
        repo.save(BudgetConfig(weeklyLimit = tl(700)))
        val updated = BudgetConfig(
            weeklyLimit = Money.of(150, Currency.EUR),
            weekStart = DayOfWeek.SUNDAY,
            weeklyGoal = Money.of(120, Currency.EUR),
            salaryDay = 15,
        )
        repo.save(updated)

        assertEquals(updated, repo.get())
        assertEquals(updated, repo.observe().first())
    }

    @Test
    fun budget_rejectsGoalInAnotherCurrency() = runTest {
        val repo = SqlBudgetRepository(newDatabase(), StandardTestDispatcher(testScheduler))
        assertFailsWith<IllegalArgumentException> {
            repo.save(BudgetConfig(weeklyLimit = tl(700), weeklyGoal = Money.of(100, Currency.USD)))
        }
    }

    @Test
    fun card_roundTrips() = runTest {
        val repo = SqlCardRepository(newDatabase(), StandardTestDispatcher(testScheduler))
        val card = Card(
            name = "QNB",
            carriedBalance = Money(1_250_075, Currency.TRY),
            monthlyRatePercent = 4.25,
            statementDay = 15,
        )
        val id = repo.add(card)
        repo.add(Card(name = "Nakit avans", carriedBalance = tl(0)))

        assertEquals(card.copy(id = id), repo.all().first())
        assertEquals(listOf("QNB", "Nakit avans"), repo.observeAll().first().map { it.name })
        assertNull(repo.all()[1].statementDay)
    }

    @Test
    fun card_updateChangesEditableFields() = runTest {
        val repo = SqlCardRepository(newDatabase(), StandardTestDispatcher(testScheduler))
        val id = repo.add(Card(name = "QNB", carriedBalance = tl(1000), statementDay = 15))
        val updated = Card(id = id, name = "QNB Kart", carriedBalance = tl(800), monthlyRatePercent = 3.5, statementDay = null)

        repo.update(updated)

        assertEquals(updated, repo.get(id))
        assertEquals(updated, repo.observe(id).first())
    }

    @Test
    fun card_deleteKeepsItsExpensesAsCash() = runTest {
        val db = newDatabase()
        val io = StandardTestDispatcher(testScheduler)
        val cards = SqlCardRepository(db, io)
        val expenses = SqlExpenseRepository(db, io)
        val id = cards.add(Card(name = "QNB", carriedBalance = tl(0)))
        val day = LocalDate(2025, 1, 13)
        expenses.add(Expense(date = day, grossAmount = tl(100), cardId = id))

        cards.delete(id)

        assertNull(cards.get(id))
        assertNull(cards.observe(id).first())
        assertEquals(listOf<Long?>(null), expenses.between(day, day).map { it.cardId })
    }

    @Test
    fun observeForCardBetween_filtersByCardAndDate() = runTest {
        val db = newDatabase()
        val io = StandardTestDispatcher(testScheduler)
        val cards = SqlCardRepository(db, io)
        val expenses = SqlExpenseRepository(db, io)
        val qnb = cards.add(Card(name = "QNB", carriedBalance = tl(0)))
        val other = cards.add(Card(name = "Bonus", carriedBalance = tl(0)))
        expenses.add(Expense(date = LocalDate(2025, 1, 10), grossAmount = tl(10), cardId = qnb)) // aralık dışı
        expenses.add(Expense(date = LocalDate(2025, 1, 16), grossAmount = tl(20), cardId = qnb))
        expenses.add(Expense(date = LocalDate(2025, 1, 16), grossAmount = tl(30), cardId = other))
        expenses.add(Expense(date = LocalDate(2025, 1, 17), grossAmount = tl(40))) // nakit

        val result = expenses.observeForCardBetween(qnb, LocalDate(2025, 1, 16), LocalDate(2025, 2, 15)).first()

        assertEquals(listOf(tl(20)), result.map { it.grossAmount })
    }
}
