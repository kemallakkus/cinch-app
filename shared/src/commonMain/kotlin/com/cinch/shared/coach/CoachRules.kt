package com.cinch.shared.coach

import com.cinch.shared.budget.BudgetStatus
import com.cinch.shared.domain.Category
import com.cinch.shared.domain.Expense
import com.cinch.shared.money.AppLocale
import com.cinch.shared.money.Money
import com.cinch.shared.money.MoneyFormatter
import com.cinch.shared.parser.minusDays
import com.cinch.shared.parser.plusDays
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlin.math.roundToInt

/** Koçluk bildirimi türü. */
enum class NotificationType {
    MIDDAY_REMAINING,   // gün ortası kalan pay
    FORGOT_LOG,         // kayıt unutma hatırlatması
    APPROACHING_LIMIT,  // sınıra yaklaşma
    WEEKLY_CLOSING,     // pazar haftalık kapanış
    HABIT_ALERT,        // alışkanlık uyarısı ("büfeye 21 kez gittin")
    STREAK,             // seri / kilometre taşı
    STATEMENT_DUE,      // ekstre günü yaklaşıyor
    SALARY,             // maaş günü
    FIXED_DUE,          // yarın sabit gider ödeme günü
    FIXED_UNPAID,       // bugün ödeme günü, henüz "ödendi" işaretlenmedi
    CATEGORY_SPIKE,     // bu hafta bir kategoriye geçen haftadan belirgin fazla gitti
}

/** Planlanmış tek bildirim. */
data class CoachNotification(
    val date: LocalDate,
    val time: LocalTime,
    val type: NotificationType,
    val priority: Int,
    val message: String,
)

/** Sessiz saatler. [start] > [end] ise gece boyu (ör. 22:00–08:00) olarak yorumlanır. */
data class QuietHours(val start: LocalTime, val end: LocalTime) {
    fun contains(time: LocalTime): Boolean =
        if (start <= end) time >= start && time < end
        else time >= start || time < end
}

/** Koçluk yapılandırması. */
data class CoachConfig(
    val locale: AppLocale = AppLocale.TR,
    val maxPerDay: Int = 2,
    val quietHours: QuietHours = QuietHours(LocalTime(22, 0), LocalTime(8, 0)),
    val middayTime: LocalTime = LocalTime(13, 0),
    val weeklyClosingTime: LocalTime = LocalTime(20, 0),
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val habitThreshold: Int = 10,
    val variantSeed: Int = 0,
)

/** Kural motorunun girdisi. */
data class CoachInput(
    val today: LocalDate,
    val budget: BudgetStatus,
    val expenses: List<Expense> = emptyList(),
    val statementDates: List<LocalDate> = emptyList(),
    val salaryDates: List<LocalDate> = emptyList(),
    val fixedDues: List<FixedDue> = emptyList(),
)

/**
 * Ufuktaki bir sabit gider ödeme günü. [paid] = o ayın ödemesi zaten işaretlendi.
 * [amount] null = tutar bilinmiyor (esnek borçta "genelde ödediğim" girilmemiş); mesajda tutar yazılmaz.
 */
data class FixedDue(val name: String, val amount: Money?, val date: LocalDate, val paid: Boolean)

/**
 * Her kayıttan sonra önümüzdeki 7 günün yerel bildirimlerini yeniden hesaplar.
 * Kurallar aday bildirim üretir; ardından sessiz saatler, günde en fazla [CoachConfig.maxPerDay]
 * ve cümle varyasyonu kısıtları uygulanır.
 */
class CoachRules(private val horizonDays: Int = 7) {

    fun planWeek(input: CoachInput, config: CoachConfig): List<CoachNotification> {
        val candidates = buildList {
            addAll(middayRemaining(input, config))
            addAll(approachingLimit(input, config))
            addAll(weeklyClosing(input, config))
            addAll(forgotLog(input, config))
            addAll(habitAlert(input, config))
            addAll(streak(input, config))
            addAll(statementDue(input, config))
            addAll(salary(input, config))
            addAll(fixedDue(input, config))
            addAll(fixedUnpaid(input, config))
            addAll(categorySpike(input, config))
        }
        return applyConstraints(candidates, config)
    }

    private fun horizon(today: LocalDate): List<LocalDate> =
        (0 until horizonDays).map { today.plusDays(it) }

    // ───────────────────────── Kurallar ─────────────────────────

    private fun middayRemaining(input: CoachInput, config: CoachConfig): List<CoachNotification> {
        val amount = MoneyFormatter.format(input.budget.dailyAllowance, config.locale)
        return horizon(input.today).map { date ->
            val msg = variant(
                config, NotificationType.MIDDAY_REMAINING, date,
                tr = listOf(
                    "Bugün $amount civarı harcayabilirsin.",
                    "Günlük payın bugün yaklaşık $amount.",
                    "Hedefte kalmak için bugünlük $amount var.",
                ),
                en = listOf(
                    "You can spend about $amount today.",
                    "Today's allowance is around $amount.",
                    "About $amount left for today to stay on track.",
                ),
            )
            CoachNotification(date, config.middayTime, NotificationType.MIDDAY_REMAINING, 50, msg)
        }
    }

    private fun approachingLimit(input: CoachInput, config: CoachConfig): List<CoachNotification> {
        if (input.budget.isOverLimit) return emptyList()
        if (input.budget.pacePercent < 0.8) return emptyList()
        val date = input.today.plusDays(1)
        val remaining = MoneyFormatter.format(input.budget.weekRemaining, config.locale)
        val msg = variant(
            config, NotificationType.APPROACHING_LIMIT, date,
            tr = listOf(
                "Haftalık sınıra yaklaşıyorsun, $remaining kaldı.",
                "Dikkat: bu hafta $remaining harcama hakkın var.",
            ),
            en = listOf(
                "You're close to your weekly limit — $remaining left.",
                "Heads up: $remaining left for this week.",
            ),
        )
        return listOf(CoachNotification(date, LocalTime(10, 0), NotificationType.APPROACHING_LIMIT, 90, msg))
    }

    private fun weeklyClosing(input: CoachInput, config: CoachConfig): List<CoachNotification> {
        val closingDay = horizon(input.today).firstOrNull {
            it.dayOfWeek.isoDayNumber == endOfWeekIso(config.weekStart)
        } ?: return emptyList()
        val spent = MoneyFormatter.format(input.budget.weekSpent, config.locale)
        val msg = variant(
            config, NotificationType.WEEKLY_CLOSING, closingDay,
            tr = listOf(
                "Haftayı kapatıyoruz: bu hafta $spent harcadın.",
                "Haftalık özet hazır — toplam $spent.",
            ),
            en = listOf(
                "Wrapping up the week: you spent $spent.",
                "Weekly summary ready — $spent total.",
            ),
        )
        return listOf(CoachNotification(closingDay, config.weeklyClosingTime, NotificationType.WEEKLY_CLOSING, 80, msg))
    }

    private fun forgotLog(input: CoachInput, config: CoachConfig): List<CoachNotification> {
        val loggedToday = input.expenses.any { it.date == input.today }
        if (loggedToday) return emptyList()
        val date = input.today.plusDays(1)
        val msg = variant(
            config, NotificationType.FORGOT_LOG, date,
            tr = listOf(
                "Dün harcama girmedin — bir şey atladın mı?",
                "Kayıt eklemeyi unutma, dün boş görünüyor.",
            ),
            en = listOf(
                "No entries yesterday — did you miss something?",
                "Don't forget to log; yesterday looks empty.",
            ),
        )
        return listOf(CoachNotification(date, LocalTime(20, 0), NotificationType.FORGOT_LOG, 40, msg))
    }

    private fun habitAlert(input: CoachInput, config: CoachConfig): List<CoachNotification> {
        val counts = mutableMapOf<String, Int>()
        for (e in input.expenses) {
            for (tok in e.tokens) {
                if (tok.length < 3) continue
                counts[tok] = (counts[tok] ?: 0) + 1
            }
        }
        val top = counts.maxByOrNull { it.value } ?: return emptyList()
        if (top.value < config.habitThreshold) return emptyList()
        val date = input.today.plusDays(2)
        val word = top.key
        val n = top.value
        val msg = when (config.locale) {
            AppLocale.TR -> "Bu dönem \"$word\" için $n kez harcama yaptın."
            AppLocale.EN -> "You've spent on \"$word\" $n times this period."
        }
        return listOf(CoachNotification(date, LocalTime(18, 0), NotificationType.HABIT_ALERT, 70, msg))
    }

    private fun streak(input: CoachInput, config: CoachConfig): List<CoachNotification> {
        val days = input.expenses.map { it.date }.toSet()
        var streak = 0
        var d = input.today
        while (d in days) {
            streak++
            d = d.minusDays(1)
        }
        if (streak < 3) return emptyList()
        val date = input.today.plusDays(1)
        val msg = when (config.locale) {
            AppLocale.TR -> "$streak gündür düzenli kayıt tutuyorsun, böyle devam!"
            AppLocale.EN -> "$streak-day logging streak — keep it up!"
        }
        return listOf(CoachNotification(date, LocalTime(9, 0), NotificationType.STREAK, 60, msg))
    }

    private fun statementDue(input: CoachInput, config: CoachConfig): List<CoachNotification> {
        val horizonSet = horizon(input.today).toSet()
        return input.statementDates
            .mapNotNull { stmt ->
                val notifyDay = stmt.minusDays(1)
                if (notifyDay !in horizonSet) return@mapNotNull null
                val msg = when (config.locale) {
                    AppLocale.TR -> "Yarın ekstre günü — kart harcamalarını gözden geçir."
                    AppLocale.EN -> "Statement day tomorrow — review your card spending."
                }
                CoachNotification(notifyDay, LocalTime(11, 0), NotificationType.STATEMENT_DUE, 85, msg)
            }
    }

    private fun salary(input: CoachInput, config: CoachConfig): List<CoachNotification> {
        val horizonSet = horizon(input.today).toSet()
        return input.salaryDates.filter { it in horizonSet }.map { day ->
            val msg = when (config.locale) {
                AppLocale.TR -> "Maaş günü! Bu haftanın planını gözden geçirelim mi?"
                AppLocale.EN -> "Payday! Want to review this week's plan?"
            }
            CoachNotification(day, LocalTime(9, 0), NotificationType.SALARY, 30, msg)
        }
    }

    /** Ödeme gününden bir gün önce; aynı gün birden çok ödeme varsa tek bildirimde toplanır. */
    private fun fixedDue(input: CoachInput, config: CoachConfig): List<CoachNotification> {
        val horizonSet = horizon(input.today).toSet()
        return input.fixedDues.filter { !it.paid }
            .groupBy { it.date.minusDays(1) }
            .filterKeys { it in horizonSet }
            .map { (notifyDay, dues) ->
                val list = dues.joinToString { due ->
                    due.amount?.let { "${due.name} ${MoneyFormatter.format(it, config.locale)}" } ?: due.name
                }
                val msg = when (config.locale) {
                    AppLocale.TR -> if (dues.size == 1) "Yarın ödeme günü: $list." else "Yarın ${dues.size} ödeme var: $list."
                    AppLocale.EN -> if (dues.size == 1) "Payment due tomorrow: $list." else "${dues.size} payments due tomorrow: $list."
                }
                CoachNotification(notifyDay, LocalTime(19, 0), NotificationType.FIXED_DUE, 75, msg)
            }
    }

    /** Ödeme günü akşamı, hâlâ işaretlenmemişse: "ödendi mi?" (ay hesabı doğru kalsın diye). */
    private fun fixedUnpaid(input: CoachInput, config: CoachConfig): List<CoachNotification> {
        val horizonSet = horizon(input.today).toSet()
        return input.fixedDues.filter { !it.paid && it.date in horizonSet }
            .groupBy { it.date }
            .map { (day, dues) ->
                val names = dues.joinToString { it.name }
                val msg = when (config.locale) {
                    AppLocale.TR -> "$names ödendi mi? Ödediysen Cinch'te işaretle, ay hesabın doğru kalsın."
                    AppLocale.EN -> "Did you pay $names? Mark it in Cinch to keep your month accurate."
                }
                CoachNotification(day, LocalTime(20, 30), NotificationType.FIXED_UNPAID, 65, msg)
            }
    }

    /**
     * Haftanın 5. günü (pazartesi başlayan haftada cuma) akşamı, hafta sonundan önce: bu hafta bir kategoriye
     * geçen haftanın aynı günlerinden en az %30 ve haftalık sınırın en az %10'u kadar fazla gittiyse.
     *
     * Yalnız o gün ve o günün verisiyle üretilir; Android bildirimi gösterirken kuralı yeniden çalıştırır,
     * o ana kadar fark kapandıysa bildirim düşer. Başka günlerde hiç üretilmez (haftada en fazla bir kez).
     */
    private fun categorySpike(input: CoachInput, config: CoachConfig): List<CoachNotification> {
        val weekStart = input.budget.weekStart
        if (input.today != weekStart.plusDays(SPIKE_CHECK_OFFSET)) return emptyList()
        val currency = input.budget.weeklyLimit.currency
        val lastWeekStart = weekStart.minusDays(7)
        val lastWeekToday = input.today.minusDays(7)

        fun totals(from: LocalDate, to: LocalDate): Map<Category, Long> =
            input.expenses.filter { it.date in from..to && it.grossAmount.currency == currency }
                .groupBy { it.category }
                .mapValues { (_, list) -> list.sumOf { it.chargeableAmount.minor } }

        val now = totals(weekStart, input.today)
        val before = totals(lastWeekStart, lastWeekToday)
        val minDiff = input.budget.weeklyLimit.minor / 10
        val spike = now.mapNotNull { (category, minor) ->
            val previous = before[category] ?: return@mapNotNull null
            val diff = minor - previous
            if (previous <= 0 || diff < minDiff || minor * 10 < previous * 13) null else Triple(category, minor, previous)
        }.maxByOrNull { (_, minor, previous) -> minor - previous } ?: return emptyList()

        val (category, minor, previous) = spike
        val percent = ((minor - previous) * 100.0 / previous).roundToInt()
        val nowText = MoneyFormatter.format(Money(minor, currency), config.locale)
        val beforeText = MoneyFormatter.format(Money(previous, currency), config.locale)
        val msg = when (config.locale) {
            AppLocale.TR -> "Bu hafta ${category.dativeTr} geçen haftadan %$percent fazla gitti: $nowText " +
                "(geçen hafta bu zamana kadar $beforeText). Hafta sonu biraz dikkat 🙂"
            AppLocale.EN -> "You've spent $percent% more on ${category.nameEn} this week than last week: $nowText " +
                "(vs $beforeText by this time last week)."
        }
        return listOf(CoachNotification(input.today, LocalTime(17, 30), NotificationType.CATEGORY_SPIKE, 72, msg))
    }

    // ───────────────────────── Kısıtlar ─────────────────────────

    internal fun applyConstraints(
        candidates: List<CoachNotification>,
        config: CoachConfig,
    ): List<CoachNotification> {
        // 1) Sessiz saatlerdeki bildirimleri sessiz saat bitişine kaydır.
        val shifted = candidates.map { n ->
            if (config.quietHours.contains(n.time)) n.copy(time = config.quietHours.end) else n
        }
        // 2) Gün başına en fazla maxPerDay: önceliğe (sonra saate) göre seç.
        val byDay = shifted.groupBy { it.date }
        val kept = byDay.values.flatMap { dayItems ->
            dayItems.sortedWith(compareByDescending<CoachNotification> { it.priority }.thenBy { it.time })
                .take(config.maxPerDay)
        }
        // 3) Son sıralama: tarih sonra saat.
        return kept.sortedWith(compareBy({ it.date }, { it.time }))
    }

    // ──────────────────── Cümle varyasyonu ────────────────────

    private fun variant(
        config: CoachConfig,
        type: NotificationType,
        date: LocalDate,
        tr: List<String>,
        en: List<String>,
    ): String {
        val options = if (config.locale == AppLocale.TR) tr else en
        // Deterministik: tür + gün + tohum → sabit varyant (test edilebilir, günler arası çeşitli).
        val idx = ((type.ordinal * 31 + date.dayOfMonth + config.variantSeed) % options.size + options.size) % options.size
        return options[idx]
    }

    private fun endOfWeekIso(weekStart: DayOfWeek): Int =
        ((weekStart.isoDayNumber - 1 + 6) % 7) + 1

    private companion object {
        /** Hafta başından kaç gün sonra kategori kıyası yapılır (4 = 5. gün). */
        const val SPIKE_CHECK_OFFSET = 4
    }
}

/** "yemeğe", "markete": "Bu hafta ___ geçen haftadan fazla gitti" cümlesi için. */
private val Category.dativeTr: String
    get() = when (this) {
        Category.MARKET -> "markete"
        Category.YEMEK -> "yemeğe"
        Category.ULASIM -> "ulaşıma"
        Category.FATURA -> "faturalara"
        Category.SAGLIK -> "sağlığa"
        Category.EGLENCE -> "eğlenceye"
        Category.GIYIM -> "giyime"
        Category.KISISEL -> "kişisel bakıma"
        Category.EV -> "ev harcamalarına"
        Category.DIGER -> "diğer harcamalara"
    }

private val Category.nameEn: String
    get() = when (this) {
        Category.MARKET -> "groceries"
        Category.YEMEK -> "food"
        Category.ULASIM -> "transport"
        Category.FATURA -> "bills"
        Category.SAGLIK -> "health"
        Category.EGLENCE -> "fun"
        Category.GIYIM -> "clothing"
        Category.KISISEL -> "personal care"
        Category.EV -> "home"
        Category.DIGER -> "other things"
    }
