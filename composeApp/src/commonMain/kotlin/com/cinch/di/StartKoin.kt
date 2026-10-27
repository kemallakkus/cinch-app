package com.cinch.di

import org.koin.plugin.module.dsl.startKoin

/** Uygulama açılışında bir kez çağrılır (Android: Application.onCreate, iOS: App.init). */
object StartKoin {
    fun doInitKoin() {
        startKoin<CinchKoinApp> { }
    }
}
