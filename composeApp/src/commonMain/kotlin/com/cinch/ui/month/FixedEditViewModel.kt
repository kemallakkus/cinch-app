package com.cinch.ui.month

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinch.navigation.FixedEditRoute
import com.cinch.shared.domain.Card
import com.cinch.shared.domain.FixedExpense
import com.cinch.shared.domain.FixedExpenseKind
import com.cinch.shared.domain.FixedPlan
import com.cinch.shared.domain.repository.CardRepository
import com.cinch.shared.domain.repository.FixedExpenseRepository
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.shared.money.MoneyInput
import com.cinch.ui.common.label
import com.cinch.ui.common.toInputText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * @param amount ödeme şekline göre aylık tutar / taksit tutarı / "genelde ödediğim" (esnekte isteğe bağlı).
 * @param totalInstallments taksitli: toplam taksit sayısı.
 * @param paidBefore taksitli: şimdiye kadar ödenmiş taksit (boş = 0).
 * @param debt esnek: kalan borç (düzenlemede: eklendiği andaki borç).
 * @param cardId hangi kartla ödendiği; null = nakit/havale.
 */
@Immutable
data class FixedEditState(
    val isNew: Boolean,
    val isLoading: Boolean = true,
    val plan: FixedPlan = FixedPlan.MONTHLY,
    val kind: FixedExpenseKind = FixedExpenseKind.RENT,
    val name: TextFieldState = TextFieldState(),
    val amount: TextFieldState = TextFieldState(),
    val totalInstallments: TextFieldState = TextFieldState(),
    val paidBefore: TextFieldState = TextFieldState(),
    val debt: TextFieldState = TextFieldState(),
    val dayOfMonth: Int? = null,
    val cardId: Long? = null,
    val cards: List<Card> = emptyList(),
    val nameError: Boolean = false,
    val amountError: Boolean = false,
    val installmentsError: Boolean = false,
    val debtError: Boolean = false,
    val dayError: Boolean = false,
    val isSaving: Boolean = false,
    val isConfirmingDelete: Boolean = false,
)

sealed interface FixedEditEvent {
    data class PlanPicked(val plan: FixedPlan) : FixedEditEvent
    data class KindPicked(val kind: FixedExpenseKind) : FixedEditEvent
    data class DayPicked(val day: Int?) : FixedEditEvent
    data class CardPicked(val cardId: Long?) : FixedEditEvent
    data object Save : FixedEditEvent
    data object DeleteRequested : FixedEditEvent
    data object DeleteConfirmed : FixedEditEvent
    data object DeleteDismissed : FixedEditEvent
}

sealed interface FixedEditEffect {
    data object Done : FixedEditEffect
}

class FixedEditViewModel(
    route: FixedEditRoute,
    private val fixedExpenses: FixedExpenseRepository,
    private val cards: CardRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(FixedEditState(isNew = route.fixedId == null))
    val state: StateFlow<FixedEditState> = _state.asStateFlow()

    private val _effect = Channel<FixedEditEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    private var existing: FixedExpense? = null

    init {
        viewModelScope.launch { load(route.fixedId) }
        clearOnEdit({ it.name }) { it.copy(nameError = false) }
        clearOnEdit({ it.amount }) { it.copy(amountError = false) }
        clearOnEdit({ it.totalInstallments }) { it.copy(installmentsError = false) }
        clearOnEdit({ it.paidBefore }) { it.copy(installmentsError = false) }
        clearOnEdit({ it.debt }) { it.copy(debtError = false) }
    }

    fun onEvent(event: FixedEditEvent) {
        when (event) {
            is FixedEditEvent.PlanPicked -> pickPlan(event.plan)
            is FixedEditEvent.KindPicked -> pickKind(event.kind)
            is FixedEditEvent.DayPicked -> _state.update { it.copy(dayOfMonth = event.day, dayError = false) }
            is FixedEditEvent.CardPicked -> _state.update { it.copy(cardId = event.cardId) }
            FixedEditEvent.Save -> save()
            FixedEditEvent.DeleteRequested -> _state.update { it.copy(isConfirmingDelete = true) }
            FixedEditEvent.DeleteDismissed -> _state.update { it.copy(isConfirmingDelete = false) }
            FixedEditEvent.DeleteConfirmed -> delete()
        }
    }

    private suspend fun load(id: Long?) {
        val cardList = cards.all()
        val fixed = id?.let { fixedExpenses.get(it) }
        existing = fixed
        val s = _state.value
        if (fixed != null) {
            s.name.setTextAndPlaceCursorAtEnd(fixed.name)
            if (!fixed.amount.isZero) s.amount.setTextAndPlaceCursorAtEnd(fixed.amount.toInputText())
            fixed.totalInstallments?.let { s.totalInstallments.setTextAndPlaceCursorAtEnd(it.toString()) }
            if (fixed.paidBefore > 0) s.paidBefore.setTextAndPlaceCursorAtEnd(fixed.paidBefore.toString())
            fixed.debt?.let { s.debt.setTextAndPlaceCursorAtEnd(it.toInputText()) }
        } else {
            // Yeni kayıt: ad türün adıyla başlar ("Kira"); kullanıcı isterse değiştirir.
            s.name.setTextAndPlaceCursorAtEnd(s.kind.label)
        }
        _state.update {
            it.copy(
                isLoading = false,
                cards = cardList,
                plan = fixed?.plan ?: it.plan,
                kind = fixed?.kind ?: it.kind,
                dayOfMonth = fixed?.dayOfMonth,
                cardId = fixed?.cardId,
            )
        }
    }

    /** Borç seçilince ve tür hâlâ varsayılan "Kira" ise tür "Kredi / taksit" olur. */
    private fun pickPlan(plan: FixedPlan) {
        _state.update { it.copy(plan = plan, amountError = false, installmentsError = false, debtError = false) }
        if (plan != FixedPlan.MONTHLY && _state.value.kind == FixedExpenseKind.RENT) pickKind(FixedExpenseKind.LOAN)
    }

    /** Ad hâlâ bir türün kendi adıysa yeni türün adıyla değişir; kullanıcının yazdığı ada dokunulmaz. */
    private fun pickKind(kind: FixedExpenseKind) {
        val current = _state.value
        val name = current.name.text.toString().trim()
        if (name.isEmpty() || FixedExpenseKind.entries.any { it.label == name }) {
            current.name.setTextAndPlaceCursorAtEnd(kind.label)
        }
        _state.update { it.copy(kind = kind) }
    }

    private fun save() {
        val current = _state.value
        if (current.isLoading || current.isSaving) return
        val currency = existing?.currency ?: Currency.TRY

        val name = current.name.text.toString().trim()
        val amountText = current.amount.text.toString().trim()
        val parsedAmount = MoneyInput.parse(amountText, currency)?.takeIf { !it.isZero }
        // Esnek borçta "genelde ödediğim" isteğe bağlı: boşsa sıfır (tahmin son ödemelerden yapılır).
        val amount = if (current.plan == FixedPlan.FLEXIBLE && amountText.isEmpty()) Money.zero(currency) else parsedAmount
        val total = current.totalInstallments.text.toString().trim().toIntOrNull()?.takeIf { it > 0 }
        val paidText = current.paidBefore.text.toString().trim()
        val paidBefore = if (paidText.isEmpty()) 0 else paidText.toIntOrNull()
        val debt = MoneyInput.parse(current.debt.text.toString(), currency)?.takeIf { !it.isZero }
        val day = current.dayOfMonth

        val installmentsInvalid = current.plan == FixedPlan.INSTALLMENTS &&
            (total == null || paidBefore == null || paidBefore !in 0..total)
        val debtInvalid = current.plan == FixedPlan.FLEXIBLE && debt == null
        if (name.isEmpty() || amount == null || day == null || installmentsInvalid || debtInvalid) {
            _state.update {
                it.copy(
                    nameError = name.isEmpty(),
                    amountError = amount == null,
                    dayError = day == null,
                    installmentsError = installmentsInvalid,
                    debtError = debtInvalid,
                )
            }
            return
        }

        val fixed = FixedExpense(
            id = existing?.id ?: 0,
            name = name,
            amount = amount,
            dayOfMonth = day,
            kind = current.kind,
            cardId = current.cardId,
            plan = current.plan,
            totalInstallments = total.takeIf { current.plan == FixedPlan.INSTALLMENTS },
            paidBefore = if (current.plan == FixedPlan.INSTALLMENTS) paidBefore ?: 0 else 0,
            debt = debt.takeIf { current.plan == FixedPlan.FLEXIBLE },
        )
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                if (existing == null) fixedExpenses.add(fixed) else fixedExpenses.update(fixed)
                _effect.send(FixedEditEffect.Done)
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun delete() {
        val id = existing?.id ?: return
        _state.update { it.copy(isConfirmingDelete = false, isSaving = true) }
        viewModelScope.launch {
            fixedExpenses.delete(id)
            _effect.send(FixedEditEffect.Done)
        }
    }

    private fun clearOnEdit(field: (FixedEditState) -> TextFieldState, clear: (FixedEditState) -> FixedEditState) {
        val state = field(_state.value)
        viewModelScope.launch {
            snapshotFlow { state.text.toString() }.collect { _state.update(clear) }
        }
    }
}
