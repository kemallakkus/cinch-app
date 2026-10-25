package com.cinch.shared.db

import app.cash.sqldelight.db.SqlDriver

/**
 * Platforma özgü SQLDelight sürücüsünü üretir (Android: AndroidSqliteDriver,
 * iOS: NativeSqliteDriver, JVM testleri: JdbcSqliteDriver). Veritabanı yerelde yaşar.
 */
expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

/** Sürücüden [CinchDatabase] örneği oluşturur. */
fun createDatabase(factory: DatabaseDriverFactory): CinchDatabase {
    val driver = factory.createDriver()
    CinchDatabase.Schema.create(driver)
    return CinchDatabase(driver)
}
