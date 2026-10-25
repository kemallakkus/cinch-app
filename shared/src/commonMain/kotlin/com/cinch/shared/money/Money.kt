package com.cinch.shared.money

import kotlin.math.absoluteValue
import kotlin.math.roundToLong

/**
 * Desteklenen para birimleri. [fractionDigits] kuruş/sent basamak sayısıdır; tutarlar her zaman
 * tam sayı "minor unit" (kuruş) olarak tutulur, böylece ondalık kayan nokta hatası olmaz.
 */
enum class Currency(val code: String, val symbol: String, val fractionDigits: Int) {
    TRY("TRY", "₺", 2),
    USD("USD", "$", 2),
    EUR("EUR", "€", 2);

    /** 1 birim = 10^fractionDigits minor unit. */
    val minorPerUnit: Long
        get() {
            var r = 1L
            repeat(fractionDigits) { r *= 10 }
            return r
        }

    companion object {
        fun fromCode(code: String): Currency =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: TRY
    }
}

/**
 * Para değeri. [minor] = kuruş cinsinden tutar (ör. 195,00 ₺ → 19500). Negatif olabilir.
 */
data class Money(val minor: Long, val currency: Currency) : Comparable<Money> {

    init {
        // Aritmetik güvenliği: farklı birimlerin karışması çağrı tarafında engellenir.
    }

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return Money(minor + other.minor, currency)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return Money(minor - other.minor, currency)
    }

    /** Oranla çarpar (ör. paylaşılan harcamada kullanıcı payı). Yarıya yuvarlar. */
    operator fun times(factor: Double): Money =
        Money((minor * factor).roundToLong(), currency)

    operator fun unaryMinus(): Money = Money(-minor, currency)

    val isZero: Boolean get() = minor == 0L
    val isNegative: Boolean get() = minor < 0L
    val absolute: Money get() = Money(minor.absoluteValue, currency)

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return minor.compareTo(other.minor)
    }

    private fun requireSameCurrency(other: Money) {
        require(currency == other.currency) {
            "Para birimleri uyuşmuyor: $currency vs ${other.currency}"
        }
    }

    companion object {
        fun zero(currency: Currency) = Money(0, currency)

        /** Birim (lira) + kuruştan oluşturur: of(195, TRY) = 195,00 ₺. */
        fun of(units: Long, currency: Currency): Money =
            Money(units * currency.minorPerUnit, currency)

        /** Ondalık birimden oluşturur: ofDouble(120.50, TRY) = 120,50 ₺. */
        fun ofDouble(amount: Double, currency: Currency): Money =
            Money((amount * currency.minorPerUnit).roundToLong(), currency)

        /** Minor unit listesini toplar; liste boşsa 0 döner. */
        fun sum(items: List<Money>, currency: Currency): Money =
            items.fold(zero(currency)) { acc, m -> acc + m }
    }
}
