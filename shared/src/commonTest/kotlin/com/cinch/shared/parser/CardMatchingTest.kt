package com.cinch.shared.parser

import com.cinch.shared.category.Categorizer
import com.cinch.shared.category.InMemoryCorrectionStore
import com.cinch.shared.domain.Card
import com.cinch.shared.domain.Category
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class CardMatchingTest {

    private val today = LocalDate(2025, 1, 13)
    private val parser = QuickEntryParser()
    private fun card(id: Long, name: String) = Card(id = id, name = name, carriedBalance = Money.zero(Currency.TRY))

    private val qnb = card(1, "QNB")
    private val axess = card(2, "Akbank Axess")
    private val wings = card(3, "Akbank Wings")

    @Test
    fun cardNameInEntry_setsCard() {
        val parsed = parser.parse("195 market qnb", today, cards = listOf(qnb, axess))
        assertEquals(1L, parsed.cardId)
        assertEquals(Category.MARKET, parsed.category)
    }

    @Test
    fun anyWordOfCardName_isEnough() {
        assertEquals(2L, parser.parse("80 taksi axess", today, cards = listOf(qnb, axess, wings)).cardId)
    }

    @Test
    fun ambiguousBankName_leavesCardEmpty() {
        assertNull(parser.parse("80 taksi akbank", today, cards = listOf(axess, wings)).cardId)
    }

    @Test
    fun cardWords_doNotCountAsCategoryWords() {
        val parsed = parser.parse("80 kırtasiye qnb", today, cards = listOf(qnb))
        assertFalse("qnb" in parsed.tokens)
        assertEquals(listOf("kirtasiye"), parsed.tokens)
    }

    @Test
    fun correctionAfterCardMatch_doesNotLearnCardName() {
        val corrections = InMemoryCorrectionStore()
        val categorizer = Categorizer(corrections = corrections)
        val parser = QuickEntryParser(categorizer)
        val parsed = parser.parse("80 kırtasiye qnb", today, cards = listOf(qnb))

        categorizer.learnFromCorrection(parsed.tokens, Category.EV)

        assertNull(corrections.categoryFor("qnb"))
        assertEquals(Category.EV, corrections.categoryFor("kirtasiye"))
    }

    @Test
    fun toExpense_carriesMatchedCard() {
        val expense = parser.parse("195 market qnb", today, cards = listOf(qnb)).toExpense()
        assertEquals(1L, expense?.cardId)
    }

    @Test
    fun withoutCards_nothingChanges() {
        val parsed = parser.parse("195 market qnb", today)
        assertNull(parsed.cardId)
        assertEquals(listOf("market", "qnb"), parsed.tokens)
    }
}
