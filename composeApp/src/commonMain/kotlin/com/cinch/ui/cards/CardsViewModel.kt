package com.cinch.ui.cards

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinch.shared.domain.Card
import com.cinch.shared.domain.repository.CardRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@Immutable
data class CardsState(val isLoading: Boolean = true, val cards: List<Card> = emptyList())

class CardsViewModel(cards: CardRepository) : ViewModel() {
    val state: StateFlow<CardsState> = cards.observeAll()
        .map { CardsState(isLoading = false, cards = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CardsState())
}
