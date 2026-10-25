package com.cinch.shared.debt

import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import kotlin.math.roundToLong

/** Tek bir ay satırı (kapanış planı çıktısı). */
data class MonthLine(
    val month: Int,
    val interest: Money,
    val payment: Money,
    val remaining: Money,
)

/** Kart kapanış sonucu. [closes] false ise ödeme faizi karşılamıyor (hiç kapanmaz). */
data class CardPayoffResult(
    val closes: Boolean,
    val months: Int,
    val totalInterest: Money,
    val totalPaid: Money,
    val schedule: List<MonthLine>,
)

/** Taksitli kredi durumu. */
data class InstallmentStatus(
    val totalMonths: Int,
    val paidMonths: Int,
    val monthlyPayment: Money,
    val remainingMonths: Int,
    val remainingBalance: Money,
)

/** Esnek ödemeli borç (ör. Eminevim) kapanış sonucu. */
data class FlexiblePayoffResult(
    val months: Int,
    val totalPaid: Money,
    val lastPayment: Money,
    val schedule: List<MonthLine>,
)

/**
 * Borç kapanış simülasyonları. Tüm tutarlar minor unit (kuruş) üzerinden, tek para biriminde.
 */
class PayoffSimulator(private val maxMonths: Int = 600) {

    /**
     * Kredi kartı kapanış simülasyonu: taşınan borca aylık [monthlyRatePercent] (faiz+vergi,
     * ör. 4.9) uygulanır, her ay sabit [fixedPayment] ödenir.
     * "Kartın X ayında kapanır" → [CardPayoffResult.months].
     */
    fun simulateCard(
        balance: Money,
        monthlyRatePercent: Double,
        fixedPayment: Money,
    ): CardPayoffResult {
        require(balance.currency == fixedPayment.currency) { "Para birimleri uyuşmuyor" }
        val currency = balance.currency
        val rate = monthlyRatePercent / 100.0

        var remaining = balance.minor
        var totalInterest = 0L
        var totalPaid = 0L
        val schedule = mutableListOf<MonthLine>()

        if (remaining <= 0L) {
            return CardPayoffResult(true, 0, Money.zero(currency), Money.zero(currency), emptyList())
        }

        var month = 0
        while (remaining > 0L && month < maxMonths) {
            month++
            val interest = (remaining * rate).roundToLong()
            val beforePayment = remaining + interest

            // Ödeme faizi karşılamıyorsa borç küçülmez → hiç kapanmaz.
            if (fixedPayment.minor <= interest) {
                return CardPayoffResult(
                    closes = false,
                    months = 0,
                    totalInterest = Money.zero(currency),
                    totalPaid = Money.zero(currency),
                    schedule = emptyList(),
                )
            }

            val payment = minOf(fixedPayment.minor, beforePayment)
            remaining = beforePayment - payment
            totalInterest += interest
            totalPaid += payment
            schedule += MonthLine(
                month = month,
                interest = Money(interest, currency),
                payment = Money(payment, currency),
                remaining = Money(remaining, currency),
            )
        }

        return CardPayoffResult(
            closes = remaining <= 0L,
            months = month,
            totalInterest = Money(totalInterest, currency),
            totalPaid = Money(totalPaid, currency),
            schedule = schedule,
        )
    }

    /** Taksitli kredi: sabit aylık ödeme, faiz kaydı tutulmaz (ödeme planı sabit). */
    fun installmentStatus(
        monthlyPayment: Money,
        totalMonths: Int,
        paidMonths: Int,
    ): InstallmentStatus {
        require(paidMonths in 0..totalMonths) { "paidMonths 0..totalMonths aralığında olmalı" }
        val remainingMonths = totalMonths - paidMonths
        return InstallmentStatus(
            totalMonths = totalMonths,
            paidMonths = paidMonths,
            monthlyPayment = monthlyPayment,
            remainingMonths = remainingMonths,
            remainingBalance = Money(monthlyPayment.minor * remainingMonths, monthlyPayment.currency),
        )
    }

    /**
     * Esnek ödemeli borç (Eminevim gibi, faizsiz): her ay taban ödeme [basePayment] yapılır,
     * ödenmeyen fark sona sarkar. [payments] verilmezse her ay taban ödenir varsayılır.
     */
    fun simulateFlexible(
        principal: Money,
        basePayment: Money,
        payments: List<Money> = emptyList(),
    ): FlexiblePayoffResult {
        require(principal.currency == basePayment.currency) { "Para birimleri uyuşmuyor" }
        require(basePayment.minor > 0L) { "Taban ödeme pozitif olmalı" }
        val currency = principal.currency

        var remaining = principal.minor
        var totalPaid = 0L
        var lastPayment = 0L
        val schedule = mutableListOf<MonthLine>()
        var month = 0

        while (remaining > 0L && month < maxMonths) {
            val planned = payments.getOrNull(month)?.minor ?: basePayment.minor
            month++
            val payment = minOf(planned, remaining)
            remaining -= payment
            totalPaid += payment
            lastPayment = payment
            schedule += MonthLine(
                month = month,
                interest = Money.zero(currency),
                payment = Money(payment, currency),
                remaining = Money(remaining, currency),
            )
        }

        return FlexiblePayoffResult(
            months = month,
            totalPaid = Money(totalPaid, currency),
            lastPayment = Money(lastPayment, currency),
            schedule = schedule,
        )
    }

    companion object {
        /** Kredi kartları için tipik aylık faiz+vergi oranı (%). */
        const val DEFAULT_CARD_RATE_PERCENT: Double = 4.9
    }
}
