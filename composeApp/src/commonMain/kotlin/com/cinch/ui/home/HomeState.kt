package com.cinch.ui.home

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import com.cinch.shared.budget.BudgetStatus
import com.cinch.shared.domain.Card
import com.cinch.shared.domain.Category
import com.cinch.shared.domain.Expense
import com.cinch.shared.money.Money
import com.cinch.shared.parser.ParsedEntry
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
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

    /** Bütçe haftasının 7 günü ve her gün harcanan (kullanıcının payı); hafta çubukları için. */
    val weekDays: List<DaySpend>
        get() {
            val s = status ?: return emptyList()
            val currency = s.weeklyLimit.currency
            val fairShare = Money(s.weeklyLimit.minor / 7, currency)
            return (0 until 7).map { offset ->
                val date = s.weekStart.plus(DatePeriod(days = offset))
                val spent = Money.sum(
                    weekExpenses.filter { it.date == date && it.grossAmount.currency == currency }.map { it.chargeableAmount },
                    currency,
                )
                DaySpend(date = date, spent = spent, fairShare = fairShare, isToday = date == today, isFuture = date > today)
            }
        }
}

/**
 * @param fairShare haftalık sınırın yedide biri: günün "adil payı".
 */
@Immutable
data class DaySpend(
    val date: LocalDate,
    val spent: Money,
    val fairShare: Money,
    val isToday: Boolean,
    val isFuture: Boolean,
) {
    /** Harcamanın adil paya oranı (1 = tam payı kadar). */
    val ratio: Float get() = if (fairShare.isZero) 0f else spent.minor.toFloat() / fairShare.minor.toFloat()
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
    /** Kayıt eklendi. [onTrack] = eklendikten sonra hâlâ payın içindesin: küçük bir kutlama yapılır. */
    data class EntrySaved(val onTrack: Boolean) : HomeEffect

    /** Snackbar'da "Geri al" sunulur. */
    data class ExpenseDeleted(val expense: Expense) : HomeEffect
}
