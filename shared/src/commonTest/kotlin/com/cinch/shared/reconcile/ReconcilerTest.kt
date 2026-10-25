package com.cinch.shared.reconcile

import com.cinch.shared.domain.Expense
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ReconcilerTest {

    private val reconciler = Reconciler()
    private fun t(units: Long) = Money.of(units, Currency.TRY)

    private fun cardExp(units: Long, fraction: Double = 1.0) = Expense(
        date = LocalDate(2025, 1, 10),
        grossAmount = t(units),
        chargeableFraction = fraction,
        cardId = 1,
    )

    @Test
    fun missingExpense_detectsForgottenAmount() {
        // Kayıt: 100 + 200 = 300; gerçek borç 403 → 103 eksik
        val r = reconciler.reconcile(listOf(cardExp(100), cardExp(200)), statedActual = t(403))
        assertEquals(ReconciliationKind.MISSING_EXPENSE, r.kind)
        assertEquals(30000, r.recordedTotal.minor)
        assertEquals(10300, r.difference.minor) // 103 TRY
    }

    @Test
    fun extraRecorded() {
        val r = reconciler.reconcile(listOf(cardExp(100), cardExp(200)), statedActual = t(250))
        assertEquals(ReconciliationKind.EXTRA_RECORDED, r.kind)
        assertEquals(-5000, r.difference.minor)
    }

    @Test
    fun match() {
        val r = reconciler.reconcile(listOf(cardExp(100), cardExp(200)), statedActual = t(300))
        assertEquals(ReconciliationKind.MATCH, r.kind)
        assertEquals(0, r.difference.minor)
    }

    @Test
    fun usesGrossNotShare() {
        // Paylaşılan harcama bile karta brüt yansır
        val r = reconciler.reconcile(listOf(cardExp(200, fraction = 0.5)), statedActual = t(200))
        assertEquals(ReconciliationKind.MATCH, r.kind)
        assertEquals(20000, r.recordedTotal.minor)
    }

    @Test
    fun tolerance() {
        val r = reconciler.reconcile(
            listOf(cardExp(100)),
            statedActual = t(101),
            tolerance = t(2),
        )
        assertEquals(ReconciliationKind.MATCH, r.kind)
    }
}
