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
    fun schemaVersionIsFive() {
        assertEquals(5L, CinchDatabase.Schema.version)
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

    /** Sürüm 2'deki harcama tablosu (sabit gider bağlantısı yok). */
    private val v2ExpenseTable = """
        CREATE TABLE expense (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            date_epoch_day INTEGER NOT NULL,
            gross_minor INTEGER NOT NULL,
            chargeable_fraction REAL NOT NULL DEFAULT 1.0,
            currency TEXT NOT NULL DEFAULT 'TRY',
            category TEXT NOT NULL DEFAULT 'DIGER',
            card_id INTEGER,
            note TEXT NOT NULL DEFAULT '',
            raw_text TEXT NOT NULL DEFAULT '',
            tokens TEXT NOT NULL DEFAULT ''
        );
    """.trimIndent()

    @Test
    fun v2To3_addsFixedExpenses_keepsOldExpenses() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, v2ExpenseTable, 0)
        driver.execute(null, "INSERT INTO expense(date_epoch_day, gross_minor) VALUES (20103, 19500)", 0)

        CinchDatabase.Schema.migrate(driver, 2, 3)

        val queries = CinchDatabase(driver).cinchQueries
        val old = queries.expensesBetween(20103, 20103).executeAsList().single()
        assertEquals(19_500L, old.gross_minor)
        assertEquals(null, old.fixed_expense_id)
        assertTrue(queries.allFixedExpenses().executeAsList().isEmpty())
    }

    @Test
    fun v3To4_existingFixedExpensesBecomeMonthly() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(
            null,
            """
            CREATE TABLE fixed_expense (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                amount_minor INTEGER NOT NULL,
                currency TEXT NOT NULL DEFAULT 'TRY',
                day_of_month INTEGER NOT NULL,
                kind TEXT NOT NULL DEFAULT 'OTHER',
                card_id INTEGER
            );
            """.trimIndent(),
            0,
        )
        driver.execute(null, "INSERT INTO fixed_expense(name, amount_minor, day_of_month, kind) VALUES ('Kira', 1500000, 1, 'RENT')", 0)

        CinchDatabase.Schema.migrate(driver, 3, 4)

        val row = CinchDatabase(driver).cinchQueries.allFixedExpenses().executeAsList().single()
        assertEquals("MONTHLY", row.plan)
        assertEquals(0L, row.paid_before)
        assertEquals(null, row.total_installments)
        assertEquals(null, row.debt_minor)
    }

    @Test
    fun v4To5_cardsKeepTheirData_andStatementsTableExists() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(
            null,
            """
            CREATE TABLE card (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                currency TEXT NOT NULL DEFAULT 'TRY',
                carried_balance_minor INTEGER NOT NULL DEFAULT 0,
                monthly_rate_percent REAL NOT NULL DEFAULT 4.9,
                statement_day INTEGER
            );
            """.trimIndent(),
            0,
        )
        driver.execute(null, "INSERT INTO card(name, carried_balance_minor, statement_day) VALUES ('QNB', 500000, 26)", 0)

        CinchDatabase.Schema.migrate(driver, 4, 5)

        val queries = CinchDatabase(driver).cinchQueries
        val card = queries.allCards().executeAsList().single()
        assertEquals("QNB", card.name)
        assertEquals(26L, card.statement_day)
        assertEquals(null, card.last4)
        assertTrue(queries.statementsForCard(card.id).executeAsList().isEmpty())
    }
}
