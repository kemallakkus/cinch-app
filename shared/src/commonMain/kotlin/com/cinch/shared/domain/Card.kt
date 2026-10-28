package com.cinch.shared.domain

import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.shared.parser.minusDays
import com.cinch.shared.parser.plusDays
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * Kredi kartı.
 *
 * @param carriedBalance taşınan (önceki dönemden kalan) borç.
 * @param monthlyRatePercent aylık faiz + vergi oranı (ör. 4.9).
 * @param statementDay ekstre kesim günü (1–31); ay o kadar uzun değilse ayın son günü sayılır.
 * @param last4 kart numarasının son 4 hanesi; banka ekranı fotoğrafındaki "•••• 1243" ile eşlemek için.
 */
data class Card(
    val id: Long = 0,
    val name: String,
    val carriedBalance: Money,
    val monthlyRatePercent: Double = 4.9,
    val statementDay: Int? = null,
    val last4: String? = null,
) {
    val currency: Currency get() = carriedBalance.currency

    /** [from]..[to] (ikisi dahil) aralığına düşen ekstre günleri. Ekstre günü yoksa boş. */
    fun statementDatesBetween(from: LocalDate, to: LocalDate): List<LocalDate> =
        statementDay?.let { monthlyDatesBetween(it, from, to) } ?: emptyList()

    /**
     * [today]'i içeren ekstre dönemi: son ekstre gününün ertesinden bugüne. Ekstre günü bugünse kapanan
     * dönem gösterilir (o günün harcaması da içinde). Ekstre günü yoksa ayın başından bugüne.
     */
    fun currentPeriod(today: LocalDate): ClosedRange<LocalDate> {
        val lastStatement = statementDatesBetween(today.minus(DatePeriod(months = 2)), today.minusDays(1)).lastOrNull()
        val start = lastStatement?.plusDays(1) ?: LocalDate(today.year, today.month, 1)
        return start..today
    }
}
