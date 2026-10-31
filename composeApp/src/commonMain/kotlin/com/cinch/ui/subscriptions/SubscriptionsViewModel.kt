package com.cinch.ui.subscriptions

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinch.shared.budget.FixedPayments
import com.cinch.shared.domain.Category
import com.cinch.shared.domain.FixedExpense
import com.cinch.shared.domain.FixedExpenseKind
import com.cinch.shared.domain.FixedPlan
import com.cinch.shared.domain.repository.ExpenseRepository
import com.cinch.shared.domain.repository.FixedExpenseRepository
import com.cinch.shared.money.Money
import com.cinch.shared.recurring.ObserveRecurringPayments
import com.cinch.shared.recurring.RecurringDismissals
import com.cinch.shared.recurring.RecurringPayment
import com.cinch.shared.recurring.RecurringPayments
import com.cinch.shared.time.LocalClock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** @param monthlyTotal bulunan düzenli ödemelerin son tutarlarının toplamı; yoksa null. */
@Immutable
data class SubscriptionsState(
    val isLoading: Boolean = true,
    val items: List<RecurringPayment> = emptyList(),
    val monthlyTotal: Money? = null,
)

sealed interface SubscriptionsEvent {
    /** Sabit gidere çevir: her ay planlanır, ödeme gününden önce hatırlatılır, adı yazılınca ödendi sayılır. */
    data class MakeFixed(val item: RecurringPayment) : SubscriptionsEvent

    /** "Abonelik değil": bir daha önerilmez. */
    data class Dismiss(val item: RecurringPayment) : SubscriptionsEvent
}

sealed interface SubscriptionsEffect {
    data class Message(val text: String) : SubscriptionsEffect
}

/**
 * Kendiliğinden bulunan düzenli ödemeler (abonelik adayları). Sabit gidere çevrilince listeden düşer; o ayın ödemesi
 * zaten yazıldıysa sabit giderin ödemesine çevrilir, ay hesabında iki kez düşmesin.
 */
class SubscriptionsViewModel(
    observe: ObserveRecurringPayments,
    private val fixedExpenses: FixedExpenseRepository,
    private val expenses: ExpenseRepository,
    private val dismissals: RecurringDismissals,
    private val clock: LocalClock,
) : ViewModel() {

    val state: StateFlow<SubscriptionsState> = observe()
        .map { items ->
            SubscriptionsState(
                isLoading = false,
                items = items,
                monthlyTotal = items.takeIf { it.isNotEmpty() }?.let { list ->
                    Money.sum(list.filter { it.lastAmount.currency == list.first().lastAmount.currency }.map { it.lastAmount }, list.first().lastAmount.currency)
                },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubscriptionsState())

    private val _effect = Channel<SubscriptionsEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    fun onEvent(event: SubscriptionsEvent) {
        when (event) {
            is SubscriptionsEvent.MakeFixed -> makeFixed(event.item)
            is SubscriptionsEvent.Dismiss -> {
                dismissals.dismiss(event.item.key)
                _effect.trySend(SubscriptionsEffect.Message("${event.item.name} bir daha önerilmeyecek"))
            }
        }
    }

    private fun makeFixed(item: RecurringPayment) {
        viewModelScope.launch {
            val fixed = FixedExpense(
                name = item.name,
                amount = item.lastAmount,
                dayOfMonth = item.dayOfMonth.coerceIn(1, 31),
                kind = kindFor(item.category),
                cardId = item.cardId,
                plan = FixedPlan.MONTHLY,
            )
            val id = fixedExpenses.add(fixed)
            // Bu ayki ödeme serbest harcama olarak yazılmışsa sabit giderin ödemesine çevrilir.
            val today = clock.today()
            if (item.lastDate.year == today.year && item.lastDate.month == today.month) {
                expenses.between(item.lastDate, item.lastDate)
                    .firstOrNull { it.fixedExpenseId == null && RecurringPayments.keyOf(it) == item.key }
                    ?.let { original ->
                        expenses.delete(original.id)
                        FixedPayments(expenses).markPaid(fixed.copy(id = id, cardId = original.cardId ?: fixed.cardId), original.grossAmount, original.date)
                    }
            }
            _effect.send(SubscriptionsEffect.Message("${item.name} sabit giderlere eklendi; ödeme gününden önce hatırlatırız"))
        }
    }

    private fun kindFor(category: Category): FixedExpenseKind = when (category) {
        Category.FATURA -> FixedExpenseKind.BILL
        Category.EV -> FixedExpenseKind.RENT
        else -> FixedExpenseKind.SUBSCRIPTION
    }
}
