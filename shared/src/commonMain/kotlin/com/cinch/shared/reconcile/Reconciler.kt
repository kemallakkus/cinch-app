package com.cinch.shared.reconcile

import com.cinch.shared.domain.Expense
import com.cinch.shared.money.Money

/** Mutabakat sonucunun türü. */
enum class ReconciliationKind {
    MATCH,            // kayıtlar güncel borçla örtüşüyor
    MISSING_EXPENSE,  // kullanıcı bazı harcamaları yazmayı unutmuş (gerçek borç > kayıt)
    EXTRA_RECORDED,   // kullanıcı fazladan/yanlış kayıt girmiş (gerçek borç < kayıt)
}

/**
 * Mutabakat sonucu.
 *
 * @param recordedTotal uygulamadaki kayıtların o karta ait toplamı (brüt tutarlar).
 * @param statedActual kullanıcının elle girdiği güncel kart borcu.
 * @param difference statedActual - recordedTotal (pozitif = eksik kayıt).
 */
data class ReconciliationResult(
    val kind: ReconciliationKind,
    val recordedTotal: Money,
    val statedActual: Money,
    val difference: Money,
)

/**
 * Doküman yüklemeden mutabakat: kullanıcı kartın güncel borcunu elle girer, uygulama kendi
 * kayıtlarıyla farkı bulur ("103 ₺ yazmayı unutmuşsun").
 */
class Reconciler {

    /**
     * @param cardExpenses yalnızca ilgili karta ait harcamalar.
     * @param statedActual kullanıcının girdiği güncel borç.
     * @param tolerance bu tutarın altındaki farklar "eşleşme" sayılır (varsayılan 0).
     */
    fun reconcile(
        cardExpenses: List<Expense>,
        statedActual: Money,
        tolerance: Money = Money.zero(statedActual.currency),
    ): ReconciliationResult {
        val currency = statedActual.currency
        // Mutabakat brüt tutar üzerinden: karta giren tam tutar (pay değil).
        val recordedTotal = cardExpenses.fold(Money.zero(currency)) { acc, e ->
            require(e.grossAmount.currency == currency) { "Para birimleri uyuşmuyor" }
            acc + e.grossAmount
        }
        val difference = statedActual - recordedTotal

        val kind = when {
            difference.absolute <= tolerance -> ReconciliationKind.MATCH
            difference.isNegative -> ReconciliationKind.EXTRA_RECORDED
            else -> ReconciliationKind.MISSING_EXPENSE
        }

        return ReconciliationResult(
            kind = kind,
            recordedTotal = recordedTotal,
            statedActual = statedActual,
            difference = difference,
        )
    }
}
