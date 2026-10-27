package com.cinch.shared.domain

import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import kotlinx.datetime.LocalDate

/**
 * Kredi kartı.
 *
 * @param carriedBalance taşınan (önceki dönemden kalan) borç.
 * @param monthlyRatePercent aylık faiz + vergi oranı (ör. 4.9).
 * @param statementDay ekstre kesim günü (1–31); ay o kadar uzun değilse ayın son günü sayılır.
 */
data class Card(
    val id: Long = 0,
    val name: String,
    val carriedBalance: Money,
    val monthlyRatePercent: Double = 4.9,
    val statementDay: Int? = null,
) {
    val currency: Currency get() = carriedBalance.currency

    /** [from]..[to] (ikisi dahil) aralığına düşen ekstre günleri. Ekstre günü yoksa boş. */
    fun statementDatesBetween(from: LocalDate, to: LocalDate): List<LocalDate> =
        statementDay?.let { monthlyDatesBetween(it, from, to) } ?: emptyList()
}
