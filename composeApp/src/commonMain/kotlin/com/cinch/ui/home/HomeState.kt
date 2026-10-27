package com.cinch.ui.home

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import com.cinch.shared.budget.BudgetStatus
import com.cinch.shared.domain.Category
import com.cinch.shared.domain.Expense
import com.cinch.shared.parser.ParsedEntry
import kotlinx.datetime.LocalDate

/**
 * @param status bütçe durumu; bütçe yoksa null.
 * @param weekExpenses bütçe haftasındaki harcamalar, en yeni üstte.
 * @param parsed hızlı kayıt alanının canlı ayrıştırması; alan boşsa null.
 * @param categoryOverride kullanıcının önizlemede seçtiği kategori; kaydederken tahmini ezer.
 */
@Immutable
data class HomeState(
    val isLoading: Boolean = true,
    val today: LocalDate,
    val status: BudgetStatus? = null,
    val weekExpenses: List<Expense> = emptyList(),
    val entry: TextFieldState = TextFieldState(),
    val parsed: ParsedEntry? = null,
    val categoryOverride: Category? = null,
    val isSaving: Boolean = false,
) {
    val entryCategory: Category? get() = categoryOverride ?: parsed?.category

    val canSave: Boolean
        get() = !isSaving && parsed?.grossAmount?.let { !it.isZero && !it.isNegative } == true
}

sealed interface HomeEvent {
    /** Ekran öne geldi: gece yarısı geçtiyse "bugün" yenilenir. */
    data object Resumed : HomeEvent
    data class CategoryPicked(val category: Category) : HomeEvent
    data object SaveEntry : HomeEvent
    data class DeleteExpense(val expense: Expense) : HomeEvent
    data class UndoDelete(val expense: Expense) : HomeEvent
}

sealed interface HomeEffect {
    /** Snackbar'da "Geri al" sunulur. */
    data class ExpenseDeleted(val expense: Expense) : HomeEffect
}
