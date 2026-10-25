package com.cinch.shared.category

/** Metni eşleştirme için sadeleştirir: küçük harf + Türkçe karakter indirgeme. */
object TextNormalizer {

    private val replacements = mapOf(
        'ı' to 'i', 'İ' to 'i', 'ş' to 's', 'Ş' to 's', 'ğ' to 'g', 'Ğ' to 'g',
        'ü' to 'u', 'Ü' to 'u', 'ö' to 'o', 'Ö' to 'o', 'ç' to 'c', 'Ç' to 'c',
    )

    fun normalize(input: String): String {
        val lower = input.lowercase()
        val sb = StringBuilder(lower.length)
        for (ch in lower) {
            sb.append(replacements[ch] ?: ch)
        }
        return sb.toString().trim()
    }

    /** Metni kelimelere böler; noktalama atılır, sayılar korunur. */
    fun tokenize(input: String): List<String> =
        input.split(Regex("[\\s]+"))
            .map { it.trim().trim('.', ',', '!', '?', ';', ':') }
            .filter { it.isNotEmpty() }
}
