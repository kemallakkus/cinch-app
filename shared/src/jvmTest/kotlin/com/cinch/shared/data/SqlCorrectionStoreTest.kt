package com.cinch.shared.data

import com.cinch.shared.category.Categorizer
import com.cinch.shared.domain.Category
import com.cinch.shared.parser.QuickEntryParser
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SqlCorrectionStoreTest {

    @Test
    fun learn_isNormalizedAndSurvivesNewInstance() {
        val db = newDatabase()
        SqlCorrectionStore(db).learn("Büfe", Category.YEMEK)

        // Yeni örnek = uygulama yeniden açıldı; önbellek veritabanından dolar.
        val reopened = SqlCorrectionStore(db)
        assertEquals(Category.YEMEK, reopened.categoryFor("bufe"))
        assertEquals(Category.YEMEK, reopened.categoryFor("BÜFE"))
        assertNull(reopened.categoryFor("market"))
    }

    @Test
    fun learn_overwritesPreviousCategory() {
        val store = SqlCorrectionStore(newDatabase())
        store.learn("qnbpay", Category.FATURA)
        store.learn("qnbpay", Category.EGLENCE)
        assertEquals(Category.EGLENCE, store.categoryFor("qnbpay"))
    }

    @Test
    fun parserLearnsCorrectionAcrossRestart() {
        val db = newDatabase()
        val today = LocalDate(2025, 1, 15)
        fun parser() = QuickEntryParser(Categorizer(corrections = SqlCorrectionStore(db)))

        val first = parser()
        val entry = first.parse("349 qnbpay", today)
        assertEquals(Category.DIGER, entry.category)

        Categorizer(corrections = SqlCorrectionStore(db)).learnFromCorrection(entry.tokens, Category.FATURA)

        assertEquals(Category.FATURA, parser().parse("120 qnbpay", today).category)
    }
}
