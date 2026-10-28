package com.cinch.shared.data

import com.cinch.shared.domain.GoalProgress
import com.cinch.shared.domain.SavingsDeposit
import com.cinch.shared.domain.SavingsGoal
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SqlSavingsRepositoryTest {

    private fun tl(units: Long) = Money.of(units, Currency.TRY)
    private val day = LocalDate(2026, 10, 20)

    @Test
    fun goalsDeposits_progress_andDeletingGoalRemovesItsDeposits() = runTest {
        val repo = SqlSavingsRepository(newDatabase(), StandardTestDispatcher(testScheduler))
        val trip = repo.addGoal(SavingsGoal(name = "Tatil", emoji = "🏖️", target = tl(20_000), createdOn = day))
        val phone = repo.addGoal(SavingsGoal(name = "Telefon", target = tl(30_000), createdOn = day))
        repo.addDeposit(SavingsDeposit(goalId = trip, amount = tl(340), date = day, note = "Haftalık artan"))
        repo.addDeposit(SavingsDeposit(goalId = trip, amount = tl(-40), date = day.plusOneDay()))
        repo.addDeposit(SavingsDeposit(goalId = phone, amount = tl(1_000), date = day))

        val goals = repo.observeGoals().first()
        assertEquals(listOf("Tatil", "Telefon"), goals.map { it.name })
        assertEquals("🏖️", goals.first().emoji)
        val progress = GoalProgress.of(goals, repo.observeDeposits().first())
        assertEquals(tl(300), progress.first().saved)
        assertEquals("Haftalık artan", progress.first().deposits.first().note)

        repo.updateGoal(goals.first().copy(name = "Yaz tatili", target = tl(25_000)))
        assertEquals("Yaz tatili", repo.observeGoals().first().first().name)

        repo.deleteGoal(trip)
        assertEquals(listOf(phone), repo.observeGoals().first().map { it.id })
        assertTrue(repo.observeDeposits().first().all { it.goalId == phone })
    }

    private fun LocalDate.plusOneDay() = LocalDate.fromEpochDays(toEpochDays() + 1)
}
