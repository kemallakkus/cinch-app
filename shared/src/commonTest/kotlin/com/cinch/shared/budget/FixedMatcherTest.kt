package com.cinch.shared.budget

import com.cinch.shared.domain.Expense
import com.cinch.shared.domain.FixedExpense
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FixedMatcherTest {

    private val day = LocalDate(2025, 3, 5)
    private fun tl(units: Long) = Money.of(units, Currency.TRY)

    private val rent = FixedExpense(id = 1, name = "Ev kirası", amount = tl(15_000), dayOfMonth = 1)
    private val netflix = FixedExpense(id = 2, name = "Netflix", amount = tl(199), dayOfMonth = 5)
    private val fixed = listOf(rent, netflix)

    private fun match(vararg tokens: String, payments: List<Expense> = emptyList()) =
        FixedMatcher.match(tokens.toList(), fixed, payments, day, Currency.TRY)

    @Test
    fun nameWordsMustAppear_turkishSuffixesTolerated() {
        assertEquals(rent, match("kira")) // "ev" kısa, sayılmaz; "kira" ↔ "kirasi"
        assertEquals(netflix, match("netflix"))
        assertNull(match("market"))
        assertNull(match("kir")) // 4 harften kısa önek eşleşmez
    }

    @Test
    fun alreadyPaidThisMonth_orOtherCurrency_doesNotMatch() {
        val paid = Expense(date = LocalDate(2025, 3, 1), grossAmount = tl(15_000), fixedExpenseId = rent.id)
        assertNull(match("kira", payments = listOf(paid)))
        val lastMonth = paid.copy(date = LocalDate(2025, 2, 1))
        assertEquals(rent, match("kira", payments = listOf(lastMonth)))
        assertNull(FixedMatcher.match(listOf("kira"), fixed, emptyList(), day, Currency.USD))
    }

    @Test
    fun ambiguous_noMatch() {
        val other = FixedExpense(id = 3, name = "Netflix aile", amount = tl(299), dayOfMonth = 9)
        assertNull(FixedMatcher.match(listOf("netflix", "aile"), fixed + other, emptyList(), day, Currency.TRY))
    }
}
