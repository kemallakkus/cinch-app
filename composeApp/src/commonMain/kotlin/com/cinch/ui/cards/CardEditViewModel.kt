package com.cinch.ui.cards

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinch.navigation.CardEditRoute
import com.cinch.shared.domain.Card
import com.cinch.shared.domain.repository.CardRepository
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.shared.money.MoneyInput
import com.cinch.ui.common.formatPercent
import com.cinch.ui.common.parsePercent
import com.cinch.ui.common.toInputText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CardEditViewModel(
    private val route: CardEditRoute,
    private val cards: CardRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CardEditState(isNew = route.cardId == null))
    val state: StateFlow<CardEditState> = _state.asStateFlow()

    private val _effect = Channel<CardEditEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    /** Düzenlenen kart; para birimi ve id buradan korunur. */
    private var existing: Card? = null

    init {
        route.cardId?.let { id -> viewModelScope.launch { load(id) } }
        clearErrorOnEdit({ it.name }) { it.copy(nameError = false) }
        clearErrorOnEdit({ it.statementDay }) { it.copy(statementDayError = false) }
        clearErrorOnEdit({ it.carriedBalance }) { it.copy(balanceError = false) }
        clearErrorOnEdit({ it.monthlyRate }) { it.copy(rateError = false) }
    }

    fun onEvent(event: CardEditEvent) {
        when (event) {
            CardEditEvent.Save -> save()
            CardEditEvent.DeleteRequested -> _state.update { it.copy(isConfirmingDelete = true) }
            CardEditEvent.DeleteDismissed -> _state.update { it.copy(isConfirmingDelete = false) }
            CardEditEvent.DeleteConfirmed -> delete()
        }
    }

    private suspend fun load(id: Long) {
        val card = cards.get(id)
        existing = card
        if (card != null) {
            val s = _state.value
            s.name.setTextAndPlaceCursorAtEnd(card.name)
            s.statementDay.setTextAndPlaceCursorAtEnd(card.statementDay?.toString().orEmpty())
            s.carriedBalance.setTextAndPlaceCursorAtEnd(
                if (card.carriedBalance.isZero) "" else card.carriedBalance.toInputText(),
            )
            s.monthlyRate.setTextAndPlaceCursorAtEnd(formatPercent(card.monthlyRatePercent))
        }
        _state.update { it.copy(isLoading = false) }
    }

    private fun save() {
        val current = _state.value
        if (current.isLoading || current.isSaving) return
        val currency = existing?.currency ?: Currency.TRY

        val name = current.name.text.toString().trim()
        val dayText = current.statementDay.text.toString().trim()
        val statementDay = dayText.toIntOrNull()?.takeIf { it in 1..31 }
        val balanceText = current.carriedBalance.text.toString().trim()
        // Boş borç sıfır demek: ekstresini her ay tam ödeyen kullanıcı için.
        val balance = if (balanceText.isEmpty()) Money.zero(currency) else MoneyInput.parse(balanceText, currency)
        val rate = parsePercent(current.monthlyRate.text.toString())

        val errors = current.copy(
            nameError = name.isEmpty(),
            statementDayError = dayText.isNotEmpty() && statementDay == null,
            balanceError = balance == null,
            rateError = rate == null,
        )
        if (errors.nameError || errors.statementDayError || errors.balanceError || errors.rateError) {
            _state.value = errors
            return
        }

        val card = Card(
            id = existing?.id ?: 0,
            name = name,
            carriedBalance = balance!!,
            monthlyRatePercent = rate!!,
            statementDay = statementDay,
        )
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                if (existing == null) cards.add(card) else cards.update(card)
                _effect.send(CardEditEffect.Saved)
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun delete() {
        val id = existing?.id ?: return
        _state.update { it.copy(isConfirmingDelete = false, isSaving = true) }
        viewModelScope.launch {
            cards.delete(id)
            _effect.send(CardEditEffect.Deleted(id))
        }
    }

    private fun clearErrorOnEdit(field: (CardEditState) -> TextFieldState, clear: (CardEditState) -> CardEditState) {
        val state = field(_state.value)
        viewModelScope.launch {
            snapshotFlow { state.text.toString() }.collect { _state.update(clear) }
        }
    }
}
