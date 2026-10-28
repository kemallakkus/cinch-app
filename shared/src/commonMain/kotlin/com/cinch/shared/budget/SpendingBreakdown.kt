package com.cinch.shared.budget

import com.cinch.shared.category.TextNormalizer
import com.cinch.shared.domain.Category
import com.cinch.shared.domain.Expense
import com.cinch.shared.domain.repository.BudgetRepository
import com.cinch.shared.domain.repository.ExpenseRepository
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.shared.parser.minusDays
import com.cinch.shared.parser.plusDays
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Bir kategorinin dönemdeki harcaması.
 *
 * @param previous geçen ayın aynı dönemindeki harcama (kıyas için).
 * @param share dönem toplamındaki payı (0–1).
 */
data class CategorySpend(
    val category: Category,
    val amount: Money,
    val previous: Money,
    val count: Int,
    val share: Float,
) {
    /** Geçen döneme göre değişim (0.3 = %30 fazla, −1 = hiç harcanmadı); geçen dönem boşsa null. */
    val change: Double? get() = changeRatio(amount, previous)
}

/** Kayıtlarda en çok para giden kelime ("migros", "büfe", "taksi"). [word] kullanıcının yazdığı biçimde. */
data class TopWord(val word: String, val amount: Money, val count: Int)

/**
 * "Nereye gitti?": bir ayın serbest harcamalarının kategori dağılımı ve geçen ayla kıyası.
 * Sabit giderler (kira, fatura ödemeleri) "Bu ay" özetinde ayrıca durduğu için burada yok.
 *
 * İçinde bulunulan ay yarım olduğundan geçen ayın **aynı günleriyle** kıyaslanır (1–20 Ekim ↔ 1–20 Eylül);
 * biten bir ay geçen ayın tamamıyla kıyaslanır.
 *
 * @param categories harcaması olanlar büyükten küçüğe; ardından bu dönem hiç harcanmamış ama geçen
 *   dönemde harcanmış olanlar ("bu ay hiç gitmedi" de bir haber).
 */
data class SpendingBreakdown(
    val period: BreakdownPeriod,
    val total: Money,
    val previousTotal: Money,
    val count: Int,
    val categories: List<CategorySpend>,
    val topWords: List<TopWord>,
) {
    val isEmpty: Boolean get() = total.isZero && previousTotal.isZero

    /** Geçen döneme göre toplam değişim; geçen dönem boşsa null. */
    val change: Double? get() = changeRatio(total, previousTotal)

    /** Halka grafiğe girecekler: bu dönem harcaması olanlar. */
    val slices: List<CategorySpend> get() = categories.filter { !it.amount.isZero }
}

/**
 * @param start ayın ilk günü.
 * @param end içinde bulunulan ayda bugün, geçmiş ayda ay sonu.
 * @param previousStart geçen ayın ilk günü; [previousEnd] geçen ayın aynı günü (ay kısaysa son günü).
 */
data class BreakdownPeriod(
    val start: LocalDate,
    val end: LocalDate,
    val previousStart: LocalDate,
    val previousEnd: LocalDate,
    val isCurrentMonth: Boolean,
) {
    companion object {
        /** @param month ayın herhangi bir günü. Gelecek ay istenirse içinde bulunulan ay döner. */
        fun of(month: LocalDate, today: LocalDate): BreakdownPeriod {
            val thisMonth = LocalDate(today.year, today.month, 1)
            val start = minOf(LocalDate(month.year, month.month, 1), thisMonth)
            val monthEnd = start.plus(DatePeriod(months = 1)).minusDays(1)
            val previousStart = start.minus(DatePeriod(months = 1))
            val current = start == thisMonth
            return if (current) {
                val previousMonthEnd = start.minusDays(1)
                BreakdownPeriod(
                    start = start,
                    end = today,
                    previousStart = previousStart,
                    previousEnd = minOf(previousStart.plusDays(today.day - 1), previousMonthEnd),
                    isCurrentMonth = true,
                )
            } else {
                BreakdownPeriod(start, monthEnd, previousStart, start.minusDays(1), isCurrentMonth = false)
            }
        }
    }
}

object SpendingBreakdowns {

    private const val TOP_WORD_COUNT = 5

    /** Kategori sayılmayacak kadar genel kelimeler. */
    private val STOPWORDS = setOf("ile", "icin", "bir", "iki", "uc", "ve", "de", "da", "kart", "nakit")

    /**
     * @param expenses en az [BreakdownPeriod.previousStart]..[BreakdownPeriod.end] aralığını kapsayan kayıtlar;
     *   aralık dışındakiler, sabit gider ödemeleri ve [currency] dışındakiler yok sayılır.
     */
    fun compute(expenses: List<Expense>, period: BreakdownPeriod, currency: Currency): SpendingBreakdown {
        val usable = expenses.filter { it.grossAmount.currency == currency }.discretionary()
        val current = usable.filter { it.date in period.start..period.end }
        val previous = usable.filter { it.date in period.previousStart..period.previousEnd }

        val total = Money.sum(current.map { it.chargeableAmount }, currency)
        val previousTotal = Money.sum(previous.map { it.chargeableAmount }, currency)
        val nowByCategory = current.groupBy { it.category }
        val beforeByCategory = previous.groupBy { it.category }

        val categories = (nowByCategory.keys + beforeByCategory.keys).map { category ->
            val now = nowByCategory[category].orEmpty()
            val amount = Money.sum(now.map { it.chargeableAmount }, currency)
            CategorySpend(
                category = category,
                amount = amount,
                previous = Money.sum(beforeByCategory[category].orEmpty().map { it.chargeableAmount }, currency),
                count = now.size,
                share = if (total.isZero) 0f else (amount.minor.toDouble() / total.minor).toFloat(),
            )
        }.sortedWith(compareByDescending<CategorySpend> { it.amount.minor }.thenByDescending { it.previous.minor })

        return SpendingBreakdown(
            period = period,
            total = total,
            previousTotal = previousTotal,
            count = current.size,
            categories = categories,
            topWords = topWords(current, currency),
        )
    }

    /** Aynı kayıtta iki kelime varsa ("migros market") tutar ikisine de yazılır. */
    private fun topWords(expenses: List<Expense>, currency: Currency): List<TopWord> {
        data class Acc(var display: String, var minor: Long = 0, var count: Int = 0)

        val byToken = linkedMapOf<String, Acc>()
        for (expense in expenses) {
            val originals = TextNormalizer.tokenize(expense.rawText)
            for (token in expense.tokens.toSet()) {
                if (token.length < 3 || token in STOPWORDS || token.any { it.isDigit() }) continue
                val display = originals.firstOrNull { TextNormalizer.normalize(it) == token } ?: token
                val acc = byToken.getOrPut(token) { Acc(display) }
                acc.minor += expense.chargeableAmount.minor
                acc.count++
            }
        }
        return byToken.values
            .sortedWith(compareByDescending<Acc> { it.minor }.thenByDescending { it.count })
            .take(TOP_WORD_COUNT)
            .map { TopWord(it.display, Money(it.minor, currency), it.count) }
    }
}

internal fun changeRatio(now: Money, before: Money): Double? =
    if (before.isZero) null else (now.minor - before.minor).toDouble() / before.minor

/** Bir ayın harcama dağılımını izler; kayıt eklenip düzenlendikçe yeniden hesaplanır. */
@OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest
class ObserveSpendingBreakdown(
    private val budgets: BudgetRepository,
    private val expenses: ExpenseRepository,
) {
    operator fun invoke(month: LocalDate, today: LocalDate): Flow<SpendingBreakdown> {
        val period = BreakdownPeriod.of(month, today)
        return budgets.observe()
            .map { it?.currency ?: Currency.TRY }
            .distinctUntilChanged()
            .flatMapLatest { currency ->
                expenses.observeBetween(period.previousStart, period.end)
                    .map { SpendingBreakdowns.compute(it, period, currency) }
            }
    }
}
