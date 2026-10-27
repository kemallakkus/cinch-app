package com.cinch.shared.data

import com.cinch.shared.budget.BudgetConfig
import com.cinch.shared.domain.Card
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
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
}
