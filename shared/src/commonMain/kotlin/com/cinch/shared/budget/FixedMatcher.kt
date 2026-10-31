package com.cinch.shared.budget

import com.cinch.shared.category.TextNormalizer
import com.cinch.shared.domain.Expense
import com.cinch.shared.domain.FixedExpense
import com.cinch.shared.money.Currency
import kotlinx.datetime.LocalDate

/**
 * Hızlı kayıttaki metin bir sabit gidere mi ait: "15000 kira" kiranın, "199 netflix" Netflix aboneliğinin bu ayki
 * ödemesi. Eşleşirse kayıt serbest harcama değil o sabit giderin ödemesi olur; yoksa ay hesabında aynı para iki kez
 * düşerdi (planlanan sabit gider + harcama).
 *
 * Temkinli: sabit giderin adındaki bütün kelimeler metinde geçmeli ("ev kirası" → "kira" yeter: Türkçe ekler için
 * baştan eşleşme), o ay henüz ödenmemiş olmalı ve tek bir sabit gider eşleşmeli.
 */
object FixedMatcher {

    private const val MIN_TOKEN = 3
    private const val MIN_PREFIX = 4

    /**
     * @param tokens hızlı kaydın sade kelimeleri ([com.cinch.shared.parser.ParsedEntry.tokens]).
     * @param payments sabit gider ödemeleri (o ay ödendi mi, borç bitti mi).
     * @param date kaydın tarihi: o ayın ödemesi aranır.
     */
    fun match(
        tokens: List<String>,
        fixed: List<FixedExpense>,
        payments: List<Expense>,
        date: LocalDate,
        currency: Currency,
    ): FixedExpense? {
        val words = tokens.map { TextNormalizer.normalize(it) }.filter { it.length >= MIN_TOKEN }
        if (words.isEmpty()) return null
        val candidates = fixed.filter { item ->
            item.currency == currency &&
                nameMatches(words, item.name) &&
                payments.none { it.fixedExpenseId == item.id && it.date.year == date.year && it.date.month == date.month } &&
                item.progress(payments, date)?.isFinished != true
        }
        return candidates.singleOrNull()
    }

    /** Adın bütün anlamlı kelimeleri metinde var mı ("ev kirası" ↔ "kira": ilk 4 harf ve fazlası örtüşüyor). */
    fun nameMatches(words: List<String>, name: String): Boolean {
        val nameWords = TextNormalizer.tokenize(name)
            .map { TextNormalizer.normalize(it) }
            .filter { it.length >= MIN_TOKEN && it.none(Char::isDigit) }
        if (nameWords.isEmpty()) return false
        return nameWords.all { n -> words.any { w -> sameWord(w, n) } }
    }

    private fun sameWord(a: String, b: String): Boolean =
        a == b || (minOf(a.length, b.length) >= MIN_PREFIX && (a.startsWith(b) || b.startsWith(a)))
}
