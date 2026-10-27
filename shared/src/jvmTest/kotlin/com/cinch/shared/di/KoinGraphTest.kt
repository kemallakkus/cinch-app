package com.cinch.shared.di

import com.cinch.shared.budget.BudgetConfig
import com.cinch.shared.budget.ObserveBudgetStatus
import com.cinch.shared.budget.FixedPayments
import com.cinch.shared.budget.ObserveMonthSummary
import com.cinch.shared.category.CorrectionStore
import com.cinch.shared.coach.CoachNotificationPlanner
import com.cinch.shared.coach.NotificationType
import com.cinch.shared.coach.PlanCoachWeek
import com.cinch.shared.data.SqlCorrectionStore
import com.cinch.shared.domain.repository.BudgetRepository
import com.cinch.shared.domain.repository.ExpenseRepository
import com.cinch.shared.domain.repository.FixedExpenseRepository
import com.cinch.shared.domain.repository.IncomeRepository
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.shared.parser.QuickEntryParser
import com.cinch.shared.prefs.KeyValueStore
import com.cinch.shared.time.LocalClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.koin.core.KoinApplication
import org.koin.plugin.module.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Uygulamadaki gibi `@Configuration` modüllerini otomatik toplayan test girişi. */
@org.koin.core.annotation.KoinApplication
class SharedTestKoinApp

class KoinGraphTest {

    private inline fun withKoin(block: KoinApplication.() -> Unit) {
        val app = koinApplication<SharedTestKoinApp>()
        try {
            app.block()
        } finally {
            app.close()
        }
    }

    @Test
    fun everyDefinitionResolves() = withKoin {
        koin.get<ObserveBudgetStatus>()
        koin.get<PlanCoachWeek>()
        koin.get<QuickEntryParser>()
        koin.get<CoachNotificationPlanner>()
        koin.get<ObserveMonthSummary>()
        koin.get<IncomeRepository>()
        koin.get<FixedExpenseRepository>()
        koin.get<FixedPayments>()
        koin.get<LocalClock>()
        koin.get<KeyValueStore>()
        assertIs<SqlCorrectionStore>(koin.get<CorrectionStore>())
        assertSame(koin.get<ExpenseRepository>(), koin.get<ExpenseRepository>())
    }

    @Test
    fun graphWorksEndToEnd() = withKoin {
        runTest {
            val today = LocalDate(2025, 1, 13)
            koin.get<BudgetRepository>().save(BudgetConfig(weeklyLimit = Money.of(700, Currency.TRY)))
            val entry = koin.get<QuickEntryParser>().parse("140 market", today).toExpense()!!
            koin.get<ExpenseRepository>().add(entry)

            val status = koin.get<ObserveBudgetStatus>()(today).first()!!
            assertEquals(Money.of(140, Currency.TRY), status.weekSpent)

            val plan = koin.get<PlanCoachWeek>()(today)
            assertTrue(plan.any { it.type == NotificationType.MIDDAY_REMAINING })
        }
    }
}
