package com.cinch.shared.parser

import com.cinch.shared.category.Categorizer
import com.cinch.shared.category.TextNormalizer
import com.cinch.shared.domain.Card
import com.cinch.shared.domain.Category
import com.cinch.shared.domain.Expense
import com.cinch.shared.language.LanguagePack
import com.cinch.shared.language.LanguagePacks
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
    private val language: LanguagePack = LanguagePacks.TURKISH,
) {

    private val words = language.quickEntry

    /**
     * @param cards kullanıcının kartları; metinde adı geçen kart [ParsedEntry.cardId] olur.
     * @param fallbackDate metinde tarih yoksa kullanılan gün ("dün kayıt yoktu" bildirimine yanıt: dün).
     */
    fun parse(
        raw: String,
        today: LocalDate,
        currency: Currency = defaultCurrency,
        cards: List<Card> = emptyList(),
        fallbackDate: LocalDate = today,
    ): ParsedEntry {
        val tokens = splitGluedAmount(TextNormalizer.tokenize(raw))
        val normalized = tokens.map { TextNormalizer.normalize(it) }

        val amount = extractAmount(tokens)?.let { Money.ofDouble(it, currency) }
        val dateMatch = extractDate(normalized, today) ?: DateMatch(fallbackDate)
        val date = dateMatch.date
        val fraction = extractChargeableFraction(normalized)

        // Kategorilendirme: sayısal ve tarih/pay anahtar kelimeleri hariç kelimeler.
        val candidates = tokens.filterIndexed { i, tok ->
            val n = TextNormalizer.normalize(tok)
            !tok.any { it.isDigit() } && n !in words.dateWords && n !in words.fractionWords && i !in dateMatch.wordIndices
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
            val split = GluedAmount.split(token, language) ?: continue
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
     * "bugün", "dün", "önceki gün" ve gün adları (dil paketinden; "yesterday", "last friday" de). Gün adı en yakın
     * geçmiş o günü verir ("cuma" bugün cumaysa bugün); "geçen cuma" bugünü atlar. "pazar" semt pazarı da
     * olabildiği için yalnız "pazar günü" ya da "geçen pazar" tarih sayılır.
     */
    /** Metinde tarih yoksa null. */
    private fun extractDate(normalized: List<String>, today: LocalDate): DateMatch? {
        // Çok kelimeli kalıplar önce: yoksa "day before yesterday"deki "yesterday" dün okunurdu.
        for (phrase in words.twoDaysAgoPhrases) {
            val parts = phrase.split(' ')
            val at = indexOfSequence(normalized, parts)
            if (at >= 0) return DateMatch(today.minusDays(2), (at until at + parts.size).toSet())
        }
        for ((i, word) in normalized.withIndex()) {
            if (word in words.todayWords) return DateMatch(today, setOf(i))
            if (word in words.yesterdayWords) return DateMatch(today.minusDays(1), setOf(i))
            val weekday = words.weekdays[word] ?: continue
            val previous = normalized.getOrNull(i - 1)?.let { it in words.lastWords } == true
            val nextIsDay = normalized.getOrNull(i + 1)?.let { it in words.dayWords } == true
            if (word in words.ambiguousWeekdays && !previous && !nextIsDay) continue
            return DateMatch(lastWeekday(today, weekday, skipToday = previous), setOf(i))
        }
        return null
    }

    private fun indexOfSequence(tokens: List<String>, parts: List<String>): Int =
        (0..tokens.size - parts.size).firstOrNull { start -> parts.indices.all { tokens[start + it] == parts[it] } } ?: -1

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
        if (words.sharedPhrases.any { it in text }) return 0.5

        if (words.reimbursePhrases.none { it in text }) return 1.0

        // İade oranı; "geri geldi" ama oran belirtilmemişse tamamı iade sayılır.
        val returned = words.returnedFractions.firstOrNull { (word, _) -> word in text }?.second ?: 1.0
        return (1.0 - returned).coerceIn(0.0, 1.0)
    }
}

/** kotlinx-datetime için küçük yardımcılar (tekrar kullanılmak üzere). */
internal fun LocalDate.minusDays(n: Int): LocalDate = this.minus(DatePeriod(days = n))

internal fun LocalDate.plusDays(n: Int): LocalDate = this.plus(DatePeriod(days = n))
