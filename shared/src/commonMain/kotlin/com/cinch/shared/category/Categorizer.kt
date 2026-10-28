package com.cinch.shared.category

import com.cinch.shared.domain.Category

/** Kullanıcı düzeltmelerini saklayan arayüz (bellek içi veya SQLDelight destekli). */
interface CorrectionStore {
    /** Bir anahtar kelime için öğrenilmiş kategoriyi döner. */
    fun categoryFor(token: String): Category?

    /** Kullanıcı bir kaydı düzelttiğinde çağrılır; kelime→kategori öğrenilir. */
    fun learn(token: String, category: Category)

    /** Kayıtlar dışarıdan değiştiyse (yedekten geri yükleme) bellekteki kopyayı yeniler. */
    fun reload() = Unit
}

class InMemoryCorrectionStore : CorrectionStore {
    private val map = mutableMapOf<String, Category>()
    override fun categoryFor(token: String): Category? = map[TextNormalizer.normalize(token)]
    override fun learn(token: String, category: Category) {
        map[TextNormalizer.normalize(token)] = category
    }
}

/** Kategori tahmini sonucu. [confidence] 0.0–1.0 arası; eşleşme yoksa 0. */
data class CategoryResult(
    val category: Category,
    val confidence: Double,
    val matchedKeyword: String?,
)

/**
 * Anahtar kelime sözlüğü + kullanıcı düzeltmeleriyle kategori tahmini yapar.
 * Kullanıcı düzeltmeleri her zaman sözlüğü ezer (öncelik: öğrenilmiş > sözlük).
 */
class Categorizer(
    private val dictionary: KeywordDictionary = KeywordDictionary.turkishDefault(),
    private val corrections: CorrectionStore = InMemoryCorrectionStore(),
) {

    /** Serbest metni kategorize eder. */
    fun categorize(text: String): CategoryResult =
        categorizeTokens(TextNormalizer.tokenize(text))

    /** Hazır kelime listesini kategorize eder. */
    fun categorizeTokens(tokens: List<String>): CategoryResult {
        // 1) Önce kullanıcı düzeltmeleri: ilk eşleşen kazanır (en güçlü sinyal).
        for (token in tokens) {
            corrections.categoryFor(token)?.let {
                return CategoryResult(it, confidence = 1.0, matchedKeyword = token)
            }
        }

        // 2) Sözlük oylaması: en çok oyu alan kategori.
        val votes = mutableMapOf<Category, Int>()
        var firstMatch: String? = null
        for (token in tokens) {
            val cat = dictionary.categoryOf(token) ?: continue
            if (firstMatch == null) firstMatch = token
            votes[cat] = (votes[cat] ?: 0) + 1
        }

        if (votes.isEmpty()) {
            return CategoryResult(Category.DIGER, confidence = 0.0, matchedKeyword = null)
        }

        val totalVotes = votes.values.sum()
        val best = votes.maxBy { it.value }
        val confidence = best.value.toDouble() / totalVotes
        return CategoryResult(best.key, confidence, firstMatch)
    }

    /** Kullanıcı bir kaydın kategorisini düzelttiğinde: kelimelerden öğren. */
    fun learnFromCorrection(tokens: List<String>, correctCategory: Category) {
        // Sözlükte zaten bilinmeyen kelimeleri öğren (bilinen kelimeleri kirletme).
        for (token in tokens) {
            if (token.any { it.isDigit() }) continue
            if (!dictionary.contains(token)) {
                corrections.learn(token, correctCategory)
            }
        }
    }
}
