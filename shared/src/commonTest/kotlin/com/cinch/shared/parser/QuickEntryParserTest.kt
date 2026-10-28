package com.cinch.shared.parser

import com.cinch.shared.domain.Category
import com.cinch.shared.money.Currency
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuickEntryParserTest {

    private val parser = QuickEntryParser()
    private val today = LocalDate(2025, 1, 15) // Çarşamba

    @Test
    fun simpleEntry_amountAndCategory() {
        val r = parser.parse("195 market", today)
        assertEquals(19500, r.grossAmount?.minor)
        assertEquals(Category.MARKET, r.category)
        assertEquals(1.0, r.chargeableFraction)
        assertEquals(today, r.date)
    }

    @Test
    fun sharedReimbursed_halfCounts() {
        val r = parser.parse("625 yemek qnb sanal yarısı geri geldi", today)
        assertEquals(62500, r.grossAmount?.minor)
        assertEquals(0.5, r.chargeableFraction)
        assertEquals(31250, r.chargeableAmount?.minor)
        assertEquals(Category.YEMEK, r.category)
    }

    @Test
    fun relativeDate_yesterday() {
        val r = parser.parse("dün 120 su ekmek", today)
        assertEquals(LocalDate(2025, 1, 14), r.date)
        assertEquals(12000, r.grossAmount?.minor)
        assertEquals(Category.YEMEK, r.category)
    }

    @Test
    fun relativeDate_dayBefore() {
        val r = parser.parse("önceki gün 50 taksi", today)
        assertEquals(LocalDate(2025, 1, 13), r.date)
        assertEquals(Category.ULASIM, r.category)
    }

    @Test
    fun turkishDecimalComma() {
        val r = parser.parse("120,50 kahve", today)
        assertEquals(12050, r.grossAmount?.minor)
        assertEquals(Category.YEMEK, r.category)
    }

    @Test
    fun sharedSplit_halfByWord() {
        val r = parser.parse("300 yemek yarı yarıya böldük", today)
        assertEquals(0.5, r.chargeableFraction)
    }

    @Test
    fun noAmount() {
        val r = parser.parse("market", today)
        assertNull(r.grossAmount)
        assertEquals(Category.MARKET, r.category)
    }

    @Test
    fun parseNumber_variants() {
        assertEquals(1250.0, parser.parseNumber("1.250"))
        assertEquals(120.5, parser.parseNumber("120,50"))
        assertEquals(120.5, parser.parseNumber("120.50"))
        assertEquals(1234.56, parser.parseNumber("1.234,56"))
        assertEquals(195.0, parser.parseNumber("195"))
    }

    @Test
    fun fullReimbursement_zeroShare() {
        val r = parser.parse("500 avans tamamı geri geldi", today)
        assertEquals(0.0, r.chargeableFraction)
    }

    @Test
    fun unknownWord_isDiger() {
        val r = parser.parse("90 zıpzıp", today)
        assertEquals(Category.DIGER, r.category)
        assertTrue(r.tokens.contains("zipzip"))
    }

    @Test
    fun weekday_isMostRecentPastOne() {
        val r = parser.parse("cuma 300 market", today)
        assertEquals(LocalDate(2025, 1, 10), r.date) // geçen cuma
        assertEquals(Category.MARKET, r.category)
        assertTrue("cuma" !in r.tokens)
    }

    @Test
    fun weekday_today_andGecenSkipsToday() {
        assertEquals(today, parser.parse("çarşamba 50 taksi", today).date)
        assertEquals(LocalDate(2025, 1, 8), parser.parse("geçen çarşamba 50 taksi", today).date)
        assertEquals(LocalDate(2025, 1, 14), parser.parse("salı 80 kahve", today).date)
    }

    @Test
    fun pazar_aloneIsTheMarket_withGunuIsSunday() {
        val bazaar = parser.parse("200 pazar", today)
        assertEquals(today, bazaar.date)
        assertTrue("pazar" in bazaar.tokens) // semt pazarı: kelime olarak kalır

        val sunday = parser.parse("pazar günü 200 market", today)
        assertEquals(LocalDate(2025, 1, 12), sunday.date)
        assertTrue("pazar" !in sunday.tokens)
    }
}
