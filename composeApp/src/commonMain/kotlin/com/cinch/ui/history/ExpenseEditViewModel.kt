package com.cinch.ui.history

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinch.navigation.ExpenseEditRoute
import com.cinch.shared.category.Categorizer
import com.cinch.shared.domain.Card
import com.cinch.shared.domain.Category
import com.cinch.shared.domain.Expense
import com.cinch.shared.domain.repository.CardRepository
import com.cinch.shared.domain.repository.ExpenseRepository
import com.cinch.shared.domain.repository.FixedExpenseRepository
import com.cinch.shared.money.Money
import com.cinch.shared.money.MoneyInput
import com.cinch.shared.parser.QuickEntryParser
import com.cinch.shared.time.LocalClock
import com.cinch.ui.common.toInputText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

/**
 * @param text listede görünen açıklama ("195 market"); tutar değişip açıklamaya dokunulmadıysa içindeki
 *   sayı da kendiliğinden güncellenir.
 * @param share kullanıcının sınıra sayılan payı (1 = tamamı, 0.5 = yarısı, 0 = tamamı geri geldi).
 * @param isFixedPayment sabit gider ödemesi: kategori ve pay sorulmaz, haftalık paya zaten sayılmaz.
 * @param fixedName ödemenin ait olduğu sabit gider (silindiyse null).
 * @param isMissing kayıt bulunamadı (başka yerden silinmiş): ekran kapanır.
 */
@Immutable
data class ExpenseEditState(
    val isLoading: Boolean = true,
    val isMissing: Boolean = false,
    val today: LocalDate,
    val amount: TextFieldState = TextFieldState(),
    val text: TextFieldState = TextFieldState(),
    val category: Category = Category.DIGER,
    val date: LocalDate = today,
    val share: Double = 1.0,
    val cardId: Long? = null,
    val cards: List<Card> = emptyList(),
    val isFixedPayment: Boolean = false,
    val fixedName: String? = null,
    val amountError: Boolean = false,
    val isSaving: Boolean = false,
    val isConfirmingDelete: Boolean = false,
) {
    /** Pay seçenekleri; kayıttaki pay bunlardan biri değilse (ör. üçte biri) o da eklenir. */
    val shareOptions: List<Double>
        get() = if (DEFAULT_SHARES.any { it == share }) DEFAULT_SHARES else DEFAULT_SHARES + share

    companion object {
        val DEFAULT_SHARES = listOf(1.0, 0.5, 0.0)
    }
}

sealed interface ExpenseEditEvent {
    data class CategoryPicked(val category: Category) : ExpenseEditEvent
    data class DatePicked(val date: LocalDate) : ExpenseEditEvent
    data class SharePicked(val share: Double) : ExpenseEditEvent
    data class CardPicked(val cardId: Long?) : ExpenseEditEvent
    data object Save : ExpenseEditEvent
    data object DeleteRequested : ExpenseEditEvent
    data object DeleteConfirmed : ExpenseEditEvent
    data object DeleteDismissed : ExpenseEditEvent
}

/** Kaydedildi ya da silindi (silme önce onaylanır); ekran kapanır. */
sealed interface ExpenseEditEffect {
    data object Done : ExpenseEditEffect
}

/**
 * Kaydedilmiş harcamayı düzeltir: tutar, açıklama, kategori, tarih, pay ve kart. Kategori elle
 * değişirse kelimeler öğrenilir (hızlı kayıttaki gibi), bir dahaki sefere doğru tahmin edilir.
 */
class ExpenseEditViewModel(
    route: ExpenseEditRoute,
    private val expenses: ExpenseRepository,
    private val cards: CardRepository,
    private val fixedExpenses: FixedExpenseRepository,
    private val parser: QuickEntryParser,
    private val categorizer: Categorizer,
    private val clock: LocalClock,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(ExpenseEditState(today = clock.today()))
    val state: StateFlow<ExpenseEditState> = _state.asStateFlow()

    private val _effect = Channel<ExpenseEditEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    private var original: Expense? = null

    init {
        viewModelScope.launch { load(route.expenseId) }
        val amount = _state.value.amount
        viewModelScope.launch {
            snapshotFlow { amount.text.toString() }.collect { _state.update { it.copy(amountError = false) } }
        }
    }

    fun onEvent(event: ExpenseEditEvent) {
        when (event) {
            is ExpenseEditEvent.CategoryPicked -> _state.update { it.copy(category = event.category) }
            is ExpenseEditEvent.DatePicked -> _state.update { it.copy(date = minOf(event.date, it.today)) }
            is ExpenseEditEvent.SharePicked -> _state.update { it.copy(share = event.share.coerceIn(0.0, 1.0)) }
            is ExpenseEditEvent.CardPicked -> _state.update { it.copy(cardId = event.cardId) }
            ExpenseEditEvent.Save -> save()
            ExpenseEditEvent.DeleteRequested -> _state.update { it.copy(isConfirmingDelete = true) }
            ExpenseEditEvent.DeleteDismissed -> _state.update { it.copy(isConfirmingDelete = false) }
            ExpenseEditEvent.DeleteConfirmed -> delete()
        }
    }

    private suspend fun load(id: Long) {
        val expense = expenses.get(id)
        if (expense == null) {
            _state.update { it.copy(isLoading = false, isMissing = true) }
            return
        }
        original = expense
        val cardList = cards.all()
        val fixedName = expense.fixedExpenseId?.let { fixedExpenses.get(it)?.name }
        val s = _state.value
        s.amount.setTextAndPlaceCursorAtEnd(expense.grossAmount.toInputText())
        s.text.setTextAndPlaceCursorAtEnd(expense.rawText)
        _state.update {
            it.copy(
                isLoading = false,
                category = expense.category,
                date = expense.date,
                share = expense.chargeableFraction,
                cardId = expense.cardId,
                cards = cardList,
                isFixedPayment = expense.isFixedPayment,
                fixedName = fixedName,
            )
        }
    }

    private fun save() {
        val current = _state.value
        val before = original ?: return
        if (current.isLoading || current.isSaving) return
        val amount = MoneyInput.parse(current.amount.text.toString(), before.grossAmount.currency)
            ?.takeIf { !it.isZero && !it.isNegative }
        if (amount == null) {
            _state.update { it.copy(amountError = true) }
            return
        }

        val typed = current.text.text.toString().trim()
        val textEdited = typed != before.rawText.trim()
        // Açıklamaya dokunulmadıysa içindeki eski tutar yenisiyle değişir: "195 market" → "200 market".
        val rawText = if (textEdited) typed else replaceAmountInText(before.rawText, before.grossAmount, amount)

        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val tokens = if (textEdited) {
                    withContext(ioDispatcher) { parser.parse(rawText, current.date, cards = current.cards).tokens }
                } else {
                    before.tokens
                }
                val category = if (before.isFixedPayment) before.category else current.category
                if (category != before.category) {
                    withContext(ioDispatcher) { categorizer.learnFromCorrection(tokens, category) }
                }
                expenses.update(
                    before.copy(
                        date = current.date,
                        grossAmount = amount,
                        chargeableFraction = if (before.isFixedPayment) before.chargeableFraction else current.share,
                        category = category,
                        cardId = current.cardId,
                        rawText = rawText,
                        tokens = tokens,
                    ),
                )
                _effect.send(ExpenseEditEffect.Done)
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun delete() {
        val expense = original ?: return
        _state.update { it.copy(isConfirmingDelete = false, isSaving = true) }
        viewModelScope.launch {
            expenses.delete(expense.id)
            _effect.send(ExpenseEditEffect.Done)
        }
    }
}

/**
 * Metindeki eski tutarı yenisiyle değiştirir: "195 market" + 200 ₺ → "200 market". Eski tutar metinde
 * sayı olarak geçmiyorsa (ör. "yarım kilo kahve") metin olduğu gibi kalır.
 */
internal fun replaceAmountInText(text: String, old: Money, new: Money): String {
    if (old == new) return text
    val words = text.split(' ')
    val index = words.indexOfFirst { word ->
        word.firstOrNull()?.isDigit() == true && MoneyInput.parse(word, old.currency) == old
    }
    if (index < 0) return text
    return words.toMutableList().also { it[index] = new.toInputText() }.joinToString(" ")
}
