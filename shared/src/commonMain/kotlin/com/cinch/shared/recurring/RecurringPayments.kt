package com.cinch.shared.recurring

import com.cinch.shared.budget.FixedMatcher
import com.cinch.shared.category.TextNormalizer
import com.cinch.shared.domain.Category
import com.cinch.shared.domain.Expense
import com.cinch.shared.domain.FixedExpense
import com.cinch.shared.domain.repository.ExpenseRepository
import com.cinch.shared.domain.repository.FixedExpenseRepository
import com.cinch.shared.money.Money
import com.cinch.shared.prefs.KeyValueStore
import com.cinch.shared.time.LocalClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.math.abs

/**
 * Her ay tekrar eden serbest harcama: abonelik adayı ("199 netflix" üç aydır her ayın 5'inde).
 *
 * @param key eşleştirme anahtarı: kaydın sade kelimeleri, sıralı ("netflix").
 * @param name görünen ad, son kaydın metninden ("Netflix").
 * @param lastAmount son ödenen; [previousAmount] bir önceki ay.
 * @param dayOfMonth genelde ödendiği gün.
 * @param months üst üste kaç ay görüldü.
 * @param expectedNext bir sonraki ödemenin beklendiği gün.
 * @param isLate beklenen gün birkaç gün geçti, hâlâ yok ("bu ay görünmedi: iptal mi ettin?").
 */
data class RecurringPayment(
    val key: String,
    val name: String,
    val category: Category,
    val cardId: Long?,
    val lastAmount: Money,
    val previousAmount: Money?,
    val dayOfMonth: Int,
    val lastDate: LocalDate,
    val months: Int,
    val expectedNext: LocalDate,
    val isLate: Boolean,
) {
    /** Son ödeme bir öncekinden belirgin farklıysa fark (artı = zam). */
    val priceChange: Money?
        get() {
            val previous = previousAmount ?: return null
            if (previous.currency != lastAmount.currency || previous.isZero) return null
            val diff = lastAmount - previous
            val significant = abs(diff.minor) > RecurringPayments.MIN_CHANGE_MINOR &&
                abs(diff.minor) * 100 >= previous.minor * RecurringPayments.MIN_CHANGE_PERCENT
            return diff.takeIf { significant }
        }
}

object RecurringPayments {

    /** Abonelik sayılmak için üst üste en az bu kadar ay. */
    const val MIN_MONTHS = 3

    /** Önceki ayların tutarı ortancadan en fazla bu kadar sapar (%); son ay zam olabilir, ayrıca bakılır. */
    private const val STABLE_PERCENT = 15

    /** Son ayın tutarı öncekilerin ortancasından en fazla bu kadar sapar (%): fazlası başka bir şeydir. */
    private const val LAST_PERCENT = 60

    /** Ödeme günleri ortanca günden en fazla bu kadar sapar. */
    private const val DAY_SLACK = 6

    /** Son görülme bundan eskiyse artık tekrar etmiyor sayılır. */
    const val ACTIVE_DAYS = 45

    /** Beklenen günden bu kadar gün sonra hâlâ yoksa "bu ay görünmedi". */
    const val LATE_DAYS = 5

    internal const val MIN_CHANGE_MINOR = 100L
    internal const val MIN_CHANGE_PERCENT = 3

    /** Geriye bakılan süre: üç ayı rahat kapsasın. */
    const val LOOKBACK_DAYS = 200

    /**
     * @param expenses son [LOOKBACK_DAYS] günün harcamaları; sabit gider ödemeleri yok sayılır.
     * @param fixed sabit giderler: adı eşleşen tekrar zaten takipte, önerilmez.
     * @param isIgnored kullanıcı "abonelik değil" dedi.
     */
    fun find(
        expenses: List<Expense>,
        today: LocalDate,
        fixed: List<FixedExpense> = emptyList(),
        isIgnored: (String) -> Boolean = { false },
    ): List<RecurringPayment> =
        expenses
            .filter { it.fixedExpenseId == null && it.date <= today }
            .mapNotNull { e -> keyOf(e)?.let { it to e } }
            .groupBy({ it.first }, { it.second })
            .mapNotNull { (key, list) -> detect(key, list, today) }
            .filterNot { isIgnored(it.key) }
            .filterNot { candidate -> fixed.any { FixedMatcher.nameMatches(candidate.key.split(' '), it.name) } }
            .sortedByDescending { it.lastAmount.minor }

    /** Sade kelimelerden anahtar; kelimesiz kayıt (yalnız tutar) tekrar sayılmaz. */
    fun keyOf(expense: Expense): String? =
        expense.tokens.filter { it.length >= 3 && it.none(Char::isDigit) }.distinct().sorted()
            .takeIf { it.isNotEmpty() }?.joinToString(" ")

    private fun detect(key: String, list: List<Expense>, today: LocalDate): RecurringPayment? {
        val currency = list.maxBy { it.date }.grossAmount.currency
        val sameCurrency = list.filter { it.grossAmount.currency == currency }
        val byMonth = sameCurrency.groupBy { it.date.year * 12 + it.date.month.ordinal }
        // Ayda birden çok kez (her hafta markete gitmek) abonelik değildir.
        if (byMonth.values.any { it.size > 1 }) return null

        // Son aydan geriye kesintisiz aylar.
        val months = byMonth.keys.sortedDescending()
        var run = 1
        while (run < months.size && months[run] == months[run - 1] - 1) run++
        if (run < MIN_MONTHS) return null
        val runExpenses = months.take(run).map { byMonth.getValue(it).single() }.sortedBy { it.date }

        val last = runExpenses.last()
        if (last.date.daysUntil(today) > ACTIVE_DAYS) return null

        val earlier = runExpenses.dropLast(1).map { it.grossAmount.minor }
        val median = earlier.sorted()[earlier.size / 2]
        if (median <= 0) return null
        if (earlier.any { abs(it - median) * 100 > median * STABLE_PERCENT }) return null
        if (abs(last.grossAmount.minor - median) * 100 > median * LAST_PERCENT) return null

        val days = runExpenses.map { it.date.day }
        val medianDay = days.sorted()[days.size / 2]
        if (days.any { abs(it - medianDay) > DAY_SLACK }) return null

        val expectedNext = last.date.plus(DatePeriod(months = 1))
        return RecurringPayment(
            key = key,
            name = displayName(last, key),
            category = last.category,
            cardId = last.cardId,
            lastAmount = last.grossAmount,
            previousAmount = runExpenses[runExpenses.size - 2].grossAmount,
            dayOfMonth = medianDay,
            lastDate = last.date,
            months = run,
            expectedNext = expectedNext,
            isLate = today > expectedNext.plus(DatePeriod(days = LATE_DAYS)),
        )
    }

    /**
     * "199 netflix qnb" → "Netflix": yalnız kaydın anlamlı kelimeleri (tutar, kart adı, "dün" gibi tarih kelimeleri
     * çıkar), yazıldığı biçimiyle; metin yoksa anahtar.
     */
    private fun displayName(expense: Expense, key: String): String {
        val words = TextNormalizer.tokenize(expense.rawText)
            .filter { w -> TextNormalizer.normalize(w) in expense.tokens }
            .distinctBy { TextNormalizer.normalize(it) }
        val text = words.joinToString(" ").ifBlank { key }
        return text.replaceFirstChar { it.uppercaseChar() }
    }

    /** Bugün baz alınarak bakılacak en eski gün. */
    fun lookbackStart(today: LocalDate): LocalDate = today.minus(DatePeriod(days = LOOKBACK_DAYS))
}

/** "Abonelik değil" denen tekrarlar; bir daha önerilmez. */
class RecurringDismissals(private val prefs: KeyValueStore) {

    private val version = MutableStateFlow(0)

    /** Değişince artar: izleyenler listeyi yeniden süzer. */
    val changes: StateFlow<Int> = version.asStateFlow()

    fun isDismissed(key: String): Boolean = prefs.getBoolean(prefKey(key))

    fun dismiss(key: String) {
        prefs.putBoolean(prefKey(key), true)
        version.value++
    }

    private fun prefKey(key: String) = "recurring_off_$key"
}

/** Son [RecurringPayments.LOOKBACK_DAYS] günün tekrarları; harcama, sabit gider ya da "abonelik değil" değişince yeniden. */
class ObserveRecurringPayments(
    private val expenses: ExpenseRepository,
    private val fixedExpenses: FixedExpenseRepository,
    private val dismissals: RecurringDismissals,
    private val clock: LocalClock,
) {
    operator fun invoke(): Flow<List<RecurringPayment>> {
        val today = clock.today()
        return combine(
            expenses.observeBetween(RecurringPayments.lookbackStart(today), today),
            fixedExpenses.observeAll(),
            dismissals.changes,
        ) { list, fixed, _ -> RecurringPayments.find(list, today, fixed, dismissals::isDismissed) }
    }
}
