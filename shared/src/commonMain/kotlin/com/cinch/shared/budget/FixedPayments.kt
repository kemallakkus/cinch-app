package com.cinch.shared.budget

import com.cinch.shared.domain.Expense
import com.cinch.shared.domain.FixedExpense
import com.cinch.shared.domain.repository.ExpenseRepository
import com.cinch.shared.money.Money
import kotlinx.datetime.LocalDate

enum class FixedPaymentState {
    PAID,

    /** Ödeme günü bu ay henüz gelmedi. */
    UPCOMING,
    DUE_TODAY,

    /** Ödeme günü geçti ama "ödendi" işaretlenmedi. */
    OVERDUE,
}

/**
 * Bir sabit giderin bu ayki durumu.
 *
 * @param payment bu ay işaretlenen ödeme kaydı; yoksa null.
 */
data class FixedStatus(
    val fixed: FixedExpense,
    val dueDate: LocalDate,
    val payment: Expense?,
    val state: FixedPaymentState,
) {
    /** Ay hesabına giren tutar: ödendiyse gerçekte ödenen, değilse planlanan. */
    val amountForMonth: Money get() = payment?.grossAmount ?: fixed.amount
}

/**
 * Sabit giderlerin [today] ayındaki durumları, ödeme günü sırasıyla.
 *
 * @param monthExpenses bu ayın harcamaları; içlerinden sabit gider ödemeleri eşleştirilir.
 */
fun fixedStatuses(fixed: List<FixedExpense>, monthExpenses: List<Expense>, today: LocalDate): List<FixedStatus> =
    fixed.map { item ->
        val due = item.dueDateIn(today)
        val payment = monthExpenses.firstOrNull { it.fixedExpenseId == item.id }
        val state = when {
            payment != null -> FixedPaymentState.PAID
            due == today -> FixedPaymentState.DUE_TODAY
            due < today -> FixedPaymentState.OVERDUE
            else -> FixedPaymentState.UPCOMING
        }
        FixedStatus(item, due, payment, state)
    }.sortedBy { it.dueDate }

/**
 * "Ödendi" işareti: sabit gidere bağlı bir harcama kaydı oluşturur (kartla ödeniyorsa o karta).
 * Bu kayıt haftalık paya sayılmaz; ay hesabına ve kart karşılaştırmasına girer.
 */
class FixedPayments(private val expenses: ExpenseRepository) {

    /** @param amount gerçekte ödenen (faturalarda planlanandan farklı olabilir). */
    suspend fun markPaid(fixed: FixedExpense, amount: Money, date: LocalDate): Long =
        expenses.add(
            Expense(
                date = date,
                grossAmount = amount,
                category = fixed.kind.category,
                cardId = fixed.cardId,
                rawText = fixed.name,
                fixedExpenseId = fixed.id,
            ),
        )

    /** Yanlışlıkla işaretlenen ödemeyi geri alır (kaydı siler). */
    suspend fun undo(payment: Expense) {
        expenses.delete(payment.id)
    }
}

/** Gelir ve sabit giderlerden haftalık serbest harcama sınırı önerisi. */
object WeeklyLimitSuggestion {

    /** Bir ayda ortalama kaç hafta var (365,25 / 12 / 7 ≈ 4,35). */
    private const val WEEKS_PER_MONTH = 365.25 / 12.0 / 7.0

    /**
     * (aylık gelir − sabit giderler) / ayın hafta sayısı, 50'lik birime aşağı yuvarlanmış.
     * Gelir yoksa ya da sabit giderler geliri karşılamıyorsa null.
     */
    fun suggest(monthlyIncome: Money, monthlyFixed: Money): Money? {
        require(monthlyIncome.currency == monthlyFixed.currency) { "Para birimleri uyuşmuyor" }
        val free = monthlyIncome - monthlyFixed
        if (monthlyIncome.isZero || free.isNegative || free.isZero) return null
        val currency = monthlyIncome.currency
        val step = 50 * currency.minorPerUnit
        val weekly = (free.minor / WEEKS_PER_MONTH).toLong() / step * step
        return if (weekly <= 0) null else Money(weekly, currency)
    }
}
