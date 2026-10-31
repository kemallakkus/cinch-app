package com.cinch.shared.recurring

import com.cinch.shared.domain.Category
import com.cinch.shared.domain.Expense
import com.cinch.shared.domain.FixedExpense
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecurringPaymentsTest {

    private val today = LocalDate(2025, 4, 8)
    private fun tl(units: Long) = Money.of(units, Currency.TRY)

    private fun netflix(month: Int, day: Int = 5, amount: Long = 199) = Expense(
        date = LocalDate(2025, month, day),
        grossAmount = tl(amount),
        category = Category.EGLENCE,
        tokens = listOf("netflix"),
        rawText = "$amount Netflix qnb",
        cardId = 7,
    )

    @Test
    fun threeMonthsInARow_sameDayAndAmount_isASubscription() {
        val found = RecurringPayments.find(listOf(netflix(2), netflix(3, day = 6), netflix(4)), today).single()
        assertEquals("netflix", found.key)
        assertEquals("Netflix", found.name) // tutar ve kart adı ("qnb": kelimelerde yok) çıkar
        assertEquals(3, found.months)
        assertEquals(5, found.dayOfMonth)
        assertEquals(tl(199), found.lastAmount)
        assertEquals(7, found.cardId)
        assertEquals(LocalDate(2025, 5, 5), found.expectedNext)
        assertNull(found.priceChange)
        assertFalse(found.isLate)
    }

    @Test
    fun priceChange_andLate() {
        val up = RecurringPayments.find(listOf(netflix(1), netflix(2), netflix(3), netflix(4, amount = 229)), today).single()
        assertEquals(tl(30), up.priceChange)

        val late = RecurringPayments.find(listOf(netflix(1), netflix(2), netflix(3)), LocalDate(2025, 4, 12)).single()
        assertTrue(late.isLate) // 5 Nisan bekleniyordu, 12'si oldu
    }

    @Test
    fun notSubscriptions() {
        // İki ay yetmez.
        assertTrue(RecurringPayments.find(listOf(netflix(3), netflix(4)), today).isEmpty())
        // Arada boş ay var.
        assertTrue(RecurringPayments.find(listOf(netflix(1), netflix(2), netflix(4)), today).isEmpty())
        // Ayda birden çok (her hafta market).
        val market = (1..4).flatMap { m -> listOf(5, 12).map { d -> netflix(m, d).copy(tokens = listOf("market")) } }
        assertTrue(RecurringPayments.find(market, today).isEmpty())
        // Tutar her ay çok farklı.
        assertTrue(RecurringPayments.find(listOf(netflix(2, amount = 100), netflix(3, amount = 400), netflix(4, amount = 250)), today).isEmpty())
        // Günler dağınık.
        assertTrue(RecurringPayments.find(listOf(netflix(2, day = 1), netflix(3, day = 20), netflix(4, day = 5)), today).isEmpty())
        // Artık gelmiyor (son görülme 45 günden eski).
        assertTrue(RecurringPayments.find(listOf(netflix(1), netflix(2), netflix(3)), LocalDate(2025, 5, 20)).isEmpty())
    }

    @Test
    fun ignored_andAlreadyFixed_areNotSuggested() {
        val list = listOf(netflix(2), netflix(3), netflix(4))
        assertTrue(RecurringPayments.find(list, today, isIgnored = { it == "netflix" }).isEmpty())
        val fixed = FixedExpense(name = "Netflix", amount = tl(199), dayOfMonth = 5)
        assertTrue(RecurringPayments.find(list, today, fixed = listOf(fixed)).isEmpty())
        // Sabit gider ödemeleri zaten takipte: sayılmaz.
        assertTrue(RecurringPayments.find(list.map { it.copy(fixedExpenseId = 1) }, today).isEmpty())
    }
}
