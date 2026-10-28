package com.cinch.shared.budget

import com.cinch.shared.domain.Card
import com.cinch.shared.domain.CardStatement
import com.cinch.shared.domain.Expense
import com.cinch.shared.domain.FixedExpense
import com.cinch.shared.domain.FixedPlan
import com.cinch.shared.domain.Income
import com.cinch.shared.domain.repository.BudgetRepository
import com.cinch.shared.domain.repository.CardRepository
import com.cinch.shared.domain.repository.CardStatementRepository
import com.cinch.shared.domain.repository.ExpenseRepository
import com.cinch.shared.domain.repository.FixedExpenseRepository
import com.cinch.shared.domain.repository.IncomeRepository
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.shared.parser.minusDays
import com.cinch.shared.parser.plusDays
import com.cinch.shared.prefs.KeyValueStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

enum class CashEventKind { INCOME, FIXED, CARD }

/** Takvimdeki tek para hareketi. [amount] artı: giren, eksi: çıkan. */
data class CashEvent(val date: LocalDate, val kind: CashEventKind, val title: String, val amount: Money)

/**
 * @param spending o gün için düşülen tahmini serbest harcama (haftalık sınırın yedide biri; bugün için kalanı).
 * @param balance gün sonunda tahmini bakiye; başlangıç bakiyesi bilinmiyorsa null.
 */
data class CashDay(val date: LocalDate, val events: List<CashEvent>, val spending: Money, val balance: Money?)

/**
 * "Maaşa kadar yeter mi?": bugünden itibaren gün gün para giriş-çıkışı ve tahmini bakiye.
 *
 * @param nextPayday ilk gelir günü (yarından itibaren).
 * @param firstShortfall bakiyenin ilk eksiye düştüğü gün; bakiye bilinmiyorsa ya da düşmüyorsa null.
 * @param balanceBeforePayday maaştan önceki günün sonunda kalan.
 * @param dailyCutNeeded maaşa (ya da takvim sonuna) kadar eksiye düşmemek için serbest harcamadan günde ne kadar
 *   kısılmalı; gerek yoksa null.
 * @param creditCardsWithoutStatement ekstresi okunmamış kredi kartı sayısı (son ödemeleri takvimde yok).
 */
data class CashForecast(
    val start: LocalDate,
    val end: LocalDate,
    val startBalance: Money?,
    val dailySpending: Money,
    val days: List<CashDay>,
    val totalIn: Money,
    val totalOut: Money,
    val nextPayday: LocalDate?,
    val firstShortfall: LocalDate?,
    val balanceBeforePayday: Money?,
    val dailyCutNeeded: Money?,
    val creditCardsWithoutStatement: Int,
) {
    /** Hareketi olan günler (bugün her zaman başta). */
    val eventDays: List<CashDay> get() = days.filter { it.events.isNotEmpty() || it.date == start }
}

/**
 * Para takviminin hesabı (saf; testler doğrudan çağırır).
 *
 * Basit tutulan varsayımlar (ekranda bilgi balonunda anlatılır):
 * - Serbest harcama her gün eşit: haftalık sınırın yedide biri; bugün için bugün harcanmamış kısmı.
 * - Kartla ödenen sabit giderler ayrıca düşülmez: kart ekstresinin içindedir. Kart ödemesi okunan son ekstrenin
 *   kalan borcuyla son ödeme gününde düşülür.
 * - Bu ay günü geçmiş ama "ödendi" işaretlenmemiş sabit gider bugün düşülür.
 */
object CashFlows {

    /** Kısılacak günlük tutar 10 TL'ye yuvarlanır. */
    private const val CUT_STEP_MINOR = 1_000L

    fun forecast(
        today: LocalDate,
        currency: Currency,
        startBalance: Money?,
        incomes: List<Income>,
        fixed: List<FixedExpense>,
        fixedPayments: List<Expense>,
        cards: List<Card>,
        statements: List<CardStatement>,
        dailySpending: Money,
        spentToday: Money,
        horizonDays: Int = 35,
    ): CashForecast {
        val end = today.plusDays(horizonDays - 1)
        val events = buildList {
            incomes.filter { it.currency == currency && !it.amount.isZero }.forEach { income ->
                income.paydaysBetween(today.plusDays(1), end).forEach { add(CashEvent(it, CashEventKind.INCOME, income.name, income.amount)) }
            }
            fixed.filter { it.currency == currency && it.cardId == null }.forEach { addAll(fixedEvents(it, fixedPayments, today, end)) }
            statements.forEach { statement ->
                val card = cards.firstOrNull { it.id == statement.cardId && it.isCredit } ?: return@forEach
                val due = statement.dueDate ?: return@forEach
                val balance = statement.statementBalance ?: return@forEach
                if (due < today || due > end || balance.isZero || balance.isNegative || balance.currency != currency) return@forEach
                add(CashEvent(due, CashEventKind.CARD, "${card.name} kart ödemesi", -balance))
            }
        }.sortedWith(compareBy({ it.date }, { it.kind.ordinal }))

        val byDay = events.groupBy { it.date }
        val todaySpending = Money((dailySpending.minor - spentToday.minor).coerceAtLeast(0), currency)
        var running = startBalance?.minor
        val days = (0 until horizonDays).map { offset ->
            val date = today.plusDays(offset)
            val dayEvents = byDay[date].orEmpty()
            val spending = if (offset == 0) todaySpending else dailySpending
            running = running?.let { it + dayEvents.sumOf { e -> e.amount.minor } - spending.minor }
            CashDay(date, dayEvents, spending, running?.let { Money(it, currency) })
        }

        val nextPayday = events.firstOrNull { it.kind == CashEventKind.INCOME }?.date
        // Maaşa kadarki pencere: maaş günü takvimde yoksa takvimin sonuna kadar.
        val window = days.filter { nextPayday == null || it.date < nextPayday }
        return CashForecast(
            start = today,
            end = end,
            startBalance = startBalance,
            dailySpending = dailySpending,
            days = days,
            totalIn = Money(events.filter { it.amount.minor > 0 }.sumOf { it.amount.minor }, currency),
            totalOut = Money(-events.filter { it.amount.minor < 0 }.sumOf { it.amount.minor }, currency),
            nextPayday = nextPayday,
            firstShortfall = days.firstOrNull { it.balance?.isNegative == true }?.date,
            balanceBeforePayday = nextPayday?.let { pay -> days.lastOrNull { it.date < pay }?.balance },
            dailyCutNeeded = cutNeeded(window, currency),
            creditCardsWithoutStatement = cards.count { card ->
                card.isCredit && statements.none { it.cardId == card.id && (it.dueDate ?: today.minusDays(1)) >= today }
            },
        )
    }

    /**
     * Her gün X kadar az harcanırsa k. günün sonundaki bakiye X·(k+1) artar. Penceredeki her gün eksiye düşmesin diye
     * gereken en küçük X: en kötü günün açığı / o güne kadarki gün sayısı.
     */
    private fun cutNeeded(window: List<CashDay>, currency: Currency): Money? {
        val worst = window.withIndex().maxOfOrNull { (i, day) ->
            val balance = day.balance?.minor ?: return null
            if (balance >= 0) 0L else (-balance + i) / (i + 1)
        } ?: return null
        if (worst <= 0) return null
        val rounded = (worst + CUT_STEP_MINOR - 1) / CUT_STEP_MINOR * CUT_STEP_MINOR
        return Money(rounded, currency)
    }

    /** Sabit giderin takvimdeki ödemeleri; taksit ve esnek borcun kalanı aşılmaz, ödenmiş aylar atlanır. */
    private fun fixedEvents(fixed: FixedExpense, payments: List<Expense>, today: LocalDate, end: LocalDate): List<CashEvent> {
        val progress = fixed.progress(payments, today)
        if (progress?.isFinished == true) return emptyList()
        var installmentsLeft = progress?.remainingCount
        var debtLeft = progress?.remainingDebt?.minor
        val usual = if (fixed.plan == FixedPlan.FLEXIBLE && fixed.amount.isZero) progress?.estimateBase ?: fixed.amount else fixed.amount

        fun paidIn(date: LocalDate) = payments.any {
            it.fixedExpenseId == fixed.id && it.date.year == date.year && it.date.month == date.month
        }

        // Bu ayın ödeme günü geçmiş ve ödenmemişse bugün düşülür.
        val thisMonthDue = fixed.dueDateIn(today)
        val dates = buildList {
            if (thisMonthDue < today && !paidIn(today)) add(today to true)
            fixed.dueDatesBetween(today, end).forEach { add(it to false) }
        }
        return dates.mapNotNull { (date, overdue) ->
            if (!overdue && paidIn(date)) return@mapNotNull null
            val amount = when (fixed.plan) {
                FixedPlan.INSTALLMENTS -> {
                    val left = installmentsLeft
                    if (left != null && left <= 0) return@mapNotNull null
                    installmentsLeft = left?.minus(1)
                    usual.minor
                }
                FixedPlan.FLEXIBLE -> {
                    val left = debtLeft ?: usual.minor
                    val pay = minOf(usual.minor, left)
                    debtLeft = left - pay
                    pay
                }
                FixedPlan.MONTHLY -> usual.minor
            }
            if (amount <= 0) return@mapNotNull null
            val title = if (overdue) "${fixed.name} (günü geçti)" else fixed.name
            CashEvent(date, CashEventKind.FIXED, title, Money(-amount, fixed.currency))
        }
    }
}

/** Kullanıcının girdiği hesap bakiyesi ve girildiği gün (para takviminin başlangıcı). */
class CashBalanceStore(private val prefs: KeyValueStore) {

    fun get(): Pair<Money, LocalDate>? {
        val day = prefs.getLong(KEY_DAY)?.takeIf { it >= 0 } ?: return null
        val minor = prefs.getLong(KEY_MINOR) ?: return null
        return Money(minor, Currency.TRY) to LocalDate.fromEpochDays(day)
    }

    /** null: silindi (alan boşaltıldı). */
    fun set(balance: Money?, date: LocalDate) {
        if (balance == null) {
            prefs.putLong(KEY_DAY, -1)
            return
        }
        prefs.putLong(KEY_MINOR, balance.minor)
        prefs.putLong(KEY_DAY, date.toEpochDays())
    }

    private companion object {
        const val KEY_MINOR = "cash_balance_minor"
        const val KEY_DAY = "cash_balance_epoch_day"
    }
}

/** Veritabanını izleyip [CashFlows.forecast]'ı yeniden hesaplar: harcama girildikçe takvim güncellenir. */
class ForecastCashFlow(
    private val budgets: BudgetRepository,
    private val incomes: IncomeRepository,
    private val fixedExpenses: FixedExpenseRepository,
    private val expenses: ExpenseRepository,
    private val cards: CardRepository,
    private val statements: CardStatementRepository,
) {
    private data class Inputs(
        val currency: Currency,
        val daily: Money,
        val incomes: List<Income>,
        val fixed: List<FixedExpense>,
        val cards: List<Card>,
    )

    operator fun invoke(today: LocalDate, startBalance: Money?): Flow<CashForecast> {
        val inputs = combine(budgets.observe(), incomes.observeAll(), fixedExpenses.observeAll(), cards.observeAll()) { budget, incomeList, fixed, cardList ->
            val currency = budget?.currency ?: Currency.TRY
            val daily = budget?.let { Money(it.weeklyLimit.minor / 7, currency) } ?: Money.zero(currency)
            Inputs(currency, daily, incomeList, fixed, cardList)
        }
        return combine(
            inputs,
            expenses.observeFixedPayments(),
            expenses.observeBetween(today, today),
            statements.observeLatestPerCard(),
        ) { input, payments, todays, latest ->
            val spentToday = Money.sum(
                todays.discretionary().filter { it.grossAmount.currency == input.currency }.map { it.chargeableAmount },
                input.currency,
            )
            CashFlows.forecast(
                today = today,
                currency = input.currency,
                startBalance = startBalance?.takeIf { it.currency == input.currency },
                incomes = input.incomes,
                fixed = input.fixed,
                fixedPayments = payments,
                cards = input.cards,
                statements = latest,
                dailySpending = input.daily,
                spentToday = spentToday,
            )
        }
    }
}

/** Kaç gün önce: takvimde "bakiyeyi 3 gün önce girdin" demek için. */
fun LocalDate.daysAgo(today: LocalDate): Int = daysUntil(today)
