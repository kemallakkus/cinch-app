package com.cinch.di

import com.cinch.notifications.CoachNotificationSync
import com.cinch.notifications.NotificationAccessChecker
import com.cinch.notifications.NotificationScheduler
import com.cinch.shared.coach.CoachNotificationPlanner
import com.cinch.shared.domain.repository.BudgetRepository
import com.cinch.shared.domain.repository.CardRepository
import com.cinch.shared.domain.repository.ExpenseRepository
import com.cinch.shared.prefs.KeyValueStore
import com.cinch.shared.time.LocalClock
import com.cinch.ui.notifications.NotificationPromptViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.KoinViewModel
import org.koin.core.annotation.Module
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single

/**
 * Koç bildirimleri. Platform zamanlayıcısı ve izin kontrolü `AppPlatformModule`'den gelir.
 * Eşitleme kimse inject etmeden çalışmalı; bu yüzden Koin açılışında kurulup başlatılır.
 */
@Module
@Configuration
class NotificationsModule {

    /** Ekran kapandıktan sonra da süren işler için uygulama ömürlü scope. */
    @Single
    @Named("appScope")
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Single(createdAtStart = true)
    fun coachNotificationSync(
        planner: CoachNotificationPlanner,
        scheduler: NotificationScheduler,
        budgets: BudgetRepository,
        expenses: ExpenseRepository,
        cards: CardRepository,
        clock: LocalClock,
        @Named("appScope") scope: CoroutineScope,
    ): CoachNotificationSync =
        CoachNotificationSync(planner, scheduler, budgets, expenses, cards, clock, scope).also { it.start() }

    @KoinViewModel
    fun notificationPromptViewModel(
        access: NotificationAccessChecker,
        prefs: KeyValueStore,
        clock: LocalClock,
    ): NotificationPromptViewModel = NotificationPromptViewModel(access, prefs, clock)
}
