package com.cinch.shared.domain

import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Sabit giderin türü; ödendiğinde oluşan harcama kaydının kategorisi buradan gelir. */
enum class FixedExpenseKind(val category: Category) {
    RENT(Category.EV),
    BILL(Category.FATURA),
    SUBSCRIPTION(Category.EGLENCE),
    LOAN(Category.DIGER),
    OTHER(Category.DIGER),
}

/**
 * Her ay ödenen sabit gider (kira, fatura, abonelik, kredi taksiti...). Haftalık serbest harcama
 * bütçesine sayılmaz; ay hesabına girer.
 *
 * @param amount planlanan aylık tutar; faturalar gibi değişenlerde ödenirken gerçek tutar yazılır.
 * @param dayOfMonth ödeme günü (1–31); ay kısaysa ayın son günü.
 * @param cardId hangi kartla ödendiği; null = nakit/havale.
 */
data class FixedExpense(
    val id: Long = 0,
    val name: String,
    val amount: Money,
    val dayOfMonth: Int,
    val kind: FixedExpenseKind = FixedExpenseKind.OTHER,
    val cardId: Long? = null,
) {
    init {
        require(dayOfMonth in 1..31) { "Gün 1–31 arası olmalı: $dayOfMonth" }
    }

    val currency: Currency get() = amount.currency

    fun dueDatesBetween(from: LocalDate, to: LocalDate): List<LocalDate> = monthlyDatesBetween(dayOfMonth, from, to)

    /** [anyDayOfMonth]'u içeren ayın ödeme günü. */
    fun dueDateIn(anyDayOfMonth: LocalDate): LocalDate {
        val monthStart = LocalDate(anyDayOfMonth.year, anyDayOfMonth.month, 1)
        val monthEnd = monthStart.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))
        return dueDatesBetween(monthStart, monthEnd).single()
    }
}
