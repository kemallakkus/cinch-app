package com.cinch.ui.month

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinch.shared.budget.DebtProgress
import com.cinch.shared.budget.FixedPaymentState
import com.cinch.shared.budget.FixedPayments
import com.cinch.shared.budget.FixedStatus
import com.cinch.shared.budget.MonthSummary
import com.cinch.shared.budget.ObserveMonthSummary
import com.cinch.shared.domain.FixedPlan
import com.cinch.shared.domain.Income
import com.cinch.shared.domain.repository.IncomeRepository
import com.cinch.shared.money.Money
import com.cinch.shared.money.MoneyInput
import com.cinch.shared.time.LocalClock
import com.cinch.ui.common.toInputText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** Ekranda açık pencere: ödemeyi onaylama ya da geri alma. */
sealed interface MonthDialog {
    data class Pay(val status: FixedStatus) : MonthDialog
    data class Undo(val status: FixedStatus) : MonthDialog
}

/**
 * @param payAmount "ödendi" penceresindeki tutar; planlanan tutarla açılır, fatura farklıysa düzeltilir.
 */
@Immutable
data class MonthState(
    val isLoading: Boolean = true,
    val today: LocalDate,
    val summary: MonthSummary? = null,
    val incomes: List<Income> = emptyList(),
    val dialog: MonthDialog? = null,
    val payAmount: TextFieldState = TextFieldState(),
    val payAmountError: Boolean = false,
)

sealed interface MonthEvent {
    /** Ekran öne geldi: ay değişmiş olabilir. */
    data object Resumed : MonthEvent

    /** Sabit giderin yanındaki işarete dokunuldu: ödenmediyse onay, ödendiyse geri alma penceresi. */
    data class FixedCheckClicked(val status: FixedStatus) : MonthEvent
    data object PayConfirmed : MonthEvent
    data object UndoConfirmed : MonthEvent
    data object DialogDismissed : MonthEvent
}

sealed interface MonthEffect {
    /** Ödeme işaretlendi: küçük bir kutlama. [finishedName] doluysa bu ödemeyle borç bitti. */
    data class Paid(val finishedName: String? = null) : MonthEffect
}

/** "Bu ay" sekmesi: gelir − sabit giderler − harcama özeti, gelirler ve sabit giderler. */
@OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest
class MonthViewModel(
    observeMonthSummary: ObserveMonthSummary,
    incomes: IncomeRepository,
    private val fixedPayments: FixedPayments,
    private val clock: LocalClock,
) : ViewModel() {

    private data class Ui(val dialog: MonthDialog? = null, val payAmountError: Boolean = false)

    private val today = MutableStateFlow(clock.today())
    private val ui = MutableStateFlow(Ui())
    private val payAmount = TextFieldState()

    val state: StateFlow<MonthState> = combine(
        today,
        today.flatMapLatest { observeMonthSummary(it) },
        incomes.observeAll(),
        ui,
    ) { day, summary, list, ui ->
        MonthState(
            isLoading = false,
            today = day,
            summary = summary,
            incomes = list,
            dialog = ui.dialog,
            payAmount = payAmount,
            payAmountError = ui.payAmountError,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthState(today = today.value, payAmount = payAmount))

    private val _effect = Channel<MonthEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    init {
        viewModelScope.launch {
            snapshotFlow { payAmount.text.toString() }.collect { ui.update { it.copy(payAmountError = false) } }
        }
    }

    fun onEvent(event: MonthEvent) {
        when (event) {
            MonthEvent.Resumed -> today.value = clock.today()
            is MonthEvent.FixedCheckClicked -> openDialog(event.status)
            MonthEvent.PayConfirmed -> confirmPay()
            MonthEvent.UndoConfirmed -> confirmUndo()
            MonthEvent.DialogDismissed -> ui.update { it.copy(dialog = null) }
        }
    }

    private fun openDialog(status: FixedStatus) {
        if (status.state == FixedPaymentState.PAID) {
            ui.update { it.copy(dialog = MonthDialog.Undo(status)) }
        } else {
            // Tahmini tutarla açılır; esnek borçta tutar bilinmiyorsa boş (kullanıcı ne ödediyse yazar).
            payAmount.setTextAndPlaceCursorAtEnd(status.plannedAmount.takeIf { !it.isZero }?.toInputText().orEmpty())
            ui.update { it.copy(dialog = MonthDialog.Pay(status), payAmountError = false) }
        }
    }

    private fun confirmPay() {
        val dialog = ui.value.dialog as? MonthDialog.Pay ?: return
        val fixed = dialog.status.fixed
        val amount = MoneyInput.parse(payAmount.text.toString(), fixed.currency)?.takeIf { !it.isZero }
        if (amount == null) {
            ui.update { it.copy(payAmountError = true) }
            return
        }
        ui.update { it.copy(dialog = null) }
        val finishes = dialog.status.progress?.let { debtFinishes(it, amount) } == true
        viewModelScope.launch {
            fixedPayments.markPaid(fixed, amount, clock.today())
            _effect.send(MonthEffect.Paid(finishedName = fixed.name.takeIf { finishes }))
        }
    }

    private fun confirmUndo() {
        val payment = (ui.value.dialog as? MonthDialog.Undo)?.status?.payment ?: return
        ui.update { it.copy(dialog = null) }
        viewModelScope.launch { fixedPayments.undo(payment) }
    }
}

/** Bu ödemeyle borç biter mi: taksitte son taksit, esnek borçta kalanı kapatan tutar. */
internal fun debtFinishes(progress: DebtProgress, amount: Money): Boolean = when (progress.plan) {
    FixedPlan.INSTALLMENTS -> progress.remainingCount == 1
    FixedPlan.FLEXIBLE -> !progress.isFinished && progress.remainingDebt.minor - amount.minor <= 0
    FixedPlan.MONTHLY -> false
}
