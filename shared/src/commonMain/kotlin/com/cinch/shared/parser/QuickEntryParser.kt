package com.cinch.shared.parser

import com.cinch.shared.category.Categorizer
import com.cinch.shared.category.TextNormalizer
import com.cinch.shared.domain.Card
import com.cinch.shared.domain.Category
import com.cinch.shared.domain.Expense
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.shared.money.MoneyInput
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Hızlı kayıttan ayrıştırılmış sonuç.
 *
 * @param grossAmount algılanan toplam tutar (null = tutar bulunamadı).
 * @param chargeableFraction kullanıcının sınıra sayılan payı (iade/paylaşım sonrası).
 * @param date algılanan tarih (varsayılan: bugün).
 * @param tokens kategori ve alışkanlık için sade kelimeler (kart adı hariç).
 * @param cardId metinde adı geçen kart; yoksa ya da belirsizse null.
 */
data class ParsedEntry(
    val rawText: String,
    val grossAmount: Money?,
    val chargeableFraction: Double,
    val date: LocalDate,
    val category: Category,
    val categoryConfidence: Double,
    val tokens: List<String>,
    val cardId: Long? = null,
) {
    val chargeableAmount: Money?
        get() = grossAmount?.times(chargeableFraction)

    /** Kaydedilecek harcamaya çevirir. Tutar bulunamadıysa null (kaydedilecek bir şey yok). */
    fun toExpense(cardId: Long? = this.cardId, note: String = ""): Expense? {
        val amount = grossAmount ?: return null
        return Expense(
            date = date,
            grossAmount = amount,
            chargeableFraction = chargeableFraction,
            category = category,
            cardId = cardId,
            note = note,
            tokens = tokens,
            rawText = rawText,
        )
    }
}

/**
 * "195 market", "625 yemek qnb sanal yarısı geri geldi", "dün 120 su ekmek" gibi kısa metinleri
 * regex + anahtar kelime ile ayrıştırır. Tarih/iade göreli olduğundan [parse] bugünün tarihini alır.
 */
class QuickEntryParser(
    private val categorizer: Categorizer = Categorizer(),
    private val defaultCurrency: Currency = Currency.TRY,
) {

    /** @param cards kullanıcının kartları; metinde adı geçen kart [ParsedEntry.cardId] olur. */
    fun parse(
        raw: String,
        today: LocalDate,
        currency: Currency = defaultCurrency,
        cards: List<Card> = emptyList(),
    ): ParsedEntry {
        val tokens = splitGluedAmount(TextNormalizer.tokenize(raw))
        val normalized = tokens.map { TextNormalizer.normalize(it) }

        val amount = extractAmount(tokens)?.let { Money.ofDouble(it, currency) }
        val dateMatch = extractDate(normalized, today)
        val date = dateMatch.date
        val fraction = extractChargeableFraction(normalized)

        // Kategorilendirme: sayısal ve tarih/pay anahtar kelimeleri hariç kelimeler.
        val candidates = tokens.filterIndexed { i, tok ->
            val n = TextNormalizer.normalize(tok)
            !tok.any { it.isDigit() } && n !in DATE_WORDS && n !in FRACTION_STOPWORDS && i !in dateMatch.wordIndices
        }
        // Kart adı kategori değildir: "qnb" ne sınıflandırmaya ne öğrenmeye ne alışkanlık sayımına girer.
        val card = CardMatcher.match(candidates.map { TextNormalizer.normalize(it) }, cards)
        val wordTokens = candidates.filter { TextNormalizer.normalize(it) !in card?.tokens.orEmpty() }
        val cat = categorizer.categorizeTokens(wordTokens)

        return ParsedEntry(
            rawText = raw,
            grossAmount = amount,
            chargeableFraction = fraction,
            date = date,
            category = cat.category,
            categoryConfidence = cat.confidence,
            tokens = wordTokens.map { TextNormalizer.normalize(it) },
            cardId = card?.card?.id,
        )
    }

    // ───────────────────────── Tutar ─────────────────────────

    private val amountRegex = Regex("^\\d[\\d.,]*$")

    /**
     * Ayrı yazılmış tutar yoksa ilk bitişik tutarı ("40market") iki kelimeye böler; sonrası her zamanki gibi
     * çalışır ("market" kategoriye ve öğrenmeye girer). Ayrı tutar varsa ("3kg elma 45") ona dokunulmaz.
     */
    private fun splitGluedAmount(tokens: List<String>): List<String> {
        if (tokens.any { amountRegex.matches(it) }) return tokens
        for ((i, token) in tokens.withIndex()) {
            val split = GluedAmount.split(token) ?: continue
            return tokens.toMutableList().apply {
                removeAt(i)
                addAll(i, listOf(split.number, split.word).filter { it.isNotEmpty() })
            }
        }
        return tokens
    }

    private fun extractAmount(tokens: List<String>): Double? {
        val token = tokens.firstOrNull { amountRegex.matches(it) } ?: return null
        return parseNumber(token)
    }

    /** Türkçe öncelikli sayı ayrıştırma: "1.250" → 1250, "120,50" → 120.5, "120.50" → 120.5. */
    internal fun parseNumber(token: String): Double? = MoneyInput.parseNumber(token)

    // ───────────────────────── Tarih ─────────────────────────

    /** @param wordIndices tarih olarak okunan kelimeler; kategori ve alışkanlık sayımına girmez. */
    private data class DateMatch(val date: LocalDate, val wordIndices: Set<Int> = emptySet())

    /**
     * "bugün", "dün", "önceki gün" ve gün adları. Gün adı en yakın geçmiş o günü verir ("cuma" bugün
     * cumaysa bugün); "geçen cuma" bugünü atlar. "pazar" semt pazarı da olabildiği için yalnız
     * "pazar günü" ya da "geçen pazar" tarih sayılır.
     */
    private fun extractDate(normalized: List<String>, today: LocalDate): DateMatch {
        for ((i, word) in normalized.withIndex()) {
            when (word) {
                "bugun" -> return DateMatch(today, setOf(i))
                "dun" -> return DateMatch(today.minusDays(1), setOf(i))
                "onceki", "evvelsi", "evvelki" -> {
                    // "önceki gün" / "evvelsi gün"
                    val next = normalized.getOrNull(i + 1)
                    if (next == "gun" || word == "evvelsi") return DateMatch(today.minusDays(2), setOf(i))
                }
            }
            val weekday = WEEKDAYS[word] ?: continue
            val previous = normalized.getOrNull(i - 1) == "gecen"
            val next = normalized.getOrNull(i + 1)
            if (word == "pazar" && !previous && next != "gunu" && next != "gun") continue
            return DateMatch(lastWeekday(today, weekday, skipToday = previous), setOf(i))
        }
        return DateMatch(today)
    }

    private fun lastWeekday(today: LocalDate, day: DayOfWeek, skipToday: Boolean): LocalDate {
        var back = (today.dayOfWeek.isoDayNumber - day.isoDayNumber + 7) % 7
        if (back == 0 && skipToday) back = 7
        return today.minusDays(back)
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
        private val DATE_WORDS = setOf("bugun", "dun", "onceki", "evvelsi", "evvelki", "gun", "gunu", "gecen")

        /** "pazar" burada da var ama kategori kelimesi olarak elenmez; tarih olarak okunursa [DateMatch] eler. */
        private val WEEKDAYS = mapOf(
            "pazartesi" to DayOfWeek.MONDAY,
            "sali" to DayOfWeek.TUESDAY,
            "carsamba" to DayOfWeek.WEDNESDAY,
            "persembe" to DayOfWeek.THURSDAY,
            "cuma" to DayOfWeek.FRIDAY,
            "cumartesi" to DayOfWeek.SATURDAY,
            "pazar" to DayOfWeek.SUNDAY,
        )
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
