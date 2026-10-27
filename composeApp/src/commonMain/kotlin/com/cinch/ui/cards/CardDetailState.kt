package com.cinch.ui.cards

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import com.cinch.shared.debt.CardPayoffResult
import com.cinch.shared.domain.Card
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
)

sealed interface CardDetailEvent {
    /** Yazılan banka tutarını kayıtlarla karşılaştır. */
    data object Reconcile : CardDetailEvent

    /** Mutabakattaki eksik tutarı bu karta "Diğer" harcaması olarak ekle. */
    data object AddMissingDifference : CardDetailEvent
}
