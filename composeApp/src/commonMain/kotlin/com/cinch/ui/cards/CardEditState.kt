package com.cinch.ui.cards

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import com.cinch.shared.domain.CardType
import com.cinch.ui.common.formatPercent

/**
 * Hata bayrakları son kaydetme denemesinden kalır; ilgili alan düzenlenince kalkar.
 *
 * @param advancedOpen kesim günü ve faiz ("Gelişmiş") açık mı; çoğu kullanıcı bunları banka ekranından okutur.
 * @param hasLegacyBalance kartta elle girilmiş eski borç var: yalnız o zaman alan gösterilir (silinebilsin diye).
 *   Yeni kartta sorulmaz; borç banka ekranından okunur.
 */
@Immutable
data class CardEditState(
    val isNew: Boolean,
    val isLoading: Boolean = !isNew,
    val type: CardType = CardType.CREDIT,
    val name: TextFieldState = TextFieldState(),
    val statementDay: Int? = null,
    val carriedBalance: TextFieldState = TextFieldState(),
    val monthlyRate: TextFieldState = TextFieldState(formatPercent(DEFAULT_MONTHLY_RATE)),
    val nameError: Boolean = false,
    val balanceError: Boolean = false,
    val rateError: Boolean = false,
    val isSaving: Boolean = false,
    val isConfirmingDelete: Boolean = false,
    val advancedOpen: Boolean = false,
    val hasLegacyBalance: Boolean = false,
) {
    companion object {
        /** Türkiye'de kredi kartı akdi faizi + vergiler için makul başlangıç; kullanıcı değiştirir. */
        const val DEFAULT_MONTHLY_RATE = 4.9
    }
}

sealed interface CardEditEvent {
    data class TypePicked(val type: CardType) : CardEditEvent
    data class StatementDayPicked(val day: Int?) : CardEditEvent
    data object AdvancedToggled : CardEditEvent
    data object Save : CardEditEvent
    data object DeleteRequested : CardEditEvent
    data object DeleteConfirmed : CardEditEvent
    data object DeleteDismissed : CardEditEvent
}

sealed interface CardEditEffect {
    /** Var olan kart güncellendi. */
    data object Saved : CardEditEffect

    /** Yeni kart eklendi: kartın sayfası açılır (oradan banka ekranı okutulur). */
    data class Created(val cardId: Long) : CardEditEffect
    data class Deleted(val cardId: Long) : CardEditEffect
}
