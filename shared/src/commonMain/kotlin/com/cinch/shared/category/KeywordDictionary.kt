package com.cinch.shared.category

import com.cinch.shared.domain.Category

/**
 * Anahtar kelime → kategori sözlüğü. Türkçe varsayılan sözlük [turkishDefault] ile gelir.
 * Kelimeler normalize edilmiş (küçük harf, Türkçe karakterler sadeleştirilmiş) biçimde tutulur.
 */
class KeywordDictionary(entries: Map<String, Category>) {

    private val map: Map<String, Category> =
        entries.mapKeys { TextNormalizer.normalize(it.key) }

    fun categoryOf(token: String): Category? = map[TextNormalizer.normalize(token)]

    fun contains(token: String): Boolean = map.containsKey(TextNormalizer.normalize(token))

    companion object {
        fun turkishDefault(): KeywordDictionary = KeywordDictionary(
            buildMap {
                fun put(cat: Category, vararg words: String) = words.forEach { put(it, cat) }

                put(
                    Category.MARKET,
                    "market", "bakkal", "migros", "bim", "a101", "sok", "şok", "carrefour",
                    "tarım", "manav", "kasap", "alışveriş",
                )
                put(
                    Category.YEMEK,
                    "yemek", "restoran", "restaurant", "lokanta", "kafe", "cafe", "kahve",
                    "çay", "su", "ekmek", "büfe", "bufe", "fast", "food", "döner", "doner",
                    "pizza", "burger", "tatlı", "pastane", "simit", "yemeksepeti", "getir",
                )
                put(
                    Category.ULASIM,
                    "taksi", "otobüs", "otobus", "metro", "metrobüs", "metrobus", "dolmuş",
                    "dolmus", "benzin", "mazot", "yakıt", "yakit", "otopark", "köprü", "kopru",
                    "hgs", "ogs", "bilet", "uber",
                )
                put(
                    Category.FATURA,
                    "fatura", "elektrik", "doğalgaz", "dogalgaz", "internet", "telefon",
                    "abonelik", "aidat",
                )
                put(
                    Category.SAGLIK,
                    "eczane", "ilaç", "ilac", "doktor", "hastane", "muayene", "diş", "dis",
                    "gözlük", "gozluk",
                )
                put(
                    Category.EGLENCE,
                    "sinema", "tiyatro", "konser", "oyun", "netflix", "spotify", "youtube",
                    "steam", "bar", "maç", "mac",
                )
                put(
                    Category.GIYIM,
                    "giyim", "ayakkabı", "ayakkabi", "tişört", "tisort", "pantolon", "ceket",
                    "elbise", "defacto", "lcw", "zara",
                )
                put(
                    Category.KISISEL,
                    "kuaför", "kuafor", "berber", "kozmetik", "parfüm", "parfum", "makyaj",
                )
                put(
                    Category.EV,
                    "kira", "mobilya", "ikea", "deterjan", "nalbur", "züccaciye", "zuccaciye",
                )
            }
        )
    }
}
