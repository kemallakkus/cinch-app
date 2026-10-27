package com.cinch.shared.coach

import com.cinch.shared.budget.BudgetEngine
import com.cinch.shared.budget.inCurrencyOf
import com.cinch.shared.domain.repository.BudgetRepository
import com.cinch.shared.domain.repository.CardRepository
import com.cinch.shared.domain.repository.ExpenseRepository
import com.cinch.shared.parser.minusDays
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
        ).inCurrencyOf(budget)

        val status = engine.status(fetched, budget, today)
        // Koç kuralları yalnız son lookbackDays günü görür: alışkanlık sayımı "bu dönem" içindir.
        val recent = fetched.filter { it.date in lookbackStart..today }

        // CoachRules ekstreden bir gün önce uyarır; ufkun dışına taşan tarihleri kendisi eler.
        val statementDates = cards.all()
            .flatMap { it.statementDatesBetween(today, today.plus(DatePeriod(months = 1))) }
            .distinct()

        val input = CoachInput(
            today = today,
            budget = status,
            expenses = recent,
            statementDates = statementDates,
            salaryDates = emptyList(), // maaş günü henüz saklanmıyor
        )
        // Haftalık kapanış günü bütçenin hafta başlangıcıyla aynı olmalı.
        return rules.planWeek(input, config.copy(weekStart = budget.weekStart))
    }
}
