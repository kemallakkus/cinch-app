package com.cinch.di

import android.content.Context
import com.cinch.notifications.AndroidNotificationAccess
import com.cinch.notifications.AndroidNotificationScheduler
import com.cinch.notifications.NotificationAccessChecker
import com.cinch.notifications.NotificationScheduler
import com.cinch.shared.prefs.KeyValueStore
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/** Uygulama katmanının platform bağımlılıkları. `Context` shared'in `PlatformModule`'ünden gelir. */
@Module
@Configuration
class AppPlatformModule {

    @Single
    fun notificationScheduler(context: Context): NotificationScheduler = AndroidNotificationScheduler(context)

    @Single
    fun notificationAccess(context: Context, prefs: KeyValueStore): NotificationAccessChecker =
        AndroidNotificationAccess(context, prefs)
}
