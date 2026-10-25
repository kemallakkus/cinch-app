package com.cinch.shared.money

import kotlin.math.absoluteValue

/** Uygulama dili / yerel ayarı. Para biçimi buna göre değişir. */
enum class AppLocale(val tag: String) {
    TR("tr"),
    EN("en");

    companion object {
        fun fromTag(tag: String): AppLocale =
            entries.firstOrNull { tag.startsWith(it.tag, ignoreCase = true) } ?: TR
    }
}

/**
 * Yerel ayara göre para biçimlendirme. Platform bağımsız ve deterministik — testlenebilir.
 *
 * - TR: `1.234,56 ₺` (nokta binlik, virgül ondalık, sembol sonda)
 * - EN: `₺1,234.56` (virgül binlik, nokta ondalık, sembol başta)
 */
object MoneyFormatter {

    fun format(money: Money, locale: AppLocale, withSymbol: Boolean = true): String {
        val sep = separators(locale)
        val digits = money.currency.fractionDigits
        val negative = money.minor < 0
        val absMinor = money.minor.absoluteValue

        val per = money.currency.minorPerUnit
        val intPart = absMinor / per
        val fracPart = absMinor % per

        val grouped = groupThousands(intPart.toString(), sep.group)
        val body = if (digits > 0) {
            val frac = fracPart.toString().padStart(digits, '0')
            "$grouped${sep.decimal}$frac"
        } else {
            grouped
        }

        val signed = if (negative) "-$body" else body
        if (!withSymbol) return signed

        return when (locale) {
            AppLocale.TR -> "$signed ${money.currency.symbol}"
            AppLocale.EN -> if (negative) "-${money.currency.symbol}$body" else "${money.currency.symbol}$body"
        }
    }

    private data class Separators(val group: Char, val decimal: Char)

    private fun separators(locale: AppLocale): Separators = when (locale) {
        AppLocale.TR -> Separators(group = '.', decimal = ',')
        AppLocale.EN -> Separators(group = ',', decimal = '.')
    }

    private fun groupThousands(intDigits: String, groupChar: Char): String {
        if (intDigits.length <= 3) return intDigits
        val sb = StringBuilder()
        val firstGroup = intDigits.length % 3
        var idx = 0
        if (firstGroup > 0) {
            sb.append(intDigits, 0, firstGroup)
            idx = firstGroup
        }
        while (idx < intDigits.length) {
            if (sb.isNotEmpty()) sb.append(groupChar)
            sb.append(intDigits, idx, idx + 3)
            idx += 3
        }
        return sb.toString()
    }
}
