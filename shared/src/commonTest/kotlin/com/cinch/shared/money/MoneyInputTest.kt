package com.cinch.shared.money

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoneyInputTest {

    private fun parse(text: String) = MoneyInput.parse(text, Currency.TRY)

    @Test
    fun turkishNumberFormats() {
        assertEquals(Money.of(1500, Currency.TRY), parse("1.500"))
        assertEquals(Money(150_050, Currency.TRY), parse("1500,50"))
        assertEquals(Money(123_456, Currency.TRY), parse("1.234,56"))
        assertEquals(Money.of(700, Currency.TRY), parse("700"))
    }

    @Test
    fun ignoresSpacesAndSymbol() {
        assertEquals(Money.of(700, Currency.TRY), parse(" 700 ₺"))
        assertEquals(Money.of(700, Currency.TRY), parse("₺700"))
        assertEquals(Money.of(1500, Currency.TRY), parse("1 500"))
    }

    @Test
    fun invalidInputIsNull() {
        assertNull(parse(""))
        assertNull(parse("yüz"))
        assertNull(parse("-50"))
        assertNull(parse("12a"))
        assertNull(parse(",5"))
    }
}
