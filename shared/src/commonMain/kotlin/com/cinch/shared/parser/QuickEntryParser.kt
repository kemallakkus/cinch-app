package com.cinch.shared.parser

import com.cinch.shared.category.Categorizer
import com.cinch.shared.category.TextNormalizer
import com.cinch.shared.domain.Category
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Hızlı kayıttan ayrıştırılmış sonuç.
 *
 * @param grossAmount algılanan toplam tutar (null = tutar bulunamadı).
 * @param chargeableFraction kullanıcının sınıra sayılan payı (iade/paylaşım sonrası).
 * @param date algılanan tarih (varsayılan: bugün).
 */
data class ParsedEntry(
    val rawText: String,
    val grossAmount: Money?,
    val chargeableFraction: Double,
    val date: LocalDate,
    val category: Category,
    val categoryConfidence: Double,
    val tokens: List<String>,
) {
    val chargeableAmount: Money?
        get() = grossAmount?.times(chargeableFraction)
}

/**
 * "195 market", "625 yemek qnb sanal yarısı geri geldi", "dün 120 su ekmek" gibi kısa metinleri
 * regex + anahtar kelime ile ayrıştırır. Tarih/iade göreli olduğundan [parse] bugünün tarihini alır.
 */
class QuickEntryParser(
    private val categorizer: Categorizer = Categorizer(),
    private val defaultCurrency: Currency = Currency.TRY,
) {

    fun parse(raw: String, today: LocalDate, currency: Currency = defaultCurrency): ParsedEntry {
        val tokens = TextNormalizer.tokenize(raw)
        val normalized = tokens.map { TextNormalizer.normalize(it) }

        val amount = extractAmount(tokens)?.let { Money.ofDouble(it, currency) }
        val date = extractDate(normalized, today)
        val fraction = extractChargeableFraction(normalized)

        // Kategorilendirme: sayısal ve tarih/pay anahtar kelimeleri hariç kelimeler.
        val wordTokens = tokens.filter { tok ->
            val n = TextNormalizer.normalize(tok)
            !tok.any { it.isDigit() } && n !in DATE_WORDS && n !in FRACTION_STOPWORDS
        }
        val cat = categorizer.categorizeTokens(wordTokens)

        return ParsedEntry(
            rawText = raw,
            grossAmount = amount,
            chargeableFraction = fraction,
            date = date,
            category = cat.category,
            categoryConfidence = cat.confidence,
            tokens = wordTokens.map { TextNormalizer.normalize(it) },
        )
    }

    // ───────────────────────── Tutar ─────────────────────────

    private val amountRegex = Regex("^\\d[\\d.,]*$")

    private fun extractAmount(tokens: List<String>): Double? {
        val token = tokens.firstOrNull { amountRegex.matches(it) } ?: return null
        return parseNumber(token)
    }

    /** Türkçe öncelikli sayı ayrıştırma: "1.250" → 1250, "120,50" → 120.5, "120.50" → 120.5. */
    internal fun parseNumber(token: String): Double? {
        val hasComma = token.contains(',')
        val hasDot = token.contains('.')
        val cleaned = when {
            hasComma && hasDot -> token.replace(".", "").replace(',', '.') // nokta binlik, virgül ondalık
            hasComma -> token.replace(',', '.')                             // virgül ondalık
            hasDot -> {
                val afterDot = token.substringAfterLast('.')
                if (afterDot.length == 3) token.replace(".", "")            // binlik ayıracı
                else token                                                   // ondalık
            }
            else -> token
        }
        return cleaned.toDoubleOrNull()
    }

    // ───────────────────────── Tarih ─────────────────────────

    private fun extractDate(normalized: List<String>, today: LocalDate): LocalDate {
        for ((i, word) in normalized.withIndex()) {
            when (word) {
                "bugun" -> return today
                "dun" -> return today.minusDays(1)
                "onceki", "evvelsi", "evvelki" -> {
                    // "önceki gün" / "evvelsi gün"
                    val next = normalized.getOrNull(i + 1)
                    if (next == "gun" || word == "evvelsi") return today.minusDays(2)
                }
            }
        }
        return today
    }

    // ──────────────────── Pay / İade oranı ────────────────────

    /**
     * Sınıra sayılan payı hesaplar. Varsayılan 1.0 (tamamı kullanıcıya ait).
     * - İade: "yarısı geri geldi" → 0.5, "tamamı geri geldi" → 0.0
     * - Paylaşım: "yarı yarıya", "böldük", "paylaştık" → 0.5
     */
    internal fun extractChargeableFraction(normalized: List<String>): Double {
        val text = normalized.joinToString(" ")

        // Paylaşım kalıpları → doğrudan pay.
        if (text.contains("yari yariya") || "boluştuk" in text ||
            "bolustuk" in text || "paylastik" in text || "ortak" in text
        ) {
            return 0.5
        }

        val reimbursed = REIMBURSE_PHRASES.any { text.contains(it) }
        if (!reimbursed) return 1.0

        // İade oranı kelimesi ara.
        val returnedFraction = when {
            "yarisi" in text || "yarim" in text -> 0.5
            "ucte biri" in text -> 1.0 / 3.0
            "ceyregi" in text -> 0.25
            "tamami" in text || "hepsi" in text -> 1.0
            else -> 1.0 // "geri geldi" ama oran belirtilmemiş → tamamı iade varsayımı
        }
        return (1.0 - returnedFraction).coerceIn(0.0, 1.0)
    }

    companion object {
        private val DATE_WORDS = setOf("bugun", "dun", "onceki", "evvelsi", "evvelki", "gun")
        private val FRACTION_STOPWORDS = setOf(
            "geri", "geldi", "gelecek", "gelir", "iade", "yarisi", "yarim", "ucte", "biri",
            "ceyregi", "tamami", "hepsi", "yari", "yariya", "bolustuk", "paylastik", "ortak",
        )
        private val REIMBURSE_PHRASES = listOf("geri geldi", "geri gelecek", "geri gelir", "iade")
    }
}

/** kotlinx-datetime için küçük yardımcılar (tekrar kullanılmak üzere). */
internal fun LocalDate.minusDays(n: Int): LocalDate = this.minus(DatePeriod(days = n))

internal fun LocalDate.plusDays(n: Int): LocalDate = this.plus(DatePeriod(days = n))
