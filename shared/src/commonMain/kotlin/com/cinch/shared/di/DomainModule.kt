package com.cinch.shared.di

import com.cinch.shared.cards.ObserveCardOverviews
import com.cinch.shared.coach.NotificationPreferencesStore
import com.cinch.shared.backup.BackupTracker
import com.cinch.shared.budget.BudgetEngine
import com.cinch.shared.budget.CashBalanceStore
import com.cinch.shared.budget.ForecastCashFlow
import com.cinch.shared.budget.ObserveBudgetStatus
import com.cinch.shared.budget.ActiveFixedExpenses
import com.cinch.shared.budget.FixedPayments
import com.cinch.shared.budget.ObserveMonthSummary
import com.cinch.shared.budget.ObserveSpendingBreakdown
import com.cinch.shared.category.Categorizer
import com.cinch.shared.category.CorrectionStore
import com.cinch.shared.category.KeywordDictionary
import com.cinch.shared.language.LanguagePack
import com.cinch.shared.language.LanguagePacks
import com.cinch.shared.language.deviceLanguageCode
import com.cinch.shared.coach.CoachNotificationPlanner
import com.cinch.shared.coach.CoachRules
import com.cinch.shared.coach.PlanCoachWeek
import com.cinch.shared.domain.repository.BudgetRepository
import com.cinch.shared.domain.repository.CardRepository
import com.cinch.shared.domain.repository.CardStatementRepository
import com.cinch.shared.domain.repository.ExpenseRepository
import com.cinch.shared.domain.repository.FixedExpenseRepository
import com.cinch.shared.domain.repository.IncomeRepository
import com.cinch.shared.domain.repository.SavingsRepository
import com.cinch.shared.gamification.BadgeStore
import com.cinch.shared.gamification.ObserveGamification
import com.cinch.shared.debt.PayoffSimulator
import com.cinch.shared.parser.QuickEntryParser
import com.cinch.shared.prefs.KeyValueStore
import com.cinch.shared.receipt.ReceiptParser
import com.cinch.shared.statement.StatementReader
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

    /**
     * Ayrıştırıcıların kelimeleri: telefonun dili önce, Türkçe ve İngilizce her zaman anlaşılır (kullanıcı hangi
     * dilde yazarsa yazsın; yurt dışı fişi de okunur).
     */
    @Single
    fun languagePack(): LanguagePack = LanguagePacks.active(deviceLanguageCode())

    @Single
    fun keywordDictionary(language: LanguagePack): KeywordDictionary = KeywordDictionary.of(language)

    /** Düzeltmeler veritabanından gelir; kullanıcı düzelttikçe kalıcı olarak öğrenir. */
    @Single
    fun categorizer(dictionary: KeywordDictionary, corrections: CorrectionStore): Categorizer =
        Categorizer(dictionary, corrections)

    @Single
    fun quickEntryParser(categorizer: Categorizer, language: LanguagePack): QuickEntryParser =
        QuickEntryParser(categorizer, language = language)

    @Single
    fun receiptParser(categorizer: Categorizer, language: LanguagePack): ReceiptParser = ReceiptParser(categorizer, language)

    @Single
    fun statementReader(language: LanguagePack): StatementReader = StatementReader(language = language)

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
        fixedExpenses: FixedExpenseRepository,
        statements: CardStatementRepository,
        backups: BackupTracker,
        engine: BudgetEngine,
        rules: CoachRules,
        preferences: NotificationPreferencesStore,
    ): PlanCoachWeek = PlanCoachWeek(
        budgets, expenses, cards, incomes, fixedExpenses, statements, backups, engine, rules, preferences = preferences,
    )

    @Factory
    fun observeCardOverviews(
        cards: CardRepository,
        expenses: ExpenseRepository,
        statements: CardStatementRepository,
        clock: LocalClock,
    ): ObserveCardOverviews = ObserveCardOverviews(cards, expenses, statements, clock)

    @Single
    fun notificationPreferences(prefs: KeyValueStore): NotificationPreferencesStore = NotificationPreferencesStore(prefs)

    @Factory
    fun observeMonthSummary(
        budgets: BudgetRepository,
        incomes: IncomeRepository,
        fixedExpenses: FixedExpenseRepository,
        expenses: ExpenseRepository,
        savings: SavingsRepository,
    ): ObserveMonthSummary = ObserveMonthSummary(budgets, incomes, fixedExpenses, expenses, savings)

    @Factory
    fun observeSpendingBreakdown(budgets: BudgetRepository, expenses: ExpenseRepository): ObserveSpendingBreakdown =
        ObserveSpendingBreakdown(budgets, expenses)

    @Factory
    fun fixedPayments(expenses: ExpenseRepository): FixedPayments = FixedPayments(expenses)

    @Factory
    fun forecastCashFlow(
        budgets: BudgetRepository,
        incomes: IncomeRepository,
        fixedExpenses: FixedExpenseRepository,
        expenses: ExpenseRepository,
        cards: CardRepository,
        statements: CardStatementRepository,
    ): ForecastCashFlow = ForecastCashFlow(budgets, incomes, fixedExpenses, expenses, cards, statements)

    @Single
    fun cashBalanceStore(prefs: KeyValueStore): CashBalanceStore = CashBalanceStore(prefs)

    @Single
    fun badgeStore(prefs: KeyValueStore): BadgeStore = BadgeStore(prefs)

    @Factory
    fun observeGamification(
        budgets: BudgetRepository,
        expenses: ExpenseRepository,
        savings: SavingsRepository,
        fixedExpenses: FixedExpenseRepository,
        backups: BackupTracker,
        badgeStore: BadgeStore,
    ): ObserveGamification = ObserveGamification(budgets, expenses, savings, fixedExpenses, backups, badgeStore)

    @Factory
    fun activeFixedExpenses(
        fixedExpenses: FixedExpenseRepository,
        expenses: ExpenseRepository,
    ): ActiveFixedExpenses = ActiveFixedExpenses(fixedExpenses, expenses)

    @Factory
    fun coachNotificationPlanner(plan: PlanCoachWeek, clock: LocalClock): CoachNotificationPlanner =
        CoachNotificationPlanner(plan, clock)
}
