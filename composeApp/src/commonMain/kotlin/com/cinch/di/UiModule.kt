package com.cinch.di

import com.cinch.navigation.BudgetSetupRoute
import com.cinch.navigation.CardDetailRoute
import com.cinch.navigation.CardEditRoute
import com.cinch.navigation.DebtDetailRoute
import com.cinch.navigation.ExpenseEditRoute
import com.cinch.navigation.FixedEditRoute
import com.cinch.navigation.HistoryRoute
import com.cinch.navigation.IncomeEditRoute
import com.cinch.navigation.InsightsRoute
import com.cinch.navigation.ReceiptReviewRoute
import com.cinch.navigation.StatementReviewRoute
import com.cinch.notifications.NotificationAccessChecker
import com.cinch.shared.budget.ObserveBudgetStatus
import com.cinch.shared.budget.ActiveFixedExpenses
import com.cinch.shared.budget.FixedPayments
import com.cinch.shared.budget.ObserveMonthSummary
import com.cinch.shared.budget.ObserveSpendingBreakdown
import com.cinch.shared.category.Categorizer
import com.cinch.shared.debt.PayoffSimulator
import com.cinch.shared.domain.repository.BudgetRepository
import com.cinch.shared.domain.repository.CardRepository
import com.cinch.shared.domain.repository.CardStatementRepository
import com.cinch.shared.domain.repository.ExpenseRepository
import com.cinch.shared.domain.repository.FixedExpenseRepository
import com.cinch.shared.domain.repository.IncomeRepository
import com.cinch.shared.parser.QuickEntryParser
import com.cinch.shared.receipt.ReceiptParser
import com.cinch.shared.statement.StatementReader
import com.cinch.shared.reconcile.Reconciler
import com.cinch.shared.time.LocalClock
import com.cinch.ui.budget.BudgetSetupViewModel
import com.cinch.ui.cards.CardDetailViewModel
import com.cinch.ui.cards.CardEditViewModel
import com.cinch.ui.cards.CardsViewModel
import com.cinch.ui.history.ExpenseEditViewModel
import com.cinch.ui.history.HistoryViewModel
import com.cinch.ui.home.HomeViewModel
import com.cinch.ui.insights.InsightsViewModel
import com.cinch.ui.month.DebtDetailViewModel
import com.cinch.ui.month.FixedEditViewModel
import com.cinch.ui.month.IncomeEditViewModel
import com.cinch.ui.month.MonthViewModel
import com.cinch.ui.receipt.ReceiptReviewViewModel
import com.cinch.ui.root.RootViewModel
import com.cinch.ui.settings.SettingsViewModel
import com.cinch.ui.statement.StatementReviewViewModel
import kotlinx.coroutines.CoroutineDispatcher
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel
import org.koin.core.annotation.Module
import org.koin.core.annotation.Named

/** Ekranların ViewModel'leri ve yalnız UI'ın ihtiyaç duyduğu bağımlılıklar. */
@Module
@Configuration
class UiModule {

    @KoinViewModel
    fun rootViewModel(budgets: BudgetRepository): RootViewModel = RootViewModel(budgets)

    @KoinViewModel
    fun budgetSetupViewModel(
        @InjectedParam route: BudgetSetupRoute,
        budgets: BudgetRepository,
        incomes: IncomeRepository,
        activeFixed: ActiveFixedExpenses,
        clock: LocalClock,
    ): BudgetSetupViewModel = BudgetSetupViewModel(route, budgets, incomes, activeFixed, clock)

    @KoinViewModel
    fun homeViewModel(
        budgets: BudgetRepository,
        expenses: ExpenseRepository,
        cards: CardRepository,
        observeBudgetStatus: ObserveBudgetStatus,
        parser: QuickEntryParser,
        categorizer: Categorizer,
        clock: LocalClock,
        @Named("ioDispatcher") ioDispatcher: CoroutineDispatcher,
    ): HomeViewModel = HomeViewModel(
        budgets = budgets,
        expenses = expenses,
        cards = cards,
        observeBudgetStatus = observeBudgetStatus,
        parser = parser,
        categorizer = categorizer,
        clock = clock,
        ioDispatcher = ioDispatcher,
    )

    @KoinViewModel
    fun cardsViewModel(cards: CardRepository): CardsViewModel = CardsViewModel(cards)

    @KoinViewModel
    fun cardEditViewModel(@InjectedParam route: CardEditRoute, cards: CardRepository): CardEditViewModel =
        CardEditViewModel(route, cards)

    @KoinViewModel
    fun cardDetailViewModel(
        @InjectedParam route: CardDetailRoute,
        cards: CardRepository,
        expenses: ExpenseRepository,
        statements: CardStatementRepository,
        reconciler: Reconciler,
        simulator: PayoffSimulator,
        clock: LocalClock,
    ): CardDetailViewModel = CardDetailViewModel(route, cards, expenses, statements, reconciler, simulator, clock)

    @KoinViewModel
    fun statementReviewViewModel(
        @InjectedParam route: StatementReviewRoute,
        reader: StatementReader,
        cards: CardRepository,
        statements: CardStatementRepository,
        expenses: ExpenseRepository,
        categorizer: Categorizer,
        entryParser: QuickEntryParser,
        clock: LocalClock,
        @Named("ioDispatcher") ioDispatcher: CoroutineDispatcher,
    ): StatementReviewViewModel = StatementReviewViewModel(
        route = route,
        reader = reader,
        cards = cards,
        statements = statements,
        expenses = expenses,
        categorizer = categorizer,
        entryParser = entryParser,
        clock = clock,
        ioDispatcher = ioDispatcher,
    )

    @KoinViewModel
    fun monthViewModel(
        observeMonthSummary: ObserveMonthSummary,
        observeSpendingBreakdown: ObserveSpendingBreakdown,
        incomes: IncomeRepository,
        fixedPayments: FixedPayments,
        clock: LocalClock,
    ): MonthViewModel = MonthViewModel(observeMonthSummary, observeSpendingBreakdown, incomes, fixedPayments, clock)

    @KoinViewModel
    fun fixedEditViewModel(
        @InjectedParam route: FixedEditRoute,
        fixedExpenses: FixedExpenseRepository,
        cards: CardRepository,
    ): FixedEditViewModel = FixedEditViewModel(route, fixedExpenses, cards)

    @KoinViewModel
    fun incomeEditViewModel(@InjectedParam route: IncomeEditRoute, incomes: IncomeRepository): IncomeEditViewModel =
        IncomeEditViewModel(route, incomes)

    @KoinViewModel
    fun settingsViewModel(budgets: BudgetRepository, access: NotificationAccessChecker): SettingsViewModel =
        SettingsViewModel(budgets, access)

    @KoinViewModel
    fun debtDetailViewModel(
        @InjectedParam route: DebtDetailRoute,
        fixedExpenses: FixedExpenseRepository,
        expenses: ExpenseRepository,
        fixedPayments: FixedPayments,
        clock: LocalClock,
    ): DebtDetailViewModel = DebtDetailViewModel(route, fixedExpenses, expenses, fixedPayments, clock)

    @KoinViewModel
    fun expenseEditViewModel(
        @InjectedParam route: ExpenseEditRoute,
        expenses: ExpenseRepository,
        cards: CardRepository,
        fixedExpenses: FixedExpenseRepository,
        parser: QuickEntryParser,
        categorizer: Categorizer,
        clock: LocalClock,
        @Named("ioDispatcher") ioDispatcher: CoroutineDispatcher,
    ): ExpenseEditViewModel = ExpenseEditViewModel(
        route = route,
        expenses = expenses,
        cards = cards,
        fixedExpenses = fixedExpenses,
        parser = parser,
        categorizer = categorizer,
        clock = clock,
        ioDispatcher = ioDispatcher,
    )

    @KoinViewModel
    fun historyViewModel(
        @InjectedParam route: HistoryRoute,
        expenses: ExpenseRepository,
        cards: CardRepository,
        clock: LocalClock,
    ): HistoryViewModel = HistoryViewModel(route, expenses, cards, clock)

    @KoinViewModel
    fun receiptReviewViewModel(
        @InjectedParam route: ReceiptReviewRoute,
        receiptParser: ReceiptParser,
        entryParser: QuickEntryParser,
        categorizer: Categorizer,
        expenses: ExpenseRepository,
        cards: CardRepository,
        fixedExpenses: FixedExpenseRepository,
        fixedPayments: FixedPayments,
        clock: LocalClock,
        @Named("ioDispatcher") ioDispatcher: CoroutineDispatcher,
    ): ReceiptReviewViewModel = ReceiptReviewViewModel(
        route = route,
        receiptParser = receiptParser,
        entryParser = entryParser,
        categorizer = categorizer,
        expenses = expenses,
        cards = cards,
        fixedExpenses = fixedExpenses,
        fixedPayments = fixedPayments,
        clock = clock,
        ioDispatcher = ioDispatcher,
    )

    @KoinViewModel
    fun insightsViewModel(
        @InjectedParam route: InsightsRoute,
        observeSpendingBreakdown: ObserveSpendingBreakdown,
        clock: LocalClock,
    ): InsightsViewModel = InsightsViewModel(route, observeSpendingBreakdown, clock)
}
