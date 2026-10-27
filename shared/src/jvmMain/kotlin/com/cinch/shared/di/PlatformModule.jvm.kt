package com.cinch.shared.di

import com.cinch.shared.db.DatabaseDriverFactory
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/** JVM hedefi yalnız testler için: her Koin uygulaması kendi bellek içi veritabanını alır. */
@Module
@Configuration
class PlatformModule {

    @Single
    fun databaseDriverFactory(): DatabaseDriverFactory = DatabaseDriverFactory()
}
