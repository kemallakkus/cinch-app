package com.cinch.shared.parser

import com.cinch.shared.category.TextNormalizer
import com.cinch.shared.domain.Card

/** @param tokens hızlı kayıtta kart adından gelen (kategoriye sayılmaması gereken) kelimeler. */
data class CardMatch(val card: Card, val tokens: Set<String>)

/**
 * Hızlı kayıttaki kart adını bulur: "195 market qnb" → QNB kartı. Kart adının herhangi bir kelimesi
 * (en az 3 harf) eşleşmeye yeter; "Akbank Axess" için "axess" yazmak yeterli.
 */
internal object CardMatcher {

    private const val MIN_LENGTH = 3

    /**
     * @param normalized kayıttaki sade kelimeler ([TextNormalizer.normalize] uygulanmış).
     * @return en çok kelimesi geçen kart. Birden çok kart eşit eşleşirse (ör. iki Akbank kartı ve
     *   yalnız "akbank" yazıldı) null: tahmin etmek yerine kullanıcı seçer.
     */
    fun match(normalized: List<String>, cards: List<Card>): CardMatch? {
        if (cards.isEmpty()) return null
        val words = normalized.toSet()
        val scored = cards.mapNotNull { card ->
            val hits = nameTokens(card).filter { it in words }.toSet()
            if (hits.isEmpty()) null else CardMatch(card, hits)
        }
        val best = scored.maxOfOrNull { it.tokens.size } ?: return null
        return scored.singleOrNull { it.tokens.size == best }
    }

    private fun nameTokens(card: Card): List<String> =
        TextNormalizer.tokenize(card.name)
            .map { TextNormalizer.normalize(it) }
            .filter { it.length >= MIN_LENGTH && it.none { ch -> ch.isDigit() } }
}
