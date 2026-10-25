package com.cinch.shared.debt

import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PayoffSimulatorTest {

    private val sim = PayoffSimulator()
    private fun t(units: Long) = Money.of(units, Currency.TRY)

    @Test
    fun card_closesInExpectedMonths() {
        // 1000 TRY borç, aylık %4.9, her ay 300 TRY ödeme
        val r = sim.simulateCard(balance = t(1000), monthlyRatePercent = 4.9, fixedPayment = t(300))
        assertTrue(r.closes)
        assertEquals(4, r.months)
        assertEquals(11977, r.totalInterest.minor)
        assertEquals(111977, r.totalPaid.minor)
        assertEquals(0, r.schedule.last().remaining.minor)
    }

    @Test
    fun card_neverCloses_whenPaymentBelowInterest() {
        // İlk ay faizi 4900 kuruş; 40 TRY ödeme karşılamaz
        val r = sim.simulateCard(balance = t(1000), monthlyRatePercent = 4.9, fixedPayment = t(40))
        assertFalse(r.closes)
        assertEquals(0, r.months)
    }

    @Test
    fun card_zeroBalance() {
        val r = sim.simulateCard(balance = t(0), monthlyRatePercent = 4.9, fixedPayment = t(300))
        assertTrue(r.closes)
        assertEquals(0, r.months)
    }

    @Test
    fun installment_remaining() {
        val s = sim.installmentStatus(monthlyPayment = t(500), totalMonths = 12, paidMonths = 5)
        assertEquals(7, s.remainingMonths)
        assertEquals(350000, s.remainingBalance.minor)
    }

    @Test
    fun flexible_basePayment() {
        // 1000 TRY, taban 300 → 3 ay tam + 4. ay 100
        val r = sim.simulateFlexible(principal = t(1000), basePayment = t(300))
        assertEquals(4, r.months)
        assertEquals(10000, r.lastPayment.minor)
        assertEquals(100000, r.totalPaid.minor)
    }

    @Test
    fun flexible_withCustomPayments_shortfallPushesToEnd() {
        // İlk iki ay sadece 200 öde (fark sona sarkar), sonra taban 300
        val r = sim.simulateFlexible(
            principal = t(1000),
            basePayment = t(300),
            payments = listOf(t(200), t(200)),
        )
        // 200+200+300+300 = 1000 → 4 ay (fark sona sarktı ama yine 4 ayda bitti)
        assertEquals(4, r.months)
        assertEquals(100000, r.totalPaid.minor)
    }
}
