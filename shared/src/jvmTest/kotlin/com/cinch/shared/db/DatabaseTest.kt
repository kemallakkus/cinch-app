package com.cinch.shared.db

import kotlin.test.Test
import kotlin.test.assertEquals

/** SQLDelight şemasının ve üretilen kodun gerçekten çalıştığını doğrular (JVM, in-memory). */
class DatabaseTest {

    @Test
    fun insertAndQueryExpense() {
        val db = createDatabase(DatabaseDriverFactory())
        db.cinchQueries.insertExpense(
            date_epoch_day = 20000,
            gross_minor = 19500,
            chargeable_fraction = 1.0,
            currency = "TRY",
            category = "MARKET",
            card_id = null,
            note = "",
            raw_text = "195 market",
        )
        val rows = db.cinchQueries.expensesBetween(from = 19999, to = 20001).executeAsList()
        assertEquals(1, rows.size)
        assertEquals(19500, rows.first().gross_minor)
    }

    @Test
    fun correctionUpsert() {
        val db = createDatabase(DatabaseDriverFactory())
        db.cinchQueries.upsertCorrection("qnbpay", "FATURA")
        assertEquals("FATURA", db.cinchQueries.correctionFor("qnbpay").executeAsOne())
    }
}
