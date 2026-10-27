package com.cinch.ui.month

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinch.navigation.IncomeEditRoute
import com.cinch.shared.domain.Income
import com.cinch.shared.domain.repository.IncomeRepository
import com.cinch.shared.money.Currency
import com.cinch.shared.money.MoneyInput
import com.cinch.ui.common.toInputText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class IncomeEditState(
    val isNew: Boolean,
    val isLoading: Boolean = !isNew,
    val name: TextFieldState = TextFieldState(),
    val amount: TextFieldState = TextFieldState(),
    val dayOfMonth: Int? = null,
    val nameError: Boolean = false,
    val amountError: Boolean = false,
    val isSaving: Boolean = false,
    val isConfirmingDelete: Boolean = false,
)

sealed interface IncomeEditEvent {
    data class DayPicked(val day: Int?) : IncomeEditEvent
    data object Save : IncomeEditEvent
    data object DeleteRequested : IncomeEditEvent
    data object DeleteConfirmed : IncomeEditEvent
    data object DeleteDismissed : IncomeEditEvent
}

sealed interface IncomeEditEffect {
    /** Kaydedildi ya da silindi: forma geri dönülmez. */
    data object Done : IncomeEditEffect
}

class IncomeEditViewModel(
    route: IncomeEditRoute,
    private val incomes: IncomeRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(IncomeEditState(isNew = route.incomeId == null))
    val state: StateFlow<IncomeEditState> = _state.asStateFlow()

    private val _effect = Channel<IncomeEditEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    private var existing: Income? = null

    init {
        route.incomeId?.let { id -> viewModelScope.launch { load(id) } }
        viewModelScope.launch {
            snapshotFlow { _state.value.name.text.toString() }.collect { _state.update { it.copy(nameError = false) } }
        }
        viewModelScope.launch {
            snapshotFlow { _state.value.amount.text.toString() }.collect { _state.update { it.copy(amountError = false) } }
        }
    }

    fun onEvent(event: IncomeEditEvent) {
        when (event) {
            is IncomeEditEvent.DayPicked -> _state.update { it.copy(dayOfMonth = event.day) }
            IncomeEditEvent.Save -> save()
            IncomeEditEvent.DeleteRequested -> _state.update { it.copy(isConfirmingDelete = true) }
            IncomeEditEvent.DeleteDismissed -> _state.update { it.copy(isConfirmingDelete = false) }
            IncomeEditEvent.DeleteConfirmed -> delete()
        }
    }

    private suspend fun load(id: Long) {
        val income = incomes.get(id)
        existing = income
        if (income != null) {
            _state.value.name.setTextAndPlaceCursorAtEnd(income.name)
            // Eski sürümden taşınan tutarsız "Maaş": alan boş açılır, kullanıcı tutarı yazar.
            if (!income.amount.isZero) _state.value.amount.setTextAndPlaceCursorAtEnd(income.amount.toInputText())
        }
        _state.update { it.copy(isLoading = false, dayOfMonth = income?.dayOfMonth) }
    }

    private fun save() {
        val current = _state.value
        if (current.isLoading || current.isSaving) return
        val currency = existing?.currency ?: Currency.TRY

        val name = current.name.text.toString().trim()
        val amount = MoneyInput.parse(current.amount.text.toString(), currency)?.takeIf { !it.isZero }
        if (name.isEmpty() || amount == null) {
            _state.update { it.copy(nameError = name.isEmpty(), amountError = amount == null) }
            return
        }

        val income = Income(id = existing?.id ?: 0, name = name, amount = amount, dayOfMonth = current.dayOfMonth)
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                if (existing == null) incomes.add(income) else incomes.update(income)
                _effect.send(IncomeEditEffect.Done)
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun delete() {
        val id = existing?.id ?: return
        _state.update { it.copy(isConfirmingDelete = false, isSaving = true) }
        viewModelScope.launch {
            incomes.delete(id)
            _effect.send(IncomeEditEffect.Done)
        }
    }
}
