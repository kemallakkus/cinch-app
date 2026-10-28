package com.cinch.ui.receipt

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinch.navigation.ReceiptReviewRoute
import com.cinch.shared.budget.FixedPayments
import com.cinch.shared.budget.progress
import com.cinch.shared.category.Categorizer
import com.cinch.shared.domain.Card
import com.cinch.shared.domain.Category
import com.cinch.shared.domain.Expense
import com.cinch.shared.domain.FixedExpense
import com.cinch.shared.domain.repository.CardRepository
import com.cinch.shared.domain.repository.ExpenseRepository
import com.cinch.shared.domain.repository.FixedExpenseRepository
import com.cinch.shared.money.Currency
import com.cinch.shared.money.MoneyInput
import com.cinch.shared.parser.QuickEntryParser
import com.cinch.shared.receipt.BillMatcher
import com.cinch.shared.receipt.ReceiptDraft
import com.cinch.shared.receipt.ReceiptParser
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
 * @param draft okunanlar (okunan metin, son ödeme tarihi gibi ipuçları için).
 * @param text listede görünecek açıklama; mağaza adıyla başlar.
 * @param fixedOptions faturanın bağlanabileceği sabit giderler (bitmiş borçlar hariç).
 * @param linkedFixedId seçili sabit gider: kaydedince "Bu ay"da ödendi işaretlenir. null = normal harcama.
 * @param paidThisMonth bu ay zaten ödendi işaretli sabit giderler (çift ödeme uyarısı için).
 */
@Immutable
data class ReceiptReviewState(
    val isLoading: Boolean = true,
    val today: LocalDate,
    val draft: ReceiptDraft? = null,
    val amount: TextFieldState = TextFieldState(),
    val text: TextFieldState = TextFieldState(),
    val category: Category = Category.DIGER,
    val date: LocalDate = today,
    val cardId: Long? = null,
    val cards: List<Card> = emptyList(),
    val fixedOptions: List<FixedExpense> = emptyList(),
    val linkedFixedId: Long? = null,
    val paidThisMonth: Set<Long> = emptySet(),
    val amountError: Boolean = false,
    val isSaving: Boolean = false,
) {
    val linkedFixed: FixedExpense? get() = fixedOptions.firstOrNull { it.id == linkedFixedId }

    /** Fatura okunduysa ya da bir sabit giderle eşleştiyse "Hangi sabit gider?" sorulur. */
    val showFixedLink: Boolean get() = fixedOptions.isNotEmpty() && (draft?.isBill == true || linkedFixedId != null)
}

sealed interface ReceiptReviewEvent {
    data class CategoryPicked(val category: Category) : ReceiptReviewEvent
    data class DatePicked(val date: LocalDate) : ReceiptReviewEvent
    data class CardPicked(val cardId: Long?) : ReceiptReviewEvent

    /** null = sabit gidere bağlama, normal harcama olarak kaydet. */
    data class FixedLinked(val fixedId: Long?) : ReceiptReviewEvent
    data object Save : ReceiptReviewEvent
}

sealed interface ReceiptReviewEffect {
    /** Kaydedildi. [fixedName] doluysa sabit gider ödendi işaretlendi. */
    data class Saved(val fixedName: String? = null) : ReceiptReviewEffect
}

/**
 * Fişten okunanları gösterir, kullanıcı düzeltip kaydeder; okuma hatası olabileceği için onaysız kayıt yok.
 * Fatura bir sabit giderle eşleşirse ([BillMatcher]) o seçili gelir: kaydedince "ödendi" işaretlenir.
 */
class ReceiptReviewViewModel(
    route: ReceiptReviewRoute,
    private val receiptParser: ReceiptParser,
    private val entryParser: QuickEntryParser,
    private val categorizer: Categorizer,
    private val expenses: ExpenseRepository,
    private val cards: CardRepository,
    private val fixedExpenses: FixedExpenseRepository,
    private val fixedPayments: FixedPayments,
    private val clock: LocalClock,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(ReceiptReviewState(today = clock.today()))
    val state: StateFlow<ReceiptReviewState> = _state.asStateFlow()

    private val _effect = Channel<ReceiptReviewEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    init {
        viewModelScope.launch { load(route.text) }
        val amount = _state.value.amount
        viewModelScope.launch {
            snapshotFlow { amount.text.toString() }.collect { _state.update { it.copy(amountError = false) } }
        }
    }

    fun onEvent(event: ReceiptReviewEvent) {
        when (event) {
            is ReceiptReviewEvent.CategoryPicked -> _state.update { it.copy(category = event.category) }
            is ReceiptReviewEvent.DatePicked -> _state.update { it.copy(date = minOf(event.date, it.today)) }
            is ReceiptReviewEvent.CardPicked -> _state.update { it.copy(cardId = event.cardId) }
            is ReceiptReviewEvent.FixedLinked -> _state.update { s ->
                // Sabit gidere bağlanınca kartı o giderin kartı olur; kullanıcı yine değiştirebilir.
                val fixed = s.fixedOptions.firstOrNull { it.id == event.fixedId }
                s.copy(linkedFixedId = fixed?.id, cardId = fixed?.cardId ?: s.cardId)
            }
            ReceiptReviewEvent.Save -> save()
        }
    }

    private suspend fun load(text: String) {
        val today = clock.today()
        val draft = withContext(ioDispatcher) { receiptParser.parse(text, today) }
        val cardList = cards.all()
        val payments = expenses.fixedPayments()
        val currency = draft.amount?.currency ?: Currency.TRY
        val options = fixedExpenses.all()
            .filter { it.currency == currency && it.progress(payments, today)?.isFinished != true }
        val match = BillMatcher.match(draft, options)
        val paid = payments
            .filter { it.date.year == today.year && it.date.month == today.month }
            .mapNotNull { it.fixedExpenseId }
            .toSet()

        val s = _state.value
        draft.amount?.let { s.amount.setTextAndPlaceCursorAtEnd(it.toInputText()) }
        s.text.setTextAndPlaceCursorAtEnd(draft.merchant.orEmpty())
        _state.update {
            it.copy(
                isLoading = false,
                draft = draft,
                category = draft.category,
                // Faturanın üstündeki tarih düzenleme tarihidir; ödeme bugün yapılıyor.
                date = if (draft.isBill) today else draft.date ?: today,
                // Fişte kartla ödendiği yazıyorsa ve tek kart varsa o; değilse nakit (kullanıcı değiştirir).
                cardId = match?.cardId ?: cardList.singleOrNull()?.id?.takeIf { draft.paidByCard },
                cards = cardList,
                fixedOptions = options,
                linkedFixedId = match?.id,
                paidThisMonth = paid,
            )
        }
    }

    private fun save() {
        val current = _state.value
        val draft = current.draft ?: return
        if (current.isLoading || current.isSaving) return
        val currency = draft.amount?.currency ?: Currency.TRY
        val amount = MoneyInput.parse(current.amount.text.toString(), currency)?.takeIf { !it.isZero && !it.isNegative }
        if (amount == null) {
            _state.update { it.copy(amountError = true) }
            return
        }

        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val fixed = current.linkedFixed
                if (fixed != null) {
                    fixedPayments.markPaid(fixed.copy(cardId = current.cardId), amount, current.date)
                    _effect.send(ReceiptReviewEffect.Saved(fixedName = fixed.name))
                    return@launch
                }
                val description = current.text.text.toString().trim()
                val tokens = withContext(ioDispatcher) {
                    entryParser.parse(description, current.date, cards = current.cards).tokens
                }
                if (current.category != draft.category) {
                    // Hızlı kayıttaki gibi: mağaza adı bir dahaki sefere doğru kategoriye düşsün.
                    withContext(ioDispatcher) { categorizer.learnFromCorrection(tokens, current.category) }
                }
                expenses.add(
                    Expense(
                        date = current.date,
                        grossAmount = amount,
                        category = current.category,
                        cardId = current.cardId,
                        note = RECEIPT_NOTE,
                        tokens = tokens,
                        rawText = description,
                    ),
                )
                _effect.send(ReceiptReviewEffect.Saved())
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }

    private companion object {
        /** Notta durur; geçmişte "fiş" diye aranınca fişten girilenler bulunur. */
        const val RECEIPT_NOTE = "Fişten okundu"
    }
}
