package com.cinch.shared.money

/**
 * Kullanıcının elle yazdığı tutarı okur (ör. bütçe formu). Hızlı kayıt da aynı sayı kurallarını kullanır.
 * Türkçe öncelikli: "1.250" → 1250, "120,50" → 120,5, "1.234,56" → 1234,56.
 */
object MoneyInput {

    private val number = Regex("^\\d[\\d.,]*$")

    /** Boşluk ve para simgesi yok sayılır. Boş, geçersiz veya eksi işaretli girişte null. */
    fun parse(text: String, currency: Currency): Money? {
        val cleaned = text.filterNot { it.isWhitespace() }
            .removePrefix(currency.symbol)
            .removeSuffix(currency.symbol)
        if (!number.matches(cleaned)) return null
        return parseNumber(cleaned)?.let { Money.ofDouble(it, currency) }
    }

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
}
