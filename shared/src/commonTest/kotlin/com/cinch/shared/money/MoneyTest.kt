package com.cinch.shared.money

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MoneyTest {

    @Test
    fun of_createsMinorUnits() {
        assertEquals(19500, Money.of(195, Currency.TRY).minor)
        assertEquals(12050, Money.ofDouble(120.50, Currency.TRY).minor)
    }

    @Test
    fun arithmetic() {
        val a = Money.of(100, Currency.TRY)
        val b = Money.of(30, Currency.TRY)
        assertEquals(13000, (a + b).minor)
        assertEquals(7000, (a - b).minor)
        assertEquals(5000, (a * 0.5).minor)
    }

    @Test
    fun times_roundsHalfUp() {
        // 625,00 * 0.5 = 312,50
        assertEquals(31250, (Money.of(625, Currency.TRY) * 0.5).minor)
        // 100,01 * (1/3) → 33,34 (yuvarlama)
        assertEquals(3334, (Money(10001, Currency.TRY) * (1.0 / 3.0)).minor)
    }

    @Test
    fun comparison_andFlags() {
        assertTrue(Money.of(10, Currency.TRY) > Money.of(5, Currency.TRY))
        assertTrue(Money(-1, Currency.TRY).isNegative)
        assertTrue(Money.zero(Currency.TRY).isZero)
        assertEquals(500, Money(-500, Currency.TRY).absolute.minor)
    }

    @Test
    fun mixingCurrencies_throws() {
        assertFailsWith<IllegalArgumentException> {
            Money.of(1, Currency.TRY) + Money.of(1, Currency.USD)
        }
    }

    @Test
    fun sum_ofList() {
        val list = listOf(Money.of(10, Currency.TRY), Money.of(20, Currency.TRY))
        assertEquals(3000, Money.sum(list, Currency.TRY).minor)
        assertEquals(0, Money.sum(emptyList(), Currency.TRY).minor)
    }
}
