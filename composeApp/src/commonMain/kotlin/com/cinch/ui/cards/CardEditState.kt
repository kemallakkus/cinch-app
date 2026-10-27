package com.cinch.ui.cards

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import com.cinch.ui.common.formatPercent

/** Hata bayrakları son kaydetme denemesinden kalır; ilgili alan düzenlenince kalkar. */
@Immutable
data class CardEditState(
    val isNew: Boolean,
    val isLoading: Boolean = !isNew,
    val name: TextFieldState = TextFieldState(),
    val statementDay: TextFieldState = TextFieldState(),
    val carriedBalance: TextFieldState = TextFieldState(),
    val monthlyRate: TextFieldState = TextFieldState(formatPercent(DEFAULT_MONTHLY_RATE)),
    val nameError: Boolean = false,
    val statementDayError: Boolean = false,
    val balanceError: Boolean = false,
    val rateError: Boolean = false,
    val isSaving: Boolean = false,
    val isConfirmingDelete: Boolean = false,
) {
    companion object {
        /** Türkiye'de kredi kartı akdi faizi + vergiler için makul başlangıç; kullanıcı değiştirir. */
        const val DEFAULT_MONTHLY_RATE = 4.9
    }
}

sealed interface CardEditEvent {
    data object Save : CardEditEvent
    data object DeleteRequested : CardEditEvent
    data object DeleteConfirmed : CardEditEvent
    data object DeleteDismissed : CardEditEvent
}

sealed interface CardEditEffect {
    data object Saved : CardEditEffect
    data class Deleted(val cardId: Long) : CardEditEffect
}
