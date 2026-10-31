package com.cinch.ui.cards

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinch.shared.cards.CardOverview
import com.cinch.shared.cards.ObserveCardOverviews
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@Immutable
data class CardsState(val isLoading: Boolean = true, val cards: List<CardOverview> = emptyList())

/** Kart listesi: her kart için ödenecek tutar ve son ödeme ya da bu dönemin harcaması. */
class CardsViewModel(overviews: ObserveCardOverviews) : ViewModel() {
    val state: StateFlow<CardsState> = overviews()
        .map { CardsState(isLoading = false, cards = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CardsState())
}
