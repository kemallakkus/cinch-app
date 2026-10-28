package com.cinch.ui.month

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinch.navigation.DebtDetailRoute
import com.cinch.shared.budget.DebtProgress
import com.cinch.shared.budget.FixedPayments
import com.cinch.shared.budget.progress
import com.cinch.shared.domain.Expense
import com.cinch.shared.domain.FixedExpense
import com.cinch.shared.domain.FixedPlan
import com.cinch.shared.domain.repository.ExpenseRepository
import com.cinch.shared.domain.repository.FixedExpenseRepository
import com.cinch.shared.money.MoneyInput
import com.cinch.shared.time.LocalClock
import com.cinch.ui.common.toInputText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

sealed interface DebtDialog {
    data object AddPayment : DebtDialog
    data class DeletePayment(val payment: Expense) : DebtDialog
}

/**
 * @param fixed borç; yüklendikten sonra null ise silinmiştir (ekran geri döner).
 * @param payments bu borca işaretlenen ödemeler, en yeni üstte.
 */
@Immutable
data class DebtDetailState(
    val isLoading: Boolean = true,
    val today: LocalDate,
    val fixed: FixedExpense? = null,
    val progress: DebtProgress? = null,
    val payments: List<Expense> = emptyList(),
    val dialog: DebtDialog? = null,
    val payAmount: TextFieldState = TextFieldState(),
    val payAmountError: Boolean = false,
)

sealed interface DebtDetailEvent {
    data object AddPaymentClicked : DebtDetailEvent
    data class PaymentClicked(val payment: Expense) : DebtDetailEvent
    data object PayConfirmed : DebtDetailEvent
    data object DeleteConfirmed : DebtDetailEvent
    data object DialogDismissed : DebtDetailEvent
}

sealed interface DebtDetailEffect {
    /** Ödeme eklendi; [finished] = bu ödemeyle borç bitti. */
    data class Paid(val finished: Boolean) : DebtDetailEffect
}

/** Taksitli ya da esnek borcun ilerlemesi, bitiş tahmini ve ödeme geçmişi; aynı ay birden çok ödeme eklenebilir. */
class DebtDetailViewModel(
    private val route: DebtDetailRoute,
    fixedExpenses: FixedExpenseRepository,
    expenses: ExpenseRepository,
    private val fixedPayments: FixedPayments,
    private val clock: LocalClock,
) : ViewModel() {

    private data class Ui(val dialog: DebtDialog? = null, val payAmountError: Boolean = false)

    private val today = clock.today()
    private val ui = MutableStateFlow(Ui())
    private val payAmount = TextFieldState()

    val state: StateFlow<DebtDetailState> = combine(
        fixedExpenses.observeAll().map { list -> list.firstOrNull { it.id == route.fixedId } },
        expenses.observeFixedPayments(),
        ui,
    ) { fixed, payments, ui ->
        DebtDetailState(
            isLoading = false,
            today = today,
            fixed = fixed,
            progress = fixed?.progress(payments, today),
            payments = payments.filter { it.fixedExpenseId == route.fixedId }
                .sortedWith(compareByDescending<Expense> { it.date }.thenByDescending { it.id }),
            dialog = ui.dialog,
            payAmount = payAmount,
            payAmountError = ui.payAmountError,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DebtDetailState(today = today, payAmount = payAmount))

    private val _effect = Channel<DebtDetailEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    init {
        viewModelScope.launch {
            snapshotFlow { payAmount.text.toString() }.collect { ui.update { it.copy(payAmountError = false) } }
        }
    }

    fun onEvent(event: DebtDetailEvent) {
        when (event) {
            DebtDetailEvent.AddPaymentClicked -> openPay()
            is DebtDetailEvent.PaymentClicked -> ui.update { it.copy(dialog = DebtDialog.DeletePayment(event.payment)) }
            DebtDetailEvent.PayConfirmed -> confirmPay()
            DebtDetailEvent.DeleteConfirmed -> confirmDelete()
            DebtDetailEvent.DialogDismissed -> ui.update { it.copy(dialog = null) }
        }
    }

    private fun openPay() {
        val current = state.value
        val fixed = current.fixed ?: return
        val planned = when (fixed.plan) {
            FixedPlan.FLEXIBLE -> current.progress?.let { minOf(fixed.amount, it.remainingDebt) } ?: fixed.amount
            else -> fixed.amount
        }
        payAmount.setTextAndPlaceCursorAtEnd(planned.takeIf { !it.isZero }?.toInputText().orEmpty())
        ui.update { it.copy(dialog = DebtDialog.AddPayment, payAmountError = false) }
    }

    private fun confirmPay() {
        val current = state.value
        val fixed = current.fixed ?: return
        val amount = MoneyInput.parse(payAmount.text.toString(), fixed.currency)?.takeIf { !it.isZero }
        if (amount == null) {
            ui.update { it.copy(payAmountError = true) }
            return
        }
        val finishes = current.progress?.let { debtFinishes(it, amount) } == true
        ui.update { it.copy(dialog = null) }
        viewModelScope.launch {
            fixedPayments.markPaid(fixed, amount, clock.today())
            _effect.send(DebtDetailEffect.Paid(finishes))
        }
    }

    private fun confirmDelete() {
        val payment = (ui.value.dialog as? DebtDialog.DeletePayment)?.payment ?: return
        ui.update { it.copy(dialog = null) }
        viewModelScope.launch { fixedPayments.undo(payment) }
    }
}
