package com.cinch.shared.budget

import com.cinch.shared.domain.Expense
import com.cinch.shared.domain.FixedExpense
import com.cinch.shared.domain.FixedPlan
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DebtProgressTest {

    private val today = LocalDate(2025, 1, 20)
    private fun tl(units: Long) = Money.of(units, Currency.TRY)
    private fun pay(date: LocalDate, units: Long, fixedId: Long = 1) =
        Expense(date = date, grossAmount = tl(units), fixedExpenseId = fixedId)

    private val sister = FixedExpense(
        id = 1, name = "Ablama borç", amount = tl(5_000), dayOfMonth = 10,
        plan = FixedPlan.INSTALLMENTS, totalInstallments = 6, paidBefore = 4,
    )
    private val eminevim = FixedExpense(
        id = 1, name = "Eminevim", amount = tl(10_000), dayOfMonth = 5,
        plan = FixedPlan.FLEXIBLE, debt = tl(100_000),
    )

    @Test
    fun monthly_hasNoProgress() {
        assertNull(FixedExpense(name = "Kira", amount = tl(15_000), dayOfMonth = 1).progress(emptyList(), today))
    }

    @Test
    fun installments_countPaidBeforeAndTrackedPayments() {
        val p = sister.progress(listOf(pay(LocalDate(2025, 1, 10), 5_000)), today)!!

        assertEquals(5, p.paidCount)
        assertEquals(1, p.remainingCount)
        assertEquals(tl(5_000), p.remainingDebt)
        assertFalse(p.isFinished)
        // Bu ayki ödendi: son taksit gelecek ay.
        assertEquals(LocalDate(2025, 2, 10), p.endDate)
    }

    @Test
    fun installments_finishAfterLastPayment() {
        val p = sister.progress(listOf(pay(LocalDate(2024, 12, 10), 5_000), pay(LocalDate(2025, 1, 10), 5_000)), today)!!

        assertTrue(p.isFinished)
        assertEquals(tl(0), p.remainingDebt)
        assertEquals(LocalDate(2025, 1, 10), p.endDate)
        assertEquals(1f, p.fraction)
    }

    @Test
    fun flexible_variablePaymentsReduceDebt_andEstimateUsesUsualAmount() {
        val payments = listOf(pay(LocalDate(2024, 11, 5), 25_000), pay(LocalDate(2024, 12, 5), 2_400), pay(LocalDate(2025, 1, 5), 30_000))
        val p = eminevim.progress(payments, today)!!

        assertEquals(tl(57_400), p.paidTotal)
        assertEquals(tl(42_600), p.remainingDebt)
        assertEquals(5, p.monthsLeft) // 42.600 / 10.000 → 5 ay
        assertEquals(LocalDate(2025, 6, 5), p.endDate) // bu ayki ödendi: Şubat'tan 5 ay
    }

    @Test
    fun flexible_withoutUsualAmount_estimatesFromLastThreePayments() {
        val noUsual = eminevim.copy(amount = tl(0))
        val payments = listOf(pay(LocalDate(2024, 11, 5), 25_000), pay(LocalDate(2024, 12, 5), 2_400), pay(LocalDate(2025, 1, 5), 30_000))
        val p = noUsual.progress(payments, today)!!

        assertEquals(Money(1_913_333, Currency.TRY), p.estimateBase) // (25.000 + 2.400 + 30.000) / 3
        assertEquals(3, p.monthsLeft)
    }

    @Test
    fun flexible_noUsualAmountNoPayments_hasNoEstimate() {
        val p = eminevim.copy(amount = tl(0)).progress(emptyList(), today)!!
        assertNull(p.monthsLeft)
        assertNull(p.endDate)
    }

    @Test
    fun flexible_finishesWhenDebtPaidOff_andIsArchivedNextMonth() {
        val payments = listOf(pay(LocalDate(2025, 1, 5), 100_000))
        assertTrue(eminevim.progress(payments, today)!!.isFinished)

        val thisMonth = fixedStatuses(listOf(eminevim), payments, today).single()
        assertFalse(thisMonth.isArchived) // bitiren ödeme bu ay: listede "bitti" olarak durur
        assertEquals(tl(100_000), thisMonth.amountForMonth)

        val nextMonth = fixedStatuses(listOf(eminevim), payments, LocalDate(2025, 2, 3)).single()
        assertTrue(nextMonth.isArchived)
        assertEquals(tl(0), nextMonth.amountForMonth)
    }

    @Test
    fun flexible_plannedAmountNeverExceedsRemainingDebt() {
        val payments = listOf(pay(LocalDate(2024, 12, 5), 95_000))
        val status = fixedStatuses(listOf(eminevim), payments, today).single()
        assertEquals(tl(5_000), status.plannedAmount)
    }
}
