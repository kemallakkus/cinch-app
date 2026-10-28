package com.cinch.shared.statement

import com.cinch.shared.category.TextNormalizer
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.max

/**
 * Banka ekranındaki tutarları okur. Bankalar (hatta aynı ekran) iki biçimi karışık kullanır: "52.331,00 TL" ve
 * "52,331.00 TL". Son ayırıcı ondalıktır; tek ayırıcı varsa arkasında iki hane kalıyorsa ondalık, üç hane
 * kalıyorsa binliktir. Kuruş küçük puntoyla yazıldığı için ayrı okunabilir ("52.331" ",00 TL"): birleştirilir.
 */
internal object ScreenMoney {

    // Geriye bakan (lookbehind) ifade kullanılmıyor: iOS'taki Kotlin/Native düzenli ifadeleriyle de çalışsın.
    private val CURRENCY = Regex("(?i)(^|[^a-z])(tl|try)([^a-z]|$)|₺")
    private val DATE = Regex("\\d{1,2}[./-]\\d{1,2}[./-]\\d{2,4}")
    private val TIME = Regex("\\d{1,2}:\\d{2}")
    private val GAP = Regex("(\\d)\\s*([.,])\\s+(\\d)|(\\d)\\s+([.,])\\s*(\\d)")
    private val TOKEN = Regex("([-−–]?)\\s?(\\d[\\d.,]*)")

    /**
     * Metindeki ilk tutar. Tarih, saat, taksit sayısı ("04/06") ya da hesap numarası tutar sanılmasın diye
     * yanında TL/₺ yazmalı ya da kuruşlu / binlik ayraçlı olmalı. Eksi işareti korunur (ödeme, limit aşımı).
     */
    fun parse(text: String, currency: Currency = Currency.TRY): Money? {
        val hasCurrency = CURRENCY.containsMatchIn(text)
        var cleaned = DATE.replace(text, " ")
        cleaned = TIME.replace(cleaned, " ")
        cleaned = zeroForLetterO(cleaned)
        cleaned = GAP.replace(cleaned) { m ->
            val g = m.groupValues
            if (g[1].isNotEmpty()) g[1] + g[2] + g[3] else g[4] + g[5] + g[6]
        }
        for (m in TOKEN.findAll(cleaned)) {
            val (minor, formatted) = toMinor(m.groupValues[2].trimEnd('.', ',')) ?: continue
            if (!hasCurrency && !formatted) continue
            val negative = m.groupValues[1].isNotEmpty()
            return Money(if (negative) -minor else minor, currency)
        }
        return null
    }

    /** "1.455,00" → (145500, true). İkinci değer: ayraçlı yazılmış mı (düz "2026" gibi sayılardan ayırmak için). */
    internal fun toMinor(raw: String): Pair<Long, Boolean>? {
        if (raw.isEmpty() || !raw.first().isDigit()) return null
        val lastDot = raw.lastIndexOf('.')
        val lastComma = raw.lastIndexOf(',')
        val lastSep = max(lastDot, lastComma)
        if (lastSep < 0) return raw.toLongOrNull()?.let { it * 100 to false }

        val separator = raw[lastSep]
        val tail = raw.substring(lastSep + 1)
        val bothKinds = lastDot >= 0 && lastComma >= 0
        val single = raw.count { it == separator } == 1
        val isDecimal = bothKinds || (single && tail.length in 1..2)
        return if (isDecimal) {
            if (tail.any { !it.isDigit() } || tail.isEmpty()) return null
            val whole = raw.substring(0, lastSep).filter { it.isDigit() }.ifEmpty { "0" }.toLongOrNull() ?: return null
            whole * 100 + tail.padEnd(2, '0').take(2).toLong() to true
        } else {
            val groups = raw.split('.', ',')
            if (groups.drop(1).any { it.length != 3 } || groups.first().length !in 1..3) return null
            groups.joinToString("").toLongOrNull()?.let { it * 100 to true }
        }
    }

    /** Tanıma "0,00"ı bazen "O,OO" okur: rakam ya da ayraç komşusu olan O harfi sıfırdır. */
    private fun zeroForLetterO(text: String): String {
        val chars = text.toCharArray()
        for (i in chars.indices) {
            if (chars[i] != 'O' && chars[i] != 'o') continue
            val prev = chars.getOrNull(i - 1)
            val next = chars.getOrNull(i + 1)
            val nearNumber = (prev != null && (prev.isDigit() || prev == ',' || prev == '.')) ||
                (next != null && (next.isDigit() || next == ',' || next == '.'))
            val inWord = (prev?.isLetter() == true && prev != 'O' && prev != 'o') ||
                (next?.isLetter() == true && next != 'O' && next != 'o')
            if (nearNumber && !inWord) chars[i] = '0'
        }
        return chars.concatToString()
    }
}

/**
 * Banka ekranındaki tarihler: "28.09.2026", "6 Ekim", "26 EYL", "28 Kasım 2026", "Son 2 gün! (6 Ekim)".
 * Yıl yazmıyorsa [anchor]'a (okunduğu gün) en yakın yıl seçilir: Ekim'de okunan "6 Ekim" bu yılın 6 Ekim'i.
 */
internal object ScreenDate {

    private val NUMERIC = Regex("(\\d{1,2})[./-](\\d{1,2})[./-](\\d{4}|\\d{2})(?!\\d)")
    private val MONTHS = listOf(
        listOf("ocak", "oca"), listOf("subat", "sub"), listOf("mart", "mar"), listOf("nisan", "nis"),
        listOf("mayis", "may"), listOf("haziran", "haz"), listOf("temmuz", "tem"), listOf("agustos", "agu"),
        listOf("eylul", "eyl"), listOf("ekim", "eki"), listOf("kasim", "kas"), listOf("aralik", "ara"),
    )
    private val NAMED = Regex(
        "(\\d{1,2})\\s*(" + MONTHS.flatten().joinToString("|") + ")(?![a-z])\\.?(?:\\s+(\\d{4}))?",
    )

    /** Tarih ve metindeki yeri; yılı yazılı mı (işlem satırlarında tam tarih aranır). */
    data class Found(val date: LocalDate, val index: Int, val hasYear: Boolean)

    fun parse(text: String, anchor: LocalDate): LocalDate? = findAll(text, anchor).firstOrNull()?.date

    fun findAll(text: String, anchor: LocalDate): List<Found> {
        val folded = TextNormalizer.fold(text)
        val numeric = NUMERIC.findAll(folded).mapNotNull { m ->
            val year = m.groupValues[3].toInt().let { if (it < 100) 2000 + it else it }
            resolve(m.groupValues[1].toInt(), m.groupValues[2].toInt(), year, anchor)
                ?.let { Found(it, m.range.first, hasYear = true) }
        }
        val named = NAMED.findAll(folded).mapNotNull { m ->
            val month = MONTHS.indexOfFirst { m.groupValues[2] in it } + 1
            val year = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()
            resolve(m.groupValues[1].toInt(), month, year, anchor)?.let { Found(it, m.range.first, hasYear = year != null) }
        }
        return (numeric + named).sortedBy { it.index }.toList()
    }

    private fun resolve(day: Int, month: Int, year: Int?, anchor: LocalDate): LocalDate? {
        if (month !in 1..12 || day !in 1..31) return null
        if (year != null) return if (year in 2000..2099) runCatching { LocalDate(year, month, day) }.getOrNull() else null
        return listOf(anchor.year - 1, anchor.year, anchor.year + 1)
            .mapNotNull { runCatching { LocalDate(it, month, day) }.getOrNull() }
            .minByOrNull { abs(it.toEpochDays() - anchor.toEpochDays()) }
    }
}
