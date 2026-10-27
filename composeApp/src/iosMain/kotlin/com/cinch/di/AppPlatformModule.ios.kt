package com.cinch.di

import com.cinch.notifications.IosNotificationAccess
import com.cinch.notifications.IosNotificationScheduler
import com.cinch.notifications.NotificationAccessChecker
import com.cinch.notifications.NotificationScheduler
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/** Uygulama katmanının platform bağımlılıkları. */
@Module
@Configuration
class AppPlatformModule {

    @Single
    fun notificationScheduler(): NotificationScheduler = IosNotificationScheduler()

    @Single
    fun notificationAccess(): NotificationAccessChecker = IosNotificationAccess()
}
