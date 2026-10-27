package com.cinch.shared.di

import com.cinch.shared.budget.BudgetEngine
import com.cinch.shared.budget.ObserveBudgetStatus
import com.cinch.shared.budget.ObserveMonthSummary
import com.cinch.shared.category.Categorizer
import com.cinch.shared.category.CorrectionStore
import com.cinch.shared.category.KeywordDictionary
import com.cinch.shared.coach.CoachNotificationPlanner
import com.cinch.shared.coach.CoachRules
import com.cinch.shared.coach.PlanCoachWeek
import com.cinch.shared.domain.repository.BudgetRepository
import com.cinch.shared.domain.repository.CardRepository
import com.cinch.shared.domain.repository.ExpenseRepository
import com.cinch.shared.domain.repository.IncomeRepository
import com.cinch.shared.debt.PayoffSimulator
import com.cinch.shared.parser.QuickEntryParser
import com.cinch.shared.reconcile.Reconciler
import com.cinch.shared.time.LocalClock
import com.cinch.shared.time.SystemLocalClock
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/** Domain motorları ve onları repository'lere bağlayan use case'ler. */
@Module
@Configuration
class DomainModule {

    @Single
    fun localClock(): LocalClock = SystemLocalClock

    @Single
    fun keywordDictionary(): KeywordDictionary = KeywordDictionary.turkishDefault()

    /** Düzeltmeler veritabanından gelir; kullanıcı düzelttikçe kalıcı olarak öğrenir. */
    @Single
    fun categorizer(dictionary: KeywordDictionary, corrections: CorrectionStore): Categorizer =
        Categorizer(dictionary, corrections)

    @Single
    fun quickEntryParser(categorizer: Categorizer): QuickEntryParser = QuickEntryParser(categorizer)

    @Single
    fun budgetEngine(): BudgetEngine = BudgetEngine()

    @Single
    fun coachRules(): CoachRules = CoachRules()

    @Single
    fun reconciler(): Reconciler = Reconciler()

    @Single
    fun payoffSimulator(): PayoffSimulator = PayoffSimulator()

    @Factory
    fun observeBudgetStatus(
        budgets: BudgetRepository,
        expenses: ExpenseRepository,
        engine: BudgetEngine,
    ): ObserveBudgetStatus = ObserveBudgetStatus(budgets, expenses, engine)

    @Factory
    fun planCoachWeek(
        budgets: BudgetRepository,
        expenses: ExpenseRepository,
        cards: CardRepository,
        incomes: IncomeRepository,
        engine: BudgetEngine,
        rules: CoachRules,
    ): PlanCoachWeek = PlanCoachWeek(budgets, expenses, cards, incomes, engine, rules)

    @Factory
    fun observeMonthSummary(
        budgets: BudgetRepository,
        incomes: IncomeRepository,
        expenses: ExpenseRepository,
    ): ObserveMonthSummary = ObserveMonthSummary(budgets, incomes, expenses)

    @Factory
    fun coachNotificationPlanner(plan: PlanCoachWeek, clock: LocalClock): CoachNotificationPlanner =
        CoachNotificationPlanner(plan, clock)
}
