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
import kotlinx.datetime.daysUntil
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
    CARD_DUE,           // kredi kartı son ödeme günü yaklaşıyor (banka ekranından okunan)
    BACKUP_REMINDER,    // uzun süredir yedek alınmadı
    WEEK_LEFTOVER,      // geçen hafta sınırın altında kalındı: artanı kumbaraya at
}

/**
 * Kuralın bildirimi, planın kurulduğu günden kaç gün sonraya koyduğu. "Yarın sabah sınıra yaklaşıyorsun",
 * "dün kayıt yoktu" gibi bildirimler bir gün önceden, alışkanlık uyarısı iki gün önceden planlanır.
 *
 * Plan her gün yeniden kurulduğu için bu bildirimler bugünün planında "yarın"a kayar; [PlanCoachWeek] bu
 * yüzden bugüne düşenleri, planlandıkları günün gözünden ama güncel veriyle yeniden kurup bugünün planına ekler.
 */
internal val NotificationType.leadDays: Int
    get() = when (this) {
        NotificationType.FORGOT_LOG, NotificationType.APPROACHING_LIMIT, NotificationType.STREAK -> 1
        NotificationType.HABIT_ALERT -> 2
        else -> 0
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
    val cardDues: List<CardDue> = emptyList(),
    /** Son yedek günü; hiç alınmadıysa null. */
    val lastBackup: LocalDate? = null,
)

/**
 * Banka ekranından okunan kart son ödemesi. [remaining] okunduğu andaki kalan ekstre borcu; null = okunamadı
 * (mesajda tutar yazılmaz), sıfır = ödenmiş (hatırlatılmaz).
 */
data class CardDue(val cardName: String, val dueDate: LocalDate, val remaining: Money?, val minimum: Money?)

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

    fun planWeek(input: CoachInput, config: CoachConfig): List<CoachNotification> =
        applyConstraints(candidates(input, config), config)

    /** Kısıt uygulanmamış adaylar; [PlanCoachWeek] önceki günlerden taşınanlarla birleştirip kısıtları bir kez uygular. */
    internal fun candidates(input: CoachInput, config: CoachConfig): List<CoachNotification> =
        buildList {
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
            addAll(cardDue(input, config))
            addAll(backupReminder(input, config))
            addAll(weekLeftover(input, config))
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
        val date = input.today.plusDays(NotificationType.APPROACHING_LIMIT.leadDays)
        // Yeni hafta yeni sınırla başlar: pazar akşamki durum pazartesi sabahı söylenmez.
        if (date > input.budget.weekEnd) return emptyList()
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
        val date = input.today.plusDays(NotificationType.FORGOT_LOG.leadDays)
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

    /**
     * Haftada bir, haftanın 3. günü (pazartesi başlayan haftada çarşamba) 18:00. Mesaj günden güne pek değişmediği
     * için her gün söylenmez.
     */
    private fun habitAlert(input: CoachInput, config: CoachConfig): List<CoachNotification> {
        val date = input.today.plusDays(NotificationType.HABIT_ALERT.leadDays)
        if (date != input.budget.weekStart.plusDays(HABIT_DAY_OFFSET)) return emptyList()
        val counts = mutableMapOf<String, Int>()
        for (e in input.expenses) {
            for (tok in e.tokens) {
                if (tok.length < 3) continue
                counts[tok] = (counts[tok] ?: 0) + 1
            }
        }
        val top = counts.maxByOrNull { it.value } ?: return emptyList()
        if (top.value < config.habitThreshold) return emptyList()
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
        val date = input.today.plusDays(NotificationType.STREAK.leadDays)
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

    /**
     * Kart son ödemesi: 3 gün önce 10:00'da ve o gün 09:30'da. Gecikme faizi ve kredi notu için önemli olduğundan
     * önceliği yüksek. Okunan borç sıfırsa (ödenmiş) hatırlatılmaz; kullanıcı ödedikten sonra ekranı yeniden
     * okutursa hatırlatma kalkar.
     */
    private fun cardDue(input: CoachInput, config: CoachConfig): List<CoachNotification> {
        val horizonSet = horizon(input.today).toSet()
        return input.cardDues
            .filter { due -> due.remaining?.let { it.isZero || it.isNegative } != true }
            .flatMap { due ->
                val amount = due.remaining?.let { MoneyFormatter.format(it, config.locale) }
                val minimum = due.minimum?.let { MoneyFormatter.format(it, config.locale) }
                val dayText = "${due.dueDate.day} ${MONTHS_TR[due.dueDate.month.ordinal]}"
                buildList {
                    val early = due.dueDate.minusDays(CARD_DUE_EARLY_DAYS)
                    if (early in horizonSet) {
                        val msg = when (config.locale) {
                            AppLocale.TR -> "${due.cardName} kartının son ödemesi $CARD_DUE_EARLY_DAYS gün sonra ($dayText)" +
                                (amount?.let { ": kalan $it" } ?: ".") + (minimum?.let { " · asgari $it" } ?: "")
                            AppLocale.EN -> "${due.cardName} card payment is due in $CARD_DUE_EARLY_DAYS days" +
                                (amount?.let { ": $it left" } ?: ".") + (minimum?.let { " · minimum $it" } ?: "")
                        }
                        add(CoachNotification(early, LocalTime(10, 0), NotificationType.CARD_DUE, 88, msg))
                    }
                    if (due.dueDate in horizonSet) {
                        val msg = when (config.locale) {
                            AppLocale.TR -> "Bugün ${due.cardName} kartının son ödeme günü" +
                                (amount?.let { ": kalan $it." } ?: ".") + " Ödediysen ekranı yeniden okut, hatırlatma kalksın."
                            AppLocale.EN -> "${due.cardName} card payment is due today" + (amount?.let { ": $it left." } ?: ".")
                        }
                        add(CoachNotification(due.dueDate, LocalTime(9, 30), NotificationType.CARD_DUE, 88, msg))
                    }
                }
            }
    }

    /**
     * Haftada bir (haftanın 2. günü 19:00): son yedek 30 günden eskiyse ya da hiç yedek yokken son 30 günde en az 20
     * kayıt birikmişse. Öncelik düşük: o gün daha önemli bildirim varsa haftaya kalır.
     */
    private fun backupReminder(input: CoachInput, config: CoachConfig): List<CoachNotification> {
        val day = horizon(input.today).firstOrNull { input.budget.weekStart.daysUntil(it).mod(7) == BACKUP_DAY_OFFSET }
            ?: return emptyList()
        val last = input.lastBackup
        val msg = if (last == null) {
            if (input.expenses.size < BACKUP_MIN_RECORDS) return emptyList()
            when (config.locale) {
                AppLocale.TR -> "Verilerin yalnız bu telefonda. Ayarlar'dan tek dosyalık bir yedek al; telefon kaybolursa geri yüklersin."
                AppLocale.EN -> "Your data lives only on this phone. Take a one-file backup in Settings."
            }
        } else {
            val days = last.daysUntil(day)
            if (days < BACKUP_STALE_DAYS) return emptyList()
            when (config.locale) {
                AppLocale.TR -> "Son yedeğin $days gün önce. Ayarlar'dan yenisini al, içi rahat etsin."
                AppLocale.EN -> "Your last backup was $days days ago. Take a fresh one in Settings."
            }
        }
        return listOf(CoachNotification(day, LocalTime(19, 0), NotificationType.BACKUP_REMINDER, 35, msg))
    }

    /**
     * Haftanın ilk günü 10:30: geçen hafta kayıt tutulmuş ve sınırın altında kalınmışsa artanı kumbaraya atmayı önerir.
     * Yalnız o gün üretilir (gösterim anında yeniden hesaplanır).
     */
    private fun weekLeftover(input: CoachInput, config: CoachConfig): List<CoachNotification> {
        val weekStart = input.budget.weekStart
        if (input.today != weekStart) return emptyList()
        val currency = input.budget.weeklyLimit.currency
        val lastWeek = input.expenses.filter {
            it.date >= weekStart.minusDays(7) && it.date < weekStart && it.grossAmount.currency == currency
        }
        if (lastWeek.isEmpty()) return emptyList()
        val left = input.budget.weeklyLimit.minor - lastWeek.sumOf { it.chargeableAmount.minor }
        if (left <= 0) return emptyList()
        val amount = MoneyFormatter.format(Money(left, currency), config.locale)
        val msg = when (config.locale) {
            AppLocale.TR -> "Geçen hafta $amount artırdın 🎉 Kumbarana atmak için Cinch'e bir dokun."
            AppLocale.EN -> "You saved $amount under your limit last week 🎉 Put it in a savings goal."
        }
        return listOf(CoachNotification(weekStart, LocalTime(10, 30), NotificationType.WEEK_LEFTOVER, 72, msg))
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

        /** Alışkanlık uyarısının günü: hafta başından 2 gün sonra (3. gün). */
        const val HABIT_DAY_OFFSET = 2

        /** Kart son ödemesi kaç gün önceden hatırlatılır. */
        const val CARD_DUE_EARLY_DAYS = 3

        /** Yedek hatırlatmasının günü: hafta başından 1 gün sonra (2. gün). */
        const val BACKUP_DAY_OFFSET = 1
        const val BACKUP_STALE_DAYS = 30
        const val BACKUP_MIN_RECORDS = 20

        val MONTHS_TR = listOf(
            "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran", "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık",
        )
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
