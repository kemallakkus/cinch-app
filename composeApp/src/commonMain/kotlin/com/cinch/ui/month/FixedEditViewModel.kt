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
import com.cinch.shared.domain.repository.CardRepository
import com.cinch.shared.domain.repository.FixedExpenseRepository
import com.cinch.shared.money.Currency
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

/** @param cardId hangi kartla ödendiği; null = nakit/havale. */
@Immutable
data class FixedEditState(
    val isNew: Boolean,
    val isLoading: Boolean = true,
    val kind: FixedExpenseKind = FixedExpenseKind.RENT,
    val name: TextFieldState = TextFieldState(),
    val amount: TextFieldState = TextFieldState(),
    val dayOfMonth: Int? = null,
    val cardId: Long? = null,
    val cards: List<Card> = emptyList(),
    val nameError: Boolean = false,
    val amountError: Boolean = false,
    val dayError: Boolean = false,
    val isSaving: Boolean = false,
    val isConfirmingDelete: Boolean = false,
)

sealed interface FixedEditEvent {
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
        viewModelScope.launch {
            snapshotFlow { _state.value.name.text.toString() }.collect { _state.update { it.copy(nameError = false) } }
        }
        viewModelScope.launch {
            snapshotFlow { _state.value.amount.text.toString() }.collect { _state.update { it.copy(amountError = false) } }
        }
    }

    fun onEvent(event: FixedEditEvent) {
        when (event) {
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
        if (fixed != null) {
            _state.value.name.setTextAndPlaceCursorAtEnd(fixed.name)
            _state.value.amount.setTextAndPlaceCursorAtEnd(fixed.amount.toInputText())
        } else {
            // Yeni kayıt: ad türün adıyla başlar ("Kira"); kullanıcı isterse değiştirir.
            _state.value.name.setTextAndPlaceCursorAtEnd(_state.value.kind.label)
        }
        _state.update {
            it.copy(
                isLoading = false,
                cards = cardList,
                kind = fixed?.kind ?: it.kind,
                dayOfMonth = fixed?.dayOfMonth,
                cardId = fixed?.cardId,
            )
        }
    }

    /** Ad hâlâ önceki türün kendi adıysa yeni türün adıyla değişir; kullanıcının yazdığı ada dokunulmaz. */
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
        val amount = MoneyInput.parse(current.amount.text.toString(), currency)?.takeIf { !it.isZero }
        val day = current.dayOfMonth
        if (name.isEmpty() || amount == null || day == null) {
            _state.update { it.copy(nameError = name.isEmpty(), amountError = amount == null, dayError = day == null) }
            return
        }

        val fixed = FixedExpense(
            id = existing?.id ?: 0,
            name = name,
            amount = amount,
            dayOfMonth = day,
            kind = current.kind,
            cardId = current.cardId,
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
}
