package com.cinch.shared.parser

import com.cinch.shared.domain.Category
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ParsedEntryToExpenseTest {

    private val parser = QuickEntryParser()
    private val today = LocalDate(2025, 1, 15)

    @Test
    fun carriesParsedFieldsIntoExpense() {
        val raw = "dün 120 su ekmek"
        val expense = parser.parse(raw, today).toExpense(cardId = 3)!!

        assertEquals(LocalDate(2025, 1, 14), expense.date)
        assertEquals(Money.of(120, Currency.TRY), expense.grossAmount)
        assertEquals(Category.YEMEK, expense.category)
        assertEquals(3L, expense.cardId)
        assertEquals(listOf("su", "ekmek"), expense.tokens) // tarih kelimesi ve sayı ayıklanır
        assertEquals(raw, expense.rawText)
    }

    @Test
    fun noAmount_returnsNull() {
        assertNull(parser.parse("market", today).toExpense())
    }
}
