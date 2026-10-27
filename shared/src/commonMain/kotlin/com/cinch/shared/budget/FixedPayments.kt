package com.cinch.shared.budget

import com.cinch.shared.domain.Expense
import com.cinch.shared.domain.FixedExpense
import com.cinch.shared.domain.FixedPlan
import com.cinch.shared.domain.repository.ExpenseRepository
import com.cinch.shared.domain.repository.FixedExpenseRepository
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

enum class FixedPaymentState {
    PAID,

    /** Ödeme günü bu ay henüz gelmedi. */
    UPCOMING,
    DUE_TODAY,

    /** Ödeme günü geçti ama "ödendi" işaretlenmedi. */
    OVERDUE,
}

/**
 * Taksitli ya da esnek bir borcun ilerlemesi; geçmiş ödemelerden hesaplanır, ayrıca saklanmaz.
 *
 * @param paidTotal uygulamada işaretlenen ödemelerin toplamı.
 * @param paidCount taksitli: ödenen taksit (önceden ödenenler dahil).
 * @param remainingDebt taksitli: kalan taksit × taksit tutarı; esnek: başlangıç borcu − ödenenler (en az 0).
 * @param startDebt taksitli: toplam taksit × tutar; esnek: eklendiği andaki borç.
 * @param monthsLeft kaç ay kaldı; esnek borçta tahmin, tahmin için tutar yoksa null.
 * @param endDate son ödemenin (tahmini) tarihi; bittiyse son ödemenin tarihi.
 * @param estimateBase esnek: tahminde kullanılan aylık ödeme ("genelde ödediğim" ya da son 3 ödemenin ortalaması).
 */
data class DebtProgress(
    val plan: FixedPlan,
    val paidTotal: Money,
    val paidCount: Int?,
    val totalCount: Int?,
    val remainingDebt: Money,
    val startDebt: Money,
    val isFinished: Boolean,
    val monthsLeft: Int?,
    val endDate: LocalDate?,
    val estimateBase: Money?,
) {
    val remainingCount: Int? get() = if (totalCount != null && paidCount != null) totalCount - paidCount else null

    /** Ödenen oranı (0–1), ilerleme çubuğu için. */
    val fraction: Float
        get() = when {
            isFinished -> 1f
            plan == FixedPlan.INSTALLMENTS && totalCount != null && totalCount > 0 ->
                (paidCount ?: 0).toFloat() / totalCount
            startDebt.minor > 0 -> (1f - remainingDebt.minor.toFloat() / startDebt.minor).coerceIn(0f, 1f)
            else -> 0f
        }
}

/**
 * Borcun ilerlemesi; her ay tekrarlayan giderde null.
 *
 * @param payments bu sabit gidere ait tüm ödemeler (her ay, tarih sırasıyla).
 */
fun FixedExpense.progress(payments: List<Expense>, today: LocalDate): DebtProgress? {
    if (plan == FixedPlan.MONTHLY) return null
    val own = payments.filter { it.fixedExpenseId == id && it.grossAmount.currency == currency }.sortedBy { it.date }
    val paidTotal = Money.sum(own.map { it.grossAmount }, currency)
    val paidThisMonth = own.any { it.date.year == today.year && it.date.month == today.month }

    return when (plan) {
        FixedPlan.INSTALLMENTS -> {
            val total = (totalInstallments ?: 0).coerceAtLeast(0)
            val paidCount = (paidBefore + own.size).coerceAtMost(total)
            val remaining = total - paidCount
            DebtProgress(
                plan = plan,
                paidTotal = paidTotal,
                paidCount = paidCount,
                totalCount = total,
                remainingDebt = amount.times(remaining),
                startDebt = amount.times(total),
                isFinished = remaining == 0,
                monthsLeft = remaining,
                endDate = endDate(remaining, paidThisMonth, own, today),
                estimateBase = amount,
            )
        }
        FixedPlan.FLEXIBLE -> {
            val start = debt ?: Money.zero(currency)
            val remaining = (start - paidTotal).let { if (it.isNegative) Money.zero(currency) else it }
            val finished = remaining.isZero && !start.isZero
            // Tahmin: kullanıcı "genelde ödediğim" dediyse o, yoksa son 3 ödemenin ortalaması.
            val base = amount.takeIf { !it.isZero }
                ?: own.takeLast(3).takeIf { it.isNotEmpty() }?.let { last ->
                    Money(last.sumOf { it.grossAmount.minor } / last.size, currency)
                }
            val monthsLeft = when {
                finished -> 0
                base == null || base.isZero -> null
                else -> ((remaining.minor + base.minor - 1) / base.minor).toInt()
            }
            DebtProgress(
                plan = plan,
                paidTotal = paidTotal,
                paidCount = null,
                totalCount = null,
                remainingDebt = remaining,
                startDebt = start,
                isFinished = finished,
                monthsLeft = monthsLeft,
                endDate = monthsLeft?.let { endDate(it, paidThisMonth, own, today) },
                estimateBase = base,
            )
        }
        FixedPlan.MONTHLY -> null
    }
}

/** Son ödemenin tarihi: bittiyse gerçek son ödeme; değilse bu ay (ödenmediyse) ya da gelecek aydan sayılır. */
private fun FixedExpense.endDate(monthsLeft: Int, paidThisMonth: Boolean, own: List<Expense>, today: LocalDate): LocalDate? {
    if (monthsLeft <= 0) return own.lastOrNull()?.date
    val firstMonth = if (paidThisMonth) today.plus(DatePeriod(months = 1)) else today
    return dueDateIn(firstMonth.plus(DatePeriod(months = monthsLeft - 1)))
}

private fun Money.times(count: Int): Money = Money(minor * count, currency)

/**
 * Bir sabit giderin bu ayki durumu.
 *
 * @param paymentsThisMonth bu ay işaretlenen ödemeler (esnek borçta birden çok olabilir).
 * @param progress taksitli/esnek borcun ilerlemesi; her ay tekrarlayanda null.
 * @param isArchived borç bu aydan önce bitti: "Biten borçlar"da durur, ay hesabına ve hatırlatmalara girmez.
 */
data class FixedStatus(
    val fixed: FixedExpense,
    val dueDate: LocalDate,
    val paymentsThisMonth: List<Expense>,
    val state: FixedPaymentState,
    val progress: DebtProgress?,
    val isArchived: Boolean,
) {
    /** Bu ayın son ödemesi; "geri al" bunu siler. */
    val payment: Expense? get() = paymentsThisMonth.lastOrNull()

    val isFinished: Boolean get() = progress?.isFinished == true

    /** Ödenmeden önce ay hesabına yazılan tahmini tutar. Esnek borçta kalan borcu aşmaz. */
    val plannedAmount: Money
        get() = when {
            fixed.plan == FixedPlan.FLEXIBLE && progress != null -> minOf(fixed.amount, progress.remainingDebt)
            else -> fixed.amount
        }

    /** Ay hesabına giren tutar: ödendiyse gerçekte ödenen, bittiyse sıfır, değilse tahmini. */
    val amountForMonth: Money
        get() = when {
            paymentsThisMonth.isNotEmpty() -> Money.sum(paymentsThisMonth.map { it.grossAmount }, fixed.currency)
            isArchived || isFinished -> Money.zero(fixed.currency)
            else -> plannedAmount
        }
}

/**
 * Sabit giderlerin [today] ayındaki durumları, ödeme günü sırasıyla.
 *
 * @param payments sabit gider ödemeleri (en az bu ayınkiler; borç ilerlemesi için tümü).
 */
fun fixedStatuses(fixed: List<FixedExpense>, payments: List<Expense>, today: LocalDate): List<FixedStatus> =
    fixed.map { item ->
        val due = item.dueDateIn(today)
        val thisMonth = payments.filter {
            it.fixedExpenseId == item.id && it.date.year == today.year && it.date.month == today.month
        }
        val progress = item.progress(payments, today)
        val state = when {
            thisMonth.isNotEmpty() -> FixedPaymentState.PAID
            due == today -> FixedPaymentState.DUE_TODAY
            due < today -> FixedPaymentState.OVERDUE
            else -> FixedPaymentState.UPCOMING
        }
        FixedStatus(
            fixed = item,
            dueDate = due,
            paymentsThisMonth = thisMonth,
            state = state,
            progress = progress,
            isArchived = progress?.isFinished == true && thisMonth.isEmpty(),
        )
    }.sortedBy { it.dueDate }

/**
 * "Ödendi" işareti: sabit gidere bağlı bir harcama kaydı oluşturur (kartla ödeniyorsa o karta).
 * Bu kayıt haftalık paya sayılmaz; ay hesabına ve kart karşılaştırmasına girer.
 */
class FixedPayments(private val expenses: ExpenseRepository) {

    /** @param amount gerçekte ödenen (faturada ya da esnek borçta planlanandan farklı olabilir). */
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

/** Haftalık sınır önerisi için: bitmemiş sabit giderlerin aylık tahmini toplamı. */
class ActiveFixedExpenses(
    private val fixedExpenses: FixedExpenseRepository,
    private val expenses: ExpenseRepository,
) {
    suspend fun plannedMonthlyTotal(today: LocalDate, currency: Currency): Money {
        val statuses = fixedStatuses(
            fixedExpenses.all().filter { it.currency == currency },
            expenses.fixedPayments(),
            today,
        )
        return Money.sum(statuses.filterNot { it.isArchived || it.isFinished }.map { it.plannedAmount }, currency)
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
