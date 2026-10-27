package com.cinch.shared.data

import com.cinch.shared.budget.FixedPayments
import com.cinch.shared.domain.Category
import com.cinch.shared.domain.FixedExpense
import com.cinch.shared.domain.FixedExpenseKind
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SqlFixedExpenseRepositoryTest {

    private fun tl(units: Long) = Money.of(units, Currency.TRY)

    @Test
    fun roundTrip_orderedByDay_updateAndDelete() = runTest {
        val repo = SqlFixedExpenseRepository(newDatabase(), StandardTestDispatcher(testScheduler))
        val rent = FixedExpense(name = "Kira", amount = tl(15_000), dayOfMonth = 1, kind = FixedExpenseKind.RENT)
        val netflixId = repo.add(FixedExpense(name = "Netflix", amount = Money(22_999, Currency.TRY), dayOfMonth = 20, kind = FixedExpenseKind.SUBSCRIPTION, cardId = 3))
        val rentId = repo.add(rent)

        assertEquals(listOf("Kira", "Netflix"), repo.observeAll().first().map { it.name })
        assertEquals(rent.copy(id = rentId), repo.get(rentId))
        assertEquals(3L, repo.get(netflixId)?.cardId)

        repo.update(rent.copy(id = rentId, amount = tl(16_500), cardId = 3))
        assertEquals(tl(16_500), repo.get(rentId)?.amount)

        repo.delete(rentId)
        assertEquals(listOf(netflixId), repo.all().map { it.id })
    }

    @Test
    fun markPaid_createsLinkedExpenseOnTheCard_andUndoRemovesIt() = runTest {
        val db = newDatabase()
        val io = StandardTestDispatcher(testScheduler)
        val expenses = SqlExpenseRepository(db, io)
        val payments = FixedPayments(expenses)
        val bill = FixedExpense(id = 4, name = "Elektrik", amount = tl(800), dayOfMonth = 20, kind = FixedExpenseKind.BILL, cardId = 2)
        val day = LocalDate(2025, 1, 20)

        payments.markPaid(bill, Money(91_240, Currency.TRY), day)

        val payment = expenses.between(day, day).single()
        assertEquals(4L, payment.fixedExpenseId)
        assertEquals(2L, payment.cardId)
        assertEquals(Category.FATURA, payment.category)
        assertEquals("Elektrik", payment.rawText)

        payments.undo(payment)
        assertTrue(expenses.between(day, day).isEmpty())
    }
}
