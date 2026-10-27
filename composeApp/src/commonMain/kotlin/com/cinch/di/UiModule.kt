package com.cinch.di

import com.cinch.navigation.BudgetSetupRoute
import com.cinch.navigation.CardDetailRoute
import com.cinch.navigation.CardEditRoute
import com.cinch.navigation.IncomeEditRoute
import com.cinch.notifications.NotificationAccessChecker
import com.cinch.shared.budget.ObserveBudgetStatus
import com.cinch.shared.budget.ObserveMonthSummary
import com.cinch.shared.category.Categorizer
import com.cinch.shared.debt.PayoffSimulator
import com.cinch.shared.domain.repository.BudgetRepository
import com.cinch.shared.domain.repository.CardRepository
import com.cinch.shared.domain.repository.ExpenseRepository
import com.cinch.shared.domain.repository.IncomeRepository
import com.cinch.shared.parser.QuickEntryParser
import com.cinch.shared.reconcile.Reconciler
import com.cinch.shared.time.LocalClock
import com.cinch.ui.budget.BudgetSetupViewModel
import com.cinch.ui.cards.CardDetailViewModel
import com.cinch.ui.cards.CardEditViewModel
import com.cinch.ui.cards.CardsViewModel
import com.cinch.ui.home.HomeViewModel
import com.cinch.ui.month.IncomeEditViewModel
import com.cinch.ui.month.MonthViewModel
import com.cinch.ui.root.RootViewModel
import com.cinch.ui.settings.SettingsViewModel
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
    ): BudgetSetupViewModel = BudgetSetupViewModel(route, budgets)

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
        reconciler: Reconciler,
        simulator: PayoffSimulator,
        clock: LocalClock,
    ): CardDetailViewModel = CardDetailViewModel(route, cards, expenses, reconciler, simulator, clock)

    @KoinViewModel
    fun monthViewModel(
        observeMonthSummary: ObserveMonthSummary,
        incomes: IncomeRepository,
        clock: LocalClock,
    ): MonthViewModel = MonthViewModel(observeMonthSummary, incomes, clock)

    @KoinViewModel
    fun incomeEditViewModel(@InjectedParam route: IncomeEditRoute, incomes: IncomeRepository): IncomeEditViewModel =
        IncomeEditViewModel(route, incomes)

    @KoinViewModel
    fun settingsViewModel(budgets: BudgetRepository, access: NotificationAccessChecker): SettingsViewModel =
        SettingsViewModel(budgets, access)
}
