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

/** Ödeme şekli: ne zaman biteceğini ve ay hesabına ne yazılacağını belirler. */
enum class FixedPlan {
    /** Her ay, bitişi yok (kira, internet, abonelik). */
    MONTHLY,

    /** Sabit taksit, belli sayıda (kredi, borç taksiti). Son taksit ödenince biter. */
    INSTALLMENTS,

    /** Her ay farklı tutar (Eminevim gibi). Kalan borç sıfırlanınca biter. */
    FLEXIBLE,
}

/**
 * Her ay ödenen sabit gider ya da borç. Haftalık serbest harcama bütçesine sayılmaz; ay hesabına girer.
 *
 * @param amount ödeme şekline göre: her ay → aylık tutar; taksitli → taksit tutarı; esnek → "genelde
 *   ödediğim" tutar (sıfır olabilir: bilinmiyor). Ödenirken gerçek tutar ayrıca yazılır.
 * @param dayOfMonth ödeme günü (1–31); ay kısaysa ayın son günü.
 * @param cardId hangi kartla ödendiği; null = nakit/havale.
 * @param totalInstallments taksitli: toplam taksit sayısı.
 * @param paidBefore taksitli: uygulamaya eklemeden önce ödenmiş taksit sayısı.
 * @param debt esnek: eklendiği andaki kalan borç; sonraki ödemeler bundan düşer.
 */
data class FixedExpense(
    val id: Long = 0,
    val name: String,
    val amount: Money,
    val dayOfMonth: Int,
    val kind: FixedExpenseKind = FixedExpenseKind.OTHER,
    val cardId: Long? = null,
    val plan: FixedPlan = FixedPlan.MONTHLY,
    val totalInstallments: Int? = null,
    val paidBefore: Int = 0,
    val debt: Money? = null,
) {
    init {
        require(dayOfMonth in 1..31) { "Gün 1–31 arası olmalı: $dayOfMonth" }
    }

    val currency: Currency get() = amount.currency

    /** Bitişi olan bir borç mu (taksitli ya da esnek). */
    val isDebt: Boolean get() = plan != FixedPlan.MONTHLY

    fun dueDatesBetween(from: LocalDate, to: LocalDate): List<LocalDate> = monthlyDatesBetween(dayOfMonth, from, to)

    /** [anyDayOfMonth]'u içeren ayın ödeme günü. */
    fun dueDateIn(anyDayOfMonth: LocalDate): LocalDate {
        val monthStart = LocalDate(anyDayOfMonth.year, anyDayOfMonth.month, 1)
        val monthEnd = monthStart.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))
        return dueDatesBetween(monthStart, monthEnd).single()
    }
}
