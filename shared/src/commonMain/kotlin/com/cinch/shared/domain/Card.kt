package com.cinch.shared.domain

import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

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
    fun statementDatesBetween(from: LocalDate, to: LocalDate): List<LocalDate> {
        val day = statementDay?.coerceIn(1, 31) ?: return emptyList()
        val result = mutableListOf<LocalDate>()
        var monthStart = LocalDate(from.year, from.month, 1)
        while (monthStart <= to) {
            val candidate = monthStart.plus(DatePeriod(days = day - 1))
            // Gün ay sonunu aşıyorsa (ör. 31 Şubat) ayın son gününe çek.
            val nextMonthStart = monthStart.plus(DatePeriod(months = 1))
            val date = if (candidate < nextMonthStart) candidate
            else nextMonthStart.plus(DatePeriod(days = -1))
            if (date in from..to) result += date
            monthStart = nextMonthStart
        }
        return result
    }
}
