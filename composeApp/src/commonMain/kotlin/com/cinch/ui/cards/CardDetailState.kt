package com.cinch.ui.cards

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import com.cinch.shared.debt.CardPayoffResult
import com.cinch.shared.domain.Card
import com.cinch.shared.domain.CardStatement
import com.cinch.shared.domain.Expense
import com.cinch.shared.money.Money
import com.cinch.shared.reconcile.ReconciliationResult
import kotlinx.datetime.LocalDate

/**
 * @param card yüklenen kart; yüklendikten sonra null ise kart silinmiştir.
 * @param periodExpenses bu ekstre dönemindeki kart harcamaları, en yeni üstte.
 * @param periodTotal dönem harcamalarının brüt toplamı (karta giren tam tutar).
 * @param payoff yazılan aylık ödemeyle devreden borcun kapanışı; ödeme okunamıyorsa null.
 * @param firstMonthInterest devreden borcun ilk ay faizi: ödeme bunu geçmezse borç hiç kapanmaz.
 * @param reconciliation "Karşılaştır"a basılınca hesaplanır; kayıtlar değişince kendiliğinden güncellenir.
 * @param statement banka ekranından okunan son ekstre; hiç okunmadıysa null.
 * @param statementStartBalance aynı ekstrenin (aynı son ödeme tarihi) ilk okunan borcu: ödedikçe azalır.
 */
@Immutable
data class CardDetailState(
    val isLoading: Boolean = true,
    val today: LocalDate,
    val card: Card? = null,
    val period: ClosedRange<LocalDate>? = null,
    val periodExpenses: List<Expense> = emptyList(),
    val periodTotal: Money? = null,
    val monthlyPayment: TextFieldState = TextFieldState(),
    val payoff: CardPayoffResult? = null,
    val firstMonthInterest: Money? = null,
    val statedSpending: TextFieldState = TextFieldState(),
    val statedSpendingError: Boolean = false,
    val reconciliation: ReconciliationResult? = null,
    val statement: CardStatement? = null,
    val statementStartBalance: Money? = null,
    val statementHistory: List<StatementPoint> = emptyList(),
) {
    /**
     * Bankanın dönem içi harcamasının ne kadarı Cinch'e girilmiş (0–1). Okuma bu döneme ait değilse ya da
     * banka tutarı okunamadıysa null.
     */
    val recordedShare: Float?
        get() {
            val reading = statement ?: return null
            val bank = reading.periodSpending ?: return null
            val ours = periodTotal ?: return null
            val start = period?.start ?: return null
            if (reading.readOn < start || bank.isZero || bank.isNegative || bank.currency != ours.currency) return null
            return (ours.minor.toFloat() / bank.minor.toFloat()).coerceIn(0f, 1f)
        }

    /** Bu ekstreye ilk okumadan bu yana ödenen ("52.331 → 31.393": 20.938 ₺). */
    val statementPaid: Money?
        get() {
            val start = statementStartBalance ?: return null
            val now = statement?.statementBalance ?: return null
            if (start.currency != now.currency) return null
            return (start - now).takeIf { !it.isNegative && !it.isZero }
        }
}

/** Bir ekstrenin tutarı: o son ödeme tarihi için ilk okunan (ödenmeden önceki) borç. */
@Immutable
data class StatementPoint(val dueDate: LocalDate, val amount: Money)

sealed interface CardDetailEvent {
    /** Yazılan banka tutarını kayıtlarla karşılaştır. */
    data object Reconcile : CardDetailEvent

    /** Mutabakattaki eksik tutarı bu karta "Diğer" harcaması olarak ekle. */
    data object AddMissingDifference : CardDetailEvent
}
