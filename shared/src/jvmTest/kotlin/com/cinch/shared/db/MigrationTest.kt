package com.cinch.shared.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Telefonda kurulu eski sürümün veritabanı yeni sürüme veri kaybetmeden geçebilmeli. */
class MigrationTest {

    /** Sürüm 1'deki bütçe tablosu (gelir tablosu yok, maaş günü bütçede). */
    private val v1BudgetTable = """
        CREATE TABLE budget_config (
            id INTEGER PRIMARY KEY DEFAULT 1,
            weekly_limit_minor INTEGER NOT NULL,
            currency TEXT NOT NULL DEFAULT 'TRY',
            week_start_iso INTEGER NOT NULL DEFAULT 1,
            weekly_goal_minor INTEGER,
            salary_day INTEGER
        );
    """.trimIndent()

    private fun v1Driver(salaryDay: Int?): JdbcSqliteDriver {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, v1BudgetTable, 0)
        driver.execute(
            null,
            "INSERT INTO budget_config(id, weekly_limit_minor, salary_day) VALUES (1, 70000, ${salaryDay ?: "NULL"})",
            0,
        )
        return driver
    }

    @Test
    fun schemaVersionIsTwo() {
        assertEquals(2L, CinchDatabase.Schema.version)
    }

    @Test
    fun v1To2_movesSalaryDayIntoIncomes() {
        val driver = v1Driver(salaryDay = 15)

        CinchDatabase.Schema.migrate(driver, 1, 2)

        val db = CinchDatabase(driver)
        val incomes = db.cinchQueries.allIncomes().executeAsList()
        assertEquals(1, incomes.size)
        assertEquals("Maaş", incomes.single().name)
        assertEquals(0L, incomes.single().amount_minor) // tutarı kullanıcı girer
        assertEquals(15L, incomes.single().day_of_month)
        assertEquals(null, db.cinchQueries.currentBudget().executeAsOne().salary_day)
    }

    @Test
    fun v1To2_withoutSalaryDay_createsEmptyIncomeTable() {
        val driver = v1Driver(salaryDay = null)

        CinchDatabase.Schema.migrate(driver, 1, 2)

        assertTrue(CinchDatabase(driver).cinchQueries.allIncomes().executeAsList().isEmpty())
    }
}
