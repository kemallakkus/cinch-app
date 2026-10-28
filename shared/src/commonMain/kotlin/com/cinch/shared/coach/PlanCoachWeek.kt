package com.cinch.shared.coach

import com.cinch.shared.budget.BudgetEngine
import com.cinch.shared.budget.discretionary
import com.cinch.shared.budget.inCurrencyOf
import com.cinch.shared.domain.repository.BudgetRepository
import com.cinch.shared.domain.repository.CardRepository
import com.cinch.shared.domain.repository.CardStatementRepository
import com.cinch.shared.domain.repository.ExpenseRepository
import com.cinch.shared.domain.repository.FixedExpenseRepository
import com.cinch.shared.domain.repository.IncomeRepository
import com.cinch.shared.budget.BudgetConfig
import com.cinch.shared.budget.DebtProgress
import com.cinch.shared.budget.progress
import com.cinch.shared.domain.FixedExpense
import com.cinch.shared.domain.FixedPlan
import com.cinch.shared.money.Money
import com.cinch.shared.parser.minusDays
import com.cinch.shared.parser.plusDays
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Veritabanındaki bütçe, harcama ve kartlardan [CoachRules] girdisini kurar ve önümüzdeki
 * 7 günün bildirim planını üretir. Her kayıttan sonra yeniden çağrılması beklenir.
 *
 * @param lookbackDays alışkanlık ve seri kuralları için geriye bakılan gün sayısı (bugün dahil).
 */
class PlanCoachWeek(
    private val budgets: BudgetRepository,
    private val expenses: ExpenseRepository,
    private val cards: CardRepository,
    private val incomes: IncomeRepository,
    private val fixedExpenses: FixedExpenseRepository,
    private val statements: CardStatementRepository,
    private val engine: BudgetEngine,
    private val rules: CoachRules,
    private val lookbackDays: Int = 30,
) {

    /**
     * Bütçe kurulmadıysa plan boş döner: günlük pay hesaplanamaz.
     *
     * Bugünün planı iki kaynaktan oluşur: bugünün verisiyle kurulan kurallar ve önceki günlerde bugüne
     * planlanmış bildirimler ([leadDays]: "dün kayıt yoktu", "sınıra yaklaşıyorsun", seri, alışkanlık).
     * İkinciler planlandıkları günün gözünden ama **güncel** veriyle yeniden kurulur. Böyle olmasa her
     * yeniden planlamada bir gün ileri kayar, hiç gösterilmezlerdi; Android gösterirken de bulamazdı.
     * Günde en fazla bildirim kısıtı ikisine birlikte uygulanır.
     */
    suspend operator fun invoke(today: LocalDate, config: CoachConfig = CoachConfig()): List<CoachNotification> {
        val budget = budgets.get() ?: return emptyList()
        // Haftalık kapanış günü bütçenin hafta başlangıcıyla aynı olmalı.
        val cfg = config.copy(weekStart = budget.weekStart)

        val fresh = rules.candidates(input(budget, today), cfg)
        val carried = CARRIED_LEADS.flatMap { lead ->
            rules.candidates(input(budget, today.minusDays(lead)), cfg)
                .filter { it.type.leadDays == lead && it.date == today }
        }
        return rules.applyConstraints(fresh + carried, cfg)
    }

    /** [day] gününün gözünden kural girdisi: o güne kadarki harcamalar, o haftanın durumu. */
    private suspend fun input(budget: BudgetConfig, day: LocalDate): CoachInput {
        val statusRange = BudgetEngine.statusRange(day, budget.weekStart)
        val lookbackStart = day.minusDays(lookbackDays - 1)
        val fetched = expenses.between(
            from = minOf(statusRange.start, lookbackStart),
            to = statusRange.endInclusive,
        ).inCurrencyOf(budget).discretionary()

        val status = engine.status(fetched, budget, day)
        // Koç kuralları yalnız son lookbackDays günü görür: alışkanlık sayımı "bu dönem" içindir.
        val recent = fetched.filter { it.date in lookbackStart..day }

        // CoachRules ufkun dışına taşan tarihleri kendisi eler; bir ay ileri bakmak yeter.
        val lookahead = day.plus(DatePeriod(months = 1))
        val statementDates = cards.all()
            .flatMap { it.statementDatesBetween(day, lookahead) }
            .distinct()

        return CoachInput(
            today = day,
            budget = status,
            expenses = recent,
            statementDates = statementDates,
            salaryDates = salaryDates(budget, day, lookahead),
            fixedDues = fixedDues(budget, day),
            cardDues = cardDues(day),
        )
    }

    /** Her kartın son okunan ekstresinden, günü geçmemiş son ödemeler. */
    private suspend fun cardDues(day: LocalDate): List<CardDue> {
        val names = cards.all().associate { it.id to it.name }
        return statements.latestPerCard().mapNotNull { statement ->
            val due = statement.dueDate?.takeIf { it >= day } ?: return@mapNotNull null
            val name = names[statement.cardId] ?: return@mapNotNull null // kart silinmiş
            CardDue(name, due, statement.statementBalance, statement.minimumPayment)
        }
    }

    /**
     * Önümüzdeki 8 günün sabit gider ödeme günleri (yarınki ödeme bugünden hatırlatılır). O ayın ödemesi
     * zaten işaretlendiyse [FixedDue.paid] true: hatırlatma gerekmez. Biten borçlar (son taksit ödendi,
     * kalan borç sıfır) hiç hatırlatılmaz.
     */
    private suspend fun fixedDues(budget: BudgetConfig, today: LocalDate): List<FixedDue> {
        val until = today.plusDays(8)
        val payments = expenses.fixedPayments()
        return fixedExpenses.all()
            .filter { it.currency == budget.currency }
            .flatMap { fixed ->
                val progress = fixed.progress(payments, today)
                if (progress?.isFinished == true) return@flatMap emptyList()
                fixed.dueDatesBetween(today, until).map { date ->
                    val paid = payments.any {
                        it.fixedExpenseId == fixed.id && it.date.year == date.year && it.date.month == date.month
                    }
                    FixedDue(dueName(fixed, progress), dueAmount(fixed, progress), date, paid)
                }
            }
    }

    /** Taksitte kaçıncı taksit olduğu da yazılır: "Ablama borç (5/6. taksit)". */
    private fun dueName(fixed: FixedExpense, progress: DebtProgress?): String {
        val paid = progress?.paidCount ?: return fixed.name
        val total = progress.totalCount ?: return fixed.name
        return "${fixed.name} (${paid + 1}/$total. taksit)"
    }

    /** Esnek borçta tutar kalan borcu aşmaz; "genelde ödediğim" girilmemişse tutar yazılmaz. */
    private fun dueAmount(fixed: FixedExpense, progress: DebtProgress?): Money? = when (fixed.plan) {
        FixedPlan.FLEXIBLE -> progress?.let { minOf(fixed.amount, it.remainingDebt) }?.takeIf { !it.isZero }
        else -> fixed.amount
    }

    /**
     * Maaş bildirimi en büyük düzenli gelirin gününe bağlanır: mesaj "Maaş günü" der, kira geliri gibi
     * ek gelirler için uygun değil.
     */
    private suspend fun salaryDates(budget: BudgetConfig, from: LocalDate, to: LocalDate): List<LocalDate> =
        incomes.all()
            .filter { it.dayOfMonth != null && it.currency == budget.currency }
            .maxByOrNull { it.amount.minor }
            ?.paydaysBetween(from, to)
            .orEmpty()

    private companion object {
        /** Önceden planlanan bildirim türlerinin kaç gün önceden planlandığı (ör. 1 ve 2). */
        val CARRIED_LEADS: List<Int> = NotificationType.entries.map { it.leadDays }.filter { it > 0 }.distinct()
    }
}
