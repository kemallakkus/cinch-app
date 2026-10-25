package com.cinch.shared.money

import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyFormatterTest {

    private val nbsp = " "

    @Test
    fun tr_format() {
        assertEquals("195,00$nbsp₺", MoneyFormatter.format(Money.of(195, Currency.TRY), AppLocale.TR))
        assertEquals("1.234,56$nbsp₺", MoneyFormatter.format(Money(123456, Currency.TRY), AppLocale.TR))
        assertEquals("12.345,67$nbsp₺", MoneyFormatter.format(Money(1234567, Currency.TRY), AppLocale.TR))
    }

    @Test
    fun en_format() {
        assertEquals("₺1,234.56", MoneyFormatter.format(Money(123456, Currency.TRY), AppLocale.EN))
        assertEquals("\$1,000.00", MoneyFormatter.format(Money.of(1000, Currency.USD), AppLocale.EN))
    }

    @Test
    fun negative_format() {
        assertEquals("-120,50$nbsp₺", MoneyFormatter.format(Money(-12050, Currency.TRY), AppLocale.TR))
        assertEquals("-₺120.50", MoneyFormatter.format(Money(-12050, Currency.TRY), AppLocale.EN))
    }

    @Test
    fun withoutSymbol() {
        assertEquals("1.000,00", MoneyFormatter.format(Money.of(1000, Currency.TRY), AppLocale.TR, withSymbol = false))
    }

    @Test
    fun localeFromTag() {
        assertEquals(AppLocale.TR, AppLocale.fromTag("tr-TR"))
        assertEquals(AppLocale.EN, AppLocale.fromTag("en-US"))
        assertEquals(AppLocale.TR, AppLocale.fromTag("de"))
    }
}
