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
    val suggestion: LimitSuggestion? = null,
    val isSaving: Boolean = false,
)

/**
 * Gelir ve sabit giderlerden haftalık sınır önerisi.
 *
 * @param weekly önerilen haftalık sınır; gelir yoksa ya da sabit giderler geliri aşıyorsa null.
 */
@Immutable
data class LimitSuggestion(val monthlyIncome: Money, val monthlyFixed: Money, val weekly: Money?)

sealed interface BudgetSetupEvent {
    data class WeekStartPicked(val day: DayOfWeek) : BudgetSetupEvent

    /** Öneriyi sınır alanına yaz. */
    data object UseSuggestion : BudgetSetupEvent
    data object Save : BudgetSetupEvent
}

sealed interface BudgetSetupEffect {
    data object Saved : BudgetSetupEffect
}
