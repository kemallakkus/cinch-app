package com.cinch.shared.category

/** Metni eşleştirme için sadeleştirir: küçük harf + Türkçe karakter indirgeme. */
object TextNormalizer {

    private val replacements = mapOf(
        'ı' to 'i', 'İ' to 'i', 'ş' to 's', 'Ş' to 's', 'ğ' to 'g', 'Ğ' to 'g',
        'ü' to 'u', 'Ü' to 'u', 'ö' to 'o', 'Ö' to 'o', 'ç' to 'c', 'Ç' to 'c',
    )

    /**
     * Harf harf: önce Türkçe indirgeme, sonra küçültme. `String.lowercase()` "İ"yi iki karaktere ("i" +
     * birleşen nokta) çevirdiği için "MİGROS" "migros"la eşleşmezdi.
     */
    fun normalize(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            sb.append(replacements[ch] ?: ch.lowercaseChar())
        }
        return sb.toString().trim()
    }

    /** Metni kelimelere böler; noktalama atılır, sayılar korunur. */
    fun tokenize(input: String): List<String> =
        input.split(Regex("[\\s]+"))
            .map { it.trim().trim('.', ',', '!', '?', ';', ':') }
            .filter { it.isNotEmpty() }
}
