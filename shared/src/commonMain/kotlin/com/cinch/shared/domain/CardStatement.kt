package com.cinch.shared.domain

import com.cinch.shared.money.Money
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/**
 * Banka ekranından okunan (kullanıcının onayladığı) ekstre bilgisi. Her okuma ayrı kayıt: aynı ekstrenin borcu
 * ödendikçe azalır ("52.331 → 31.393"), geçmiş okumalar ödeme ilerlemesini ve aylık kıyası verir.
 *
 * @param readOn okunduğu gün.
 * @param statementBalance ödenecek (kalan) ekstre borcu.
 * @param availableLimit eksiyse limit aşılmış.
 */
data class CardStatement(
    val id: Long = 0,
    val cardId: Long,
    val readOn: LocalDate,
    val statementDate: LocalDate? = null,
    val dueDate: LocalDate? = null,
    val nextStatementDate: LocalDate? = null,
    val statementBalance: Money? = null,
    val minimumPayment: Money? = null,
    val periodSpending: Money? = null,
    val futureInstallments: Money? = null,
    val totalDebt: Money? = null,
    val creditLimit: Money? = null,
    val availableLimit: Money? = null,
) {
    /** Son ödemeye kalan gün (0 = bugün, eksi = geçti); tarih okunamadıysa null. */
    fun daysUntilDue(today: LocalDate): Int? = dueDate?.let { today.daysUntil(it) }

    /** Ekstre ödenmiş görünüyor (kalan borç sıfır ya da eksi). */
    val isPaidOff: Boolean get() = statementBalance?.let { it.isZero || it.isNegative } == true
}
