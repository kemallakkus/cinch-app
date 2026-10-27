package com.cinch.shared.domain

import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import kotlinx.datetime.LocalDate

/**
 * Düzenli aylık gelir (maaş, ek iş, kira geliri...).
 *
 * @param dayOfMonth ayın kaçında yattığı (1–31); ay kısaysa son gün. Null = günü belli değil.
 */
data class Income(
    val id: Long = 0,
    val name: String,
    val amount: Money,
    val dayOfMonth: Int? = null,
) {
    init {
        require(dayOfMonth == null || dayOfMonth in 1..31) { "Gün 1–31 arası olmalı: $dayOfMonth" }
    }

    val currency: Currency get() = amount.currency

    /** [from]..[to] (ikisi dahil) aralığına düşen yatış günleri. Günü yoksa boş. */
    fun paydaysBetween(from: LocalDate, to: LocalDate): List<LocalDate> =
        dayOfMonth?.let { monthlyDatesBetween(it, from, to) } ?: emptyList()
}
