package com.cinch.app

import android.app.Application
import com.cinch.di.StartKoin
import com.cinch.shared.di.initPlatform

class CinchApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initPlatform(this)
        StartKoin.doInitKoin()
    }
}
