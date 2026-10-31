package com.cinch.shared.cards

import com.cinch.shared.domain.Card
import com.cinch.shared.domain.CardStatement
import com.cinch.shared.domain.CardType
import com.cinch.shared.domain.Expense
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CardOverviewTest {

    private val today = LocalDate(2025, 2, 3)
    private fun tl(units: Long) = Money.of(units, Currency.TRY)

    /** Kesim günü 15: bugünün dönemi 16 Ocak – 3 Şubat. */
    private val credit = Card(id = 1, name = "QNB", carriedBalance = tl(0), statementDay = 15)
    private val debit = Card(id = 2, name = "Maaş", carriedBalance = tl(0), type = CardType.DEBIT)

    private fun spend(day: LocalDate, units: Long, card: Card = credit) =
        Expense(date = day, grossAmount = tl(units), cardId = card.id)

    private fun reading(readOn: LocalDate, due: LocalDate?, balance: Long?, period: Long? = null) = CardStatement(
        cardId = credit.id, readOn = readOn, dueDate = due, statementBalance = balance?.let(::tl), periodSpending = period?.let(::tl),
    )

    private fun of(card: Card = credit, expenses: List<Expense> = emptyList(), readings: List<CardStatement> = emptyList()) =
        CardOverviews.of(card, expenses, readings, today)

    @Test
    fun noReading_showsOnlyThisPeriodsSpending() {
        val o = of(expenses = listOf(spend(LocalDate(2025, 1, 20), 300), spend(LocalDate(2025, 1, 10), 999))) // 10 Ocak önceki dönem
        assertEquals(CardStatus.NO_READING, o.status)
        assertEquals(tl(300), o.periodSpent)
        assertNull(o.toPay)
        assertNull(o.dueDate)
    }

    @Test
    fun due_paid_overdue_stale() {
        val due = of(readings = listOf(reading(LocalDate(2025, 2, 1), LocalDate(2025, 2, 6), 52_331)))
        assertEquals(CardStatus.DUE, due.status)
        assertEquals(tl(52_331), due.toPay)
        assertEquals(3, due.daysLeft)

        val paid = of(readings = listOf(reading(LocalDate(2025, 2, 2), LocalDate(2025, 2, 6), 0)))
        assertEquals(CardStatus.PAID, paid.status)
        assertNull(paid.toPay)

        val overdue = of(readings = listOf(reading(LocalDate(2025, 1, 20), LocalDate(2025, 1, 30), 5_000)))
        assertEquals(CardStatus.OVERDUE, overdue.status)
        assertEquals(tl(5_000), overdue.toPay)
        assertEquals(-4, overdue.daysLeft)

        // Son ödemeden 10 günden fazla geçti: yeni ekstre kesilmiştir, eski borç gösterilmez.
        val stale = of(readings = listOf(reading(LocalDate(2025, 1, 2), LocalDate(2025, 1, 10), 5_000)))
        assertEquals(CardStatus.STALE, stale.status)
        assertNull(stale.toPay)
    }

    @Test
    fun missing_comesFromTheBankReadingOfThisPeriod() {
        val expenses = listOf(spend(LocalDate(2025, 1, 20), 900))
        val short = of(expenses = expenses, readings = listOf(reading(LocalDate(2025, 2, 2), LocalDate(2025, 2, 25), 6_000, period = 1_240)))
        assertEquals(tl(1_240), short.bankPeriodSpending)
        assertEquals(tl(340), short.missing)

        val matches = of(expenses = expenses, readings = listOf(reading(LocalDate(2025, 2, 2), LocalDate(2025, 2, 25), 6_000, period = 900)))
        assertNull(matches.missing)

        // Okuma önceki döneme ait: karşılaştırılmaz.
        val old = of(expenses = expenses, readings = listOf(reading(LocalDate(2025, 1, 10), LocalDate(2025, 2, 6), 6_000, period = 5_000)))
        assertNull(old.bankPeriodSpending)
        assertNull(old.missing)
    }

    @Test
    fun debitCard_hasNoStatementStatus() {
        val o = of(card = debit, expenses = listOf(spend(LocalDate(2025, 2, 1), 120, debit), spend(LocalDate(2025, 1, 31), 50, debit)))
        assertEquals(CardStatus.DEBIT, o.status)
        assertEquals(tl(120), o.periodSpent) // banka kartında dönem ayın başından
        assertNull(o.dueDate)
    }

    @Test
    fun paidReading_zeroesTheBalance_keepsTheDueDate() {
        val latest = reading(LocalDate(2025, 2, 1), LocalDate(2025, 2, 6), 52_331).copy(id = 7, minimumPayment = tl(10_000))
        val paid = CardOverviews.paidReading(latest, today)
        assertEquals(0, paid.id)
        assertEquals(today, paid.readOn)
        assertEquals(tl(0), paid.statementBalance)
        assertNull(paid.minimumPayment)
        assertEquals(LocalDate(2025, 2, 6), paid.dueDate)
        assertEquals(CardStatus.PAID, of(readings = listOf(paid, latest)).status)
    }
}
