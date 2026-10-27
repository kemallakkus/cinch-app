package com.cinch.shared.domain

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Her ay [dayOfMonth]. güne denk gelen ve [from]..[to] (ikisi dahil) aralığına düşen tarihler.
 * Ay o kadar uzun değilse (ör. 31 Şubat) ayın son günü sayılır. Ekstre ve maaş günü için ortak.
 */
internal fun monthlyDatesBetween(dayOfMonth: Int, from: LocalDate, to: LocalDate): List<LocalDate> {
    val day = dayOfMonth.coerceIn(1, 31)
    val result = mutableListOf<LocalDate>()
    var monthStart = LocalDate(from.year, from.month, 1)
    while (monthStart <= to) {
        val candidate = monthStart.plus(DatePeriod(days = day - 1))
        val nextMonthStart = monthStart.plus(DatePeriod(months = 1))
        val date = if (candidate < nextMonthStart) candidate
        else nextMonthStart.plus(DatePeriod(days = -1))
        if (date in from..to) result += date
        monthStart = nextMonthStart
    }
    return result
}
