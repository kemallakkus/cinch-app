package com.cinch.ui.statement

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinch.navigation.StatementReviewRoute
import com.cinch.shared.category.Categorizer
import com.cinch.shared.domain.Card
import com.cinch.shared.domain.CardStatement
import com.cinch.shared.domain.Category
import com.cinch.shared.domain.Expense
import com.cinch.shared.domain.repository.CardRepository
import com.cinch.shared.domain.repository.CardStatementRepository
import com.cinch.shared.domain.repository.ExpenseRepository
import com.cinch.shared.money.Currency
import com.cinch.shared.money.MoneyInput
import com.cinch.shared.parser.QuickEntryParser
import com.cinch.shared.receipt.OcrCodec
import com.cinch.shared.statement.StatementDraft
import com.cinch.shared.statement.StatementReader
import com.cinch.shared.statement.StatementTransaction
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
import kotlinx.datetime.daysUntil
import kotlin.math.abs

/** Bankadaki hareketin Cinch'teki kayıtlarla durumu. */
enum class TransactionStatus {
    /** Aynı tutar, yakın tarih: zaten girilmiş. */
    MATCHED,

    /** Bankada var, Cinch'te yok: eklenmesi önerilir. */
    MISSING,

    /** Taksit: alışveriş tek seferde girilmiş olabilir, kendiliğinden eklenmez. */
    INSTALLMENT,

    /** Ödeme ya da iade (eksi tutar): harcama değil. */
    PAYMENT,
}

@Immutable
data class TransactionRow(
    val transaction: StatementTransaction,
    val status: TransactionStatus,
    val selected: Boolean,
    val category: Category,
)

/**
 * @param cardId okunanların yazılacağı kart; son 4 haneden, kart detayından ya da tek kart varsa kendiliğinden.
 * @param balance kalan ekstre borcu (düzeltilebilir); [minimum] asgari ödeme.
 * @param transactions bankadaki hareketler ve Cinch'teki durumları; seçili eksikler kaydedince eklenir.
 */
@Immutable
data class StatementReviewState(
    val isLoading: Boolean = true,
    val today: LocalDate,
    val draft: StatementDraft? = null,
    val cards: List<Card> = emptyList(),
    val cardId: Long? = null,
    val cardError: Boolean = false,
    val dueDate: LocalDate? = null,
    val statementDate: LocalDate? = null,
    val balance: TextFieldState = TextFieldState(),
    val minimum: TextFieldState = TextFieldState(),
    val transactions: List<TransactionRow> = emptyList(),
    val isSaving: Boolean = false,
) {
    val selectedCard: Card? get() = cards.firstOrNull { it.id == cardId }

    /** Okunan son 4 hane seçili karta yazılacak (kartta yoksa ya da farklıysa): bir dahaki sefere kart kendiliğinden seçilir. */
    val last4ToSave: String? get() = draft?.last4?.takeIf { last4 -> selectedCard?.let { it.last4 != last4 } == true }

    val selectedCount: Int get() = transactions.count { it.selected }
}

sealed interface StatementReviewEvent {
    data class CardPicked(val cardId: Long) : StatementReviewEvent
    data class DueDatePicked(val date: LocalDate) : StatementReviewEvent
    data class StatementDatePicked(val date: LocalDate) : StatementReviewEvent
    data class TransactionToggled(val index: Int) : StatementReviewEvent
    data object Save : StatementReviewEvent
}

sealed interface StatementReviewEffect {
    /** Kaydedildi; [addedExpenses] bankadan eklenen eksik harcama sayısı. */
    data class Saved(val addedExpenses: Int) : StatementReviewEffect
}

/**
 * Banka ekranı fotoğraflarından okunanları gösterir; kullanıcı düzeltip kaydeder. Kaydedince ekstre okuması
 * saklanır (son ödeme hatırlatması ve kart detayı için), kartın son 4 hanesi ve ekstre günü (boşsa) yazılır,
 * seçili eksik hareketler o karta harcama olarak eklenir.
 */
class StatementReviewViewModel(
    private val route: StatementReviewRoute,
    private val reader: StatementReader,
    private val cards: CardRepository,
    private val statements: CardStatementRepository,
    private val expenses: ExpenseRepository,
    private val categorizer: Categorizer,
    private val entryParser: QuickEntryParser,
    private val clock: LocalClock,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(StatementReviewState(today = clock.today()))
    val state: StateFlow<StatementReviewState> = _state.asStateFlow()

    private val _effect = Channel<StatementReviewEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    init {
        viewModelScope.launch { load() }
    }

    fun onEvent(event: StatementReviewEvent) {
        when (event) {
            is StatementReviewEvent.CardPicked -> {
                _state.update { it.copy(cardId = event.cardId, cardError = false) }
                viewModelScope.launch { refreshTransactions() }
            }
            is StatementReviewEvent.DueDatePicked -> _state.update { it.copy(dueDate = event.date) }
            is StatementReviewEvent.StatementDatePicked -> _state.update { it.copy(statementDate = minOf(event.date, it.today)) }
            is StatementReviewEvent.TransactionToggled -> _state.update { s ->
                s.copy(
                    transactions = s.transactions.mapIndexed { i, row ->
                        // Eşleşen ve ödeme satırları eklenmez; yalnız eksik ve taksit seçilebilir.
                        if (i == event.index && row.status in SELECTABLE) row.copy(selected = !row.selected) else row
                    },
                )
            }
            StatementReviewEvent.Save -> save()
        }
    }

    private suspend fun load() {
        val today = _state.value.today
        val draft = withContext(ioDispatcher) { reader.read(OcrCodec.decode(route.ocr), today) }
        val cardList = cards.all()
        val cardId = route.cardId?.takeIf { id -> cardList.any { it.id == id } }
            ?: draft.last4?.let { last4 -> cardList.firstOrNull { it.last4 == last4 }?.id }
            ?: cardList.singleOrNull()?.id
        val s = _state.value
        draft.statementBalance?.takeIf { !it.isNegative }?.let { s.balance.setTextAndPlaceCursorAtEnd(it.toInputText()) }
        draft.minimumPayment?.takeIf { !it.isNegative }?.let { s.minimum.setTextAndPlaceCursorAtEnd(it.toInputText()) }
        _state.update {
            it.copy(
                isLoading = false,
                draft = draft,
                cards = cardList,
                cardId = cardId,
                dueDate = draft.dueDate,
                statementDate = draft.statementDate,
            )
        }
        refreshTransactions()
    }

    /** Seçili kartın kayıtlarıyla karşılaştırır; kart değişince yeniden. */
    private suspend fun refreshTransactions() {
        val draft = _state.value.draft ?: return
        val cardId = _state.value.cardId
        val recorded = cardId?.let { expenses.forCard(it) }.orEmpty()
        val rows = classifyTransactions(draft.transactions, recorded) { categorizer.categorize(it).category }
        _state.update { it.copy(transactions = rows) }
    }

    private fun save() {
        val current = _state.value
        val draft = current.draft ?: return
        if (current.isLoading || current.isSaving) return
        val card = current.selectedCard
        if (card == null) {
            _state.update { it.copy(cardError = true) }
            return
        }
        val currency = card.currency

        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                // Banka kartında ekstre yok: yalnız hareketler (eksikler) işlenir.
                if (card.isCredit) statements.add(
                    CardStatement(
                        cardId = card.id,
                        readOn = current.today,
                        statementDate = current.statementDate,
                        dueDate = current.dueDate,
                        nextStatementDate = draft.nextStatementDate,
                        statementBalance = parseOptional(current.balance, currency),
                        minimumPayment = parseOptional(current.minimum, currency),
                        periodSpending = draft.periodSpending,
                        futureInstallments = draft.futureInstallments,
                        totalDebt = draft.totalDebt,
                        creditLimit = draft.creditLimit,
                        availableLimit = draft.availableLimit,
                    ),
                )
                // Kart her kayıtta yazılır: koç planı kart değişikliğiyle yeniden kurulur (son ödeme hatırlatması).
                cards.update(
                    card.copy(
                        last4 = current.last4ToSave ?: card.last4,
                        statementDay = if (card.isCredit) card.statementDay ?: current.statementDate?.day else null,
                    ),
                )
                val toAdd = current.transactions.filter { it.selected }
                toAdd.forEach { row -> expenses.add(expenseFor(row, card)) }
                _effect.send(StatementReviewEffect.Saved(addedExpenses = toAdd.size))
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }

    private suspend fun expenseFor(row: TransactionRow, card: Card): Expense {
        val tx = row.transaction
        val tokens = withContext(ioDispatcher) { entryParser.parse(tx.description, tx.date, cards = listOf(card)).tokens }
        return Expense(
            date = tx.date,
            grossAmount = tx.amount,
            category = row.category,
            cardId = card.id,
            note = STATEMENT_NOTE,
            tokens = tokens,
            rawText = tx.description,
        )
    }

    private fun parseOptional(field: TextFieldState, currency: Currency) =
        field.text.toString().trim().takeIf { it.isNotEmpty() }?.let { MoneyInput.parse(it, currency) }

    private companion object {
        val SELECTABLE = setOf(TransactionStatus.MISSING, TransactionStatus.INSTALLMENT)

        /** Notta durur; geçmişte "ekstre" diye aranınca bankadan eklenenler bulunur. */
        const val STATEMENT_NOTE = "Ekstreden okundu"
    }
}

/**
 * Bankadaki hareketleri kayıtlarla eşler: aynı tutar (±1 kuruş) ve en fazla 3 gün fark. Her kayıt bir kez
 * eşlenir. Eşleşmeyen harcamalar eksik (seçili gelir), taksitler seçilmeden gelir, eksi tutarlar ödemedir.
 */
internal fun classifyTransactions(
    transactions: List<StatementTransaction>,
    recorded: List<Expense>,
    categoryOf: (String) -> Category,
): List<TransactionRow> {
    val pool = recorded.toMutableList()
    return transactions.map { tx ->
        val status = when {
            tx.isPaymentOrRefund -> TransactionStatus.PAYMENT
            else -> {
                val match = pool.firstOrNull { e ->
                    e.grossAmount.currency == tx.amount.currency &&
                        abs(e.grossAmount.minor - tx.amount.minor) <= 1 &&
                        abs(e.date.daysUntil(tx.date)) <= MATCH_DAYS
                }
                when {
                    match != null -> {
                        pool.remove(match)
                        TransactionStatus.MATCHED
                    }
                    tx.installment != null -> TransactionStatus.INSTALLMENT
                    else -> TransactionStatus.MISSING
                }
            }
        }
        TransactionRow(
            transaction = tx,
            status = status,
            selected = status == TransactionStatus.MISSING,
            category = categoryOf(tx.description),
        )
    }
}

private const val MATCH_DAYS = 3
