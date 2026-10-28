package com.cinch.shared.budget

import com.cinch.shared.domain.Card
import com.cinch.shared.domain.CardStatement
import com.cinch.shared.domain.CardType
import com.cinch.shared.domain.Expense
import com.cinch.shared.domain.FixedExpense
import com.cinch.shared.domain.FixedExpenseKind
import com.cinch.shared.domain.FixedPlan
import com.cinch.shared.domain.Income
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CashFlowsTest {

    private val today = LocalDate(2026, 10, 20)
    private fun tl(units: Long) = Money.of(units, Currency.TRY)

    private val salary = Income(name = "Maaş", amount = tl(40_000), dayOfMonth = 15)
    private val rent = FixedExpense(id = 1, name = "Kira", amount = tl(15_000), dayOfMonth = 1, kind = FixedExpenseKind.RENT)
    private val qnb = Card(id = 1, name = "QNB", carriedBalance = tl(0))

    private fun forecast(
        startBalance: Money?,
        incomes: List<Income> = listOf(salary),
        fixed: List<FixedExpense> = emptyList(),
        payments: List<Expense> = emptyList(),
        cards: List<Card> = listOf(qnb),
        statements: List<CardStatement> = emptyList(),
        spentToday: Money = tl(0),
    ) = CashFlows.forecast(
        today = today,
        currency = Currency.TRY,
        startBalance = startBalance,
        incomes = incomes,
        fixed = fixed,
        fixedPayments = payments,
        cards = cards,
        statements = statements,
        dailySpending = tl(100),
        spentToday = spentToday,
    )

    private fun cardDue(due: LocalDate, balance: Long) =
        CardStatement(cardId = 1, readOn = today, dueDate = due, statementBalance = tl(balance))

    @Test
    fun enoughUntilPayday() {
        val f = forecast(
            tl(50_000),
            fixed = listOf(rent),
            // Ekim kirası ödendi: yalnız 1 Kasım'daki düşülür.
            payments = listOf(Expense(date = LocalDate(2026, 10, 1), grossAmount = tl(15_000), fixedExpenseId = 1)),
            statements = listOf(cardDue(LocalDate(2026, 10, 26), 10_000)),
        )

        assertEquals(LocalDate(2026, 11, 15), f.nextPayday)
        // 26 gün × 100 ₺ harcama + 10.000 kart + 15.000 kira
        assertEquals(tl(22_400), f.balanceBeforePayday)
        assertNull(f.firstShortfall)
        assertNull(f.dailyCutNeeded)
        assertEquals(tl(40_000), f.totalIn)
        assertEquals(tl(25_000), f.totalOut)
    }

    @Test
    fun shortfall_dayAndDailyCut() {
        val f = forecast(tl(5_000), statements = listOf(cardDue(LocalDate(2026, 10, 26), 6_000)))

        assertEquals(LocalDate(2026, 10, 26), f.firstShortfall)
        // En kötü gün kart ödemesi günü: 1.700 ₺ açık / 7 gün ≈ 243 ₺ → 10 TL'ye yuvarlanır.
        assertEquals(tl(250), f.dailyCutNeeded)
        assertTrue(f.dailyCutNeeded!! > f.dailySpending) // kessen bile yetmiyor
    }

    @Test
    fun todayCountsOnlyWhatIsLeftOfTheDailyShare() {
        val f = forecast(tl(1_000), spentToday = tl(30))
        assertEquals(tl(70), f.days.first().spending)
        assertEquals(tl(930), f.days.first().balance)
    }

    @Test
    fun fixedExpenses_installmentsLimited_cardPaidSkipped_overdueToday_paidMonthSkipped() {
        val fixed = listOf(
            FixedExpense(id = 2, name = "Netflix", amount = tl(230), dayOfMonth = 5, cardId = 1),
            FixedExpense(
                id = 3, name = "Ablama borç", amount = tl(5_000), dayOfMonth = 22,
                plan = FixedPlan.INSTALLMENTS, totalInstallments = 6, paidBefore = 5,
            ),
            FixedExpense(id = 4, name = "Elektrik", amount = tl(900), dayOfMonth = 10, kind = FixedExpenseKind.BILL),
            rent,
        )
        val payments = listOf(Expense(date = LocalDate(2026, 10, 1), grossAmount = tl(15_000), fixedExpenseId = 1))

        val events = forecast(null, incomes = emptyList(), fixed = fixed, payments = payments).days.flatMap { it.events }

        assertEquals(
            listOf(
                LocalDate(2026, 10, 20) to "Elektrik (günü geçti)",
                LocalDate(2026, 10, 22) to "Ablama borç", // son taksit; Kasım'da yok
                LocalDate(2026, 11, 1) to "Kira", // Ekim'deki ödendi
                LocalDate(2026, 11, 10) to "Elektrik",
            ),
            events.map { it.date to it.title },
        )
    }

    @Test
    fun flexibleDebt_neverPaysMoreThanWhatIsLeft() {
        val eminevim = FixedExpense(
            id = 5, name = "Eminevim", amount = tl(10_000), dayOfMonth = 25,
            plan = FixedPlan.FLEXIBLE, debt = tl(8_000),
        )
        val events = forecast(null, incomes = emptyList(), fixed = listOf(eminevim)).days.flatMap { it.events }

        // Genelde 10.000 ödeniyor ama 8.000 kaldı.
        assertEquals(listOf(LocalDate(2026, 10, 25)), events.map { it.date })
        assertEquals(tl(-8_000), events.single().amount)
    }

    @Test
    fun withoutBalance_onlyEventsNoVerdict() {
        val f = forecast(null, statements = listOf(cardDue(LocalDate(2026, 10, 26), 6_000)))
        assertTrue(f.days.all { it.balance == null })
        assertNull(f.firstShortfall)
        assertNull(f.dailyCutNeeded)
    }

    @Test
    fun creditCardsWithoutCurrentStatement_areCounted_debitIgnored() {
        val cards = listOf(
            qnb,
            Card(id = 2, name = "Axess", carriedBalance = tl(0)),
            Card(id = 3, name = "Akbank Kart", carriedBalance = tl(0), type = CardType.DEBIT),
        )
        val f = forecast(tl(1_000), cards = cards, statements = listOf(cardDue(LocalDate(2026, 10, 26), 100)))
        assertEquals(1, f.creditCardsWithoutStatement)
    }
}
