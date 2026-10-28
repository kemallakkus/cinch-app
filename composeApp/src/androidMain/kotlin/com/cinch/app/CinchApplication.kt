package com.cinch.app

import android.app.Application
import com.cinch.di.StartKoin
import com.cinch.notifications.CoachReplanWorker
import com.cinch.shared.di.initPlatform
import com.cinch.widget.TodayWidgetUpdater

class CinchApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initPlatform(this)
        // Koin açılışta bildirim eşitlemesini de başlatır (NotificationsModule).
        StartKoin.doInitKoin()
        CoachReplanWorker.ensureScheduled(this)
        // Harcama ve bütçe değiştikçe ana ekran widget'ı yenilenir.
        TodayWidgetUpdater.start(this)
    }
}
