package com.cinch.shared.category

import com.cinch.shared.domain.Category
import com.cinch.shared.language.LanguagePack
import com.cinch.shared.language.LanguagePacks

/**
 * Anahtar kelime → kategori sözlüğü. Kelimeler dil paketinden gelir ([of]); Türkçe varsayılan [turkishDefault].
 * Kelimeler normalize edilmiş (küçük harf, Türkçe karakterler sadeleştirilmiş) biçimde tutulur.
 */
class KeywordDictionary(entries: Map<String, Category>) {

    private val map: Map<String, Category> =
        entries.mapKeys { TextNormalizer.normalize(it.key) }

    fun categoryOf(token: String): Category? = map[TextNormalizer.normalize(token)]

    fun contains(token: String): Boolean = map.containsKey(TextNormalizer.normalize(token))

    companion object {
        /** Dil paketinin kelimeleri (birleşik pakette birden çok dil). */
        fun of(language: LanguagePack): KeywordDictionary = KeywordDictionary(language.keywords)

        fun turkishDefault(): KeywordDictionary = of(LanguagePacks.TURKISH)
    }
}
