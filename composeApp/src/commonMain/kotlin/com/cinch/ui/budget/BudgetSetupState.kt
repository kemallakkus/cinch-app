package com.cinch.ui.budget

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import com.cinch.shared.money.Money
import kotlinx.datetime.DayOfWeek

/**
 * @param dailyHint yazılan haftalık sınırın günlük karşılığı; tutar okunamıyorsa null.
 * @param limitError son kaydetme denemesinde sınır geçersizdi; alan düzenlenince kalkar.
 */
@Immutable
data class BudgetSetupState(
    val isOnboarding: Boolean,
    val isLoading: Boolean = true,
    val weeklyLimit: TextFieldState = TextFieldState(),
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val dailyHint: Money? = null,
    val limitError: Boolean = false,
    val isSaving: Boolean = false,
)

sealed interface BudgetSetupEvent {
    data class WeekStartPicked(val day: DayOfWeek) : BudgetSetupEvent
    data object Save : BudgetSetupEvent
}

sealed interface BudgetSetupEffect {
    data object Saved : BudgetSetupEffect
}
