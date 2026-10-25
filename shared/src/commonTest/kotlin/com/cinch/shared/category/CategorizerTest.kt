package com.cinch.shared.category

import com.cinch.shared.domain.Category
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CategorizerTest {

    @Test
    fun dictionaryMatch() {
        val c = Categorizer()
        val r = c.categorize("market")
        assertEquals(Category.MARKET, r.category)
        assertEquals(1.0, r.confidence)
    }

    @Test
    fun turkishCharsNormalized() {
        val c = Categorizer()
        // "büfe" sözlükte; normalize → "bufe"
        assertEquals(Category.YEMEK, c.categorize("Büfe").category)
    }

    @Test
    fun unknown_isDigerZeroConfidence() {
        val c = Categorizer()
        val r = c.categorize("asdfqwer")
        assertEquals(Category.DIGER, r.category)
        assertEquals(0.0, r.confidence)
    }

    @Test
    fun voting_majorityWins() {
        val c = Categorizer()
        // iki yemek kelimesi, bir market → YEMEK
        val r = c.categorizeTokens(listOf("kahve", "ekmek", "migros"))
        assertEquals(Category.YEMEK, r.category)
        assertTrue(r.confidence > 0.5)
    }

    @Test
    fun learnsUnknownWord() {
        val c = Categorizer()
        c.learnFromCorrection(listOf("qnbpay"), Category.FATURA)
        assertEquals(Category.FATURA, c.categorize("qnbpay").category)
    }

    @Test
    fun correctionOverridesDictionary() {
        val store = InMemoryCorrectionStore()
        store.learn("kahve", Category.KISISEL) // kullanıcı düzeltmesi
        val c = Categorizer(corrections = store)
        // Sözlükte "kahve" YEMEK; düzeltme kazanmalı
        assertEquals(Category.KISISEL, c.categorize("kahve").category)
    }

    @Test
    fun learnSkipsKnownDictionaryWords() {
        val store = InMemoryCorrectionStore()
        val c = Categorizer(corrections = store)
        c.learnFromCorrection(listOf("market", "qnbpay"), Category.EV)
        // "market" sözlükte olduğundan öğrenilmez, "qnbpay" öğrenilir
        assertEquals(null, store.categoryFor("market"))
        assertEquals(Category.EV, store.categoryFor("qnbpay"))
    }
}
