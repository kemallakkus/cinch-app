package com.cinch.ui.home

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import com.cinch.shared.budget.BudgetStatus
import com.cinch.shared.domain.Card
import com.cinch.shared.domain.Category
import com.cinch.shared.domain.Expense
import com.cinch.shared.parser.ParsedEntry
import kotlinx.datetime.LocalDate

/**
 * @param status bütçe durumu; bütçe yoksa null.
 * @param weekExpenses bütçe haftasındaki harcamalar, en yeni üstte.
 * @param parsed hızlı kayıt alanının canlı ayrıştırması; alan boşsa null.
 * @param categoryOverride kullanıcının önizlemede seçtiği kategori; kaydederken tahmini ezer.
 * @param cards kullanıcının kartları (önizlemedeki kart seçimi ve listede kart adı için).
 * @param cardOverride önizlemede elle seçilen kart (ya da nakit); kaydederken metinden bulunanı ezer.
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
    val cards: List<Card> = emptyList(),
    val cardOverride: CardSelection? = null,
    val isSaving: Boolean = false,
) {
    val entryCategory: Category? get() = categoryOverride ?: parsed?.category

    /** Kaydedilecek kart: elle seçilen, yoksa metinde adı geçen; null = nakit. */
    val entryCardId: Long? get() = if (cardOverride != null) cardOverride.cardId else parsed?.cardId

    val canSave: Boolean
        get() = !isSaving && parsed?.grossAmount?.let { !it.isZero && !it.isNegative } == true
}

/** Elle yapılan kart seçimi. [cardId] null = nakit (metinde kart adı geçse bile). */
data class CardSelection(val cardId: Long?)

sealed interface HomeEvent {
    /** Ekran öne geldi: gece yarısı geçtiyse "bugün" yenilenir. */
    data object Resumed : HomeEvent
    data class CategoryPicked(val category: Category) : HomeEvent
    data class CardPicked(val cardId: Long?) : HomeEvent
    data object SaveEntry : HomeEvent
    data class DeleteExpense(val expense: Expense) : HomeEvent
    data class UndoDelete(val expense: Expense) : HomeEvent
}

sealed interface HomeEffect {
    /** Snackbar'da "Geri al" sunulur. */
    data class ExpenseDeleted(val expense: Expense) : HomeEffect
}
