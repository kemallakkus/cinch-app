package com.cinch.shared.data

import com.cinch.shared.domain.Income
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlIncomeRepositoryTest {

    private fun tl(units: Long) = Money.of(units, Currency.TRY)
    private fun TestScope.repo() = SqlIncomeRepository(newDatabase(), StandardTestDispatcher(testScheduler))

    @Test
    fun add_roundTrips() = runTest {
        val repo = repo()
        val salary = Income(name = "Maaş", amount = Money(4_000_050, Currency.TRY), dayOfMonth = 15)
        val id = repo.add(salary)
        repo.add(Income(name = "Serbest iş", amount = tl(5_000)))

        assertEquals(salary.copy(id = id), repo.get(id))
        assertEquals(listOf("Maaş", "Serbest iş"), repo.observeAll().first().map { it.name })
        assertNull(repo.all()[1].dayOfMonth)
    }

    @Test
    fun update_andDelete() = runTest {
        val repo = repo()
        val id = repo.add(Income(name = "Maaş", amount = tl(0), dayOfMonth = 15))

        val updated = Income(id = id, name = "Maaş", amount = tl(42_000), dayOfMonth = 1)
        repo.update(updated)
        assertEquals(updated, repo.get(id))

        repo.delete(id)
        assertTrue(repo.all().isEmpty())
    }
}
