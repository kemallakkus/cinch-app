package com.cinch.shared.coach

import com.cinch.shared.budget.BudgetEngine
import com.cinch.shared.budget.discretionary
import com.cinch.shared.budget.inCurrencyOf
import com.cinch.shared.domain.repository.BudgetRepository
import com.cinch.shared.domain.repository.CardRepository
import com.cinch.shared.domain.repository.ExpenseRepository
import com.cinch.shared.domain.repository.FixedExpenseRepository
import com.cinch.shared.domain.repository.IncomeRepository
import com.cinch.shared.budget.BudgetConfig
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
    private val engine: BudgetEngine,
    private val rules: CoachRules,
    private val lookbackDays: Int = 30,
) {

    /** Bütçe kurulmadıysa plan boş döner: günlük pay hesaplanamaz. */
    suspend operator fun invoke(today: LocalDate, config: CoachConfig = CoachConfig()): List<CoachNotification> {
        val budget = budgets.get() ?: return emptyList()

        val statusRange = BudgetEngine.statusRange(today, budget.weekStart)
        val lookbackStart = today.minusDays(lookbackDays - 1)
        val fetched = expenses.between(
            from = minOf(statusRange.start, lookbackStart),
            to = statusRange.endInclusive,
        ).inCurrencyOf(budget).discretionary()

        val status = engine.status(fetched, budget, today)
        // Koç kuralları yalnız son lookbackDays günü görür: alışkanlık sayımı "bu dönem" içindir.
        val recent = fetched.filter { it.date in lookbackStart..today }

        // CoachRules ufkun dışına taşan tarihleri kendisi eler; bir ay ileri bakmak yeter.
        val lookahead = today.plus(DatePeriod(months = 1))
        val statementDates = cards.all()
            .flatMap { it.statementDatesBetween(today, lookahead) }
            .distinct()

        val input = CoachInput(
            today = today,
            budget = status,
            expenses = recent,
            statementDates = statementDates,
            salaryDates = salaryDates(budget, today, lookahead),
            fixedDues = fixedDues(budget, today),
        )
        // Haftalık kapanış günü bütçenin hafta başlangıcıyla aynı olmalı.
        return rules.planWeek(input, config.copy(weekStart = budget.weekStart))
    }

    /**
     * Önümüzdeki 8 günün sabit gider ödeme günleri (yarınki ödeme bugünden hatırlatılır). O ayın ödemesi
     * zaten işaretlendiyse [FixedDue.paid] true: hatırlatma gerekmez.
     */
    private suspend fun fixedDues(budget: BudgetConfig, today: LocalDate): List<FixedDue> {
        val until = today.plusDays(8)
        val payments = expenses.between(LocalDate(today.year, today.month, 1), until).filter { it.isFixedPayment }
        return fixedExpenses.all()
            .filter { it.currency == budget.currency }
            .flatMap { fixed ->
                fixed.dueDatesBetween(today, until).map { date ->
                    val paid = payments.any {
                        it.fixedExpenseId == fixed.id && it.date.year == date.year && it.date.month == date.month
                    }
                    FixedDue(fixed.name, fixed.amount, date, paid)
                }
            }
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
}
