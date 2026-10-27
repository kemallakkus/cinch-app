package com.cinch.shared.di

import android.annotation.SuppressLint
import android.content.Context
import com.cinch.shared.db.DatabaseDriverFactory
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

// Yalnızca application context tutulur; Activity sızıntısı olmaz.
@SuppressLint("StaticFieldLeak")
private lateinit var appContext: Context

/** Koin başlamadan önce Application.onCreate içinde çağrılmalı. */
fun initPlatform(context: Context) {
    appContext = context.applicationContext
}

@Module
@Configuration
class PlatformModule {

    @Single
    fun databaseDriverFactory(): DatabaseDriverFactory = DatabaseDriverFactory(appContext)
}
