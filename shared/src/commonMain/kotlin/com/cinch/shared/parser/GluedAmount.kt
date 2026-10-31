package com.cinch.shared.parser

import com.cinch.shared.category.TextNormalizer
import com.cinch.shared.language.LanguagePack
import com.cinch.shared.language.LanguagePacks

/**
 * Hızlı yazarken boşluksuz girilen tutar: "40market" → "40" + "market", "195tl" → "195". Sayının ardı yalnız
 * harfse ayrılır ("40'lık", "3x4" ayrılmaz). Ölçü ekleri ("3kg", "2li") tutar sayılmaz; para birimi eki atılır.
 */
object GluedAmount {

    /** @param word sayıya bitişik kelime; para birimiyse ("tl", "₺") boş. */
    data class Split(val number: String, val word: String)

    /** @param language para birimi ve ölçü ekleri; varsayılan bütün diller (ör. düzenleme ekranındaki metin). */
    fun split(token: String, language: LanguagePack = LanguagePacks.ALL): Split? {
        if (token.firstOrNull()?.isDigit() != true) return null
        val end = token.indexOfFirst { !it.isDigit() && it != '.' && it != ',' }
        if (end < 0) return null // zaten ayrı bir sayı
        val number = token.substring(0, end).trimEnd('.', ',')
        val suffix = token.substring(end)
        val folded = TextNormalizer.normalize(suffix)
        return when {
            folded in language.quickEntry.currencySuffixes -> Split(number, "")
            folded in language.quickEntry.unitSuffixes -> null
            suffix.all { it.isLetter() } -> Split(number, suffix)
            else -> null
        }
    }
}
