package com.cinch.di

import com.cinch.navigation.BudgetSetupRoute
import com.cinch.shared.budget.ObserveBudgetStatus
import com.cinch.shared.category.Categorizer
import com.cinch.shared.domain.repository.BudgetRepository
import com.cinch.shared.domain.repository.ExpenseRepository
import com.cinch.shared.parser.QuickEntryParser
import com.cinch.ui.budget.BudgetSetupViewModel
import com.cinch.ui.common.SystemTodayProvider
import com.cinch.ui.common.TodayProvider
import com.cinch.ui.home.HomeViewModel
import com.cinch.ui.root.RootViewModel
import kotlinx.coroutines.CoroutineDispatcher
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel
import org.koin.core.annotation.Module
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single

/** Ekranların ViewModel'leri ve yalnız UI'ın ihtiyaç duyduğu bağımlılıklar. */
@Module
@Configuration
class UiModule {

    @Single
    fun todayProvider(): TodayProvider = SystemTodayProvider

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
        observeBudgetStatus: ObserveBudgetStatus,
        parser: QuickEntryParser,
        categorizer: Categorizer,
        todayProvider: TodayProvider,
        @Named("ioDispatcher") ioDispatcher: CoroutineDispatcher,
    ): HomeViewModel = HomeViewModel(
        budgets = budgets,
        expenses = expenses,
        observeBudgetStatus = observeBudgetStatus,
        parser = parser,
        categorizer = categorizer,
        todayProvider = todayProvider,
        ioDispatcher = ioDispatcher,
    )
}
