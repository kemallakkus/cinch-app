package com.cinch.shared.parser

import com.cinch.shared.category.TextNormalizer

/**
 * Hızlı yazarken boşluksuz girilen tutar: "40market" → "40" + "market", "195tl" → "195". Sayının ardı yalnız
 * harfse ayrılır ("40'lık", "3x4" ayrılmaz). Ölçü ekleri ("3kg", "2li") tutar sayılmaz; para birimi eki atılır.
 */
object GluedAmount {

    /** @param word sayıya bitişik kelime; para birimiyse ("tl", "₺") boş. */
    data class Split(val number: String, val word: String)

    fun split(token: String): Split? {
        if (token.firstOrNull()?.isDigit() != true) return null
        val end = token.indexOfFirst { !it.isDigit() && it != '.' && it != ',' }
        if (end < 0) return null // zaten ayrı bir sayı
        val number = token.substring(0, end).trimEnd('.', ',')
        val suffix = token.substring(end)
        val folded = TextNormalizer.normalize(suffix)
        return when {
            folded in CURRENCY_SUFFIXES -> Split(number, "")
            folded in UNIT_SUFFIXES -> null
            suffix.all { it.isLetter() } -> Split(number, suffix)
            else -> null
        }
    }

    private val CURRENCY_SUFFIXES = setOf("tl", "₺", "lira", "try")

    private val UNIT_SUFFIXES = setOf(
        "kg", "gr", "g", "gram", "lt", "l", "litre", "ml", "cl", "km", "cm", "mm", "m", "adet", "x",
        "li", "lu", "lik", "luk", "lk",
    )
}
