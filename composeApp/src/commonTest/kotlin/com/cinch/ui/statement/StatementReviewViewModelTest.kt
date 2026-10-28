package com.cinch.ui.statement

import androidx.lifecycle.viewModelScope
import com.cinch.navigation.StatementReviewRoute
import com.cinch.shared.category.Categorizer
import com.cinch.shared.category.InMemoryCorrectionStore
import com.cinch.shared.domain.Card
import com.cinch.shared.domain.Category
import com.cinch.shared.domain.Expense
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.shared.parser.QuickEntryParser
import com.cinch.shared.receipt.OcrCodec
import com.cinch.shared.receipt.OcrLine
import com.cinch.shared.receipt.OcrScan
import com.cinch.shared.statement.Installment
import com.cinch.shared.statement.StatementReader
import com.cinch.shared.statement.StatementTransaction
import com.cinch.ui.FakeCardRepository
import com.cinch.ui.FakeCardStatementRepository
import com.cinch.ui.FakeClock
import com.cinch.ui.FakeExpenseRepository
import com.cinch.ui.withTestMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StatementReviewViewModelTest {

    private val today = LocalDate(2026, 10, 25)
    private fun tl(minor: Long) = Money(minor, Currency.TRY)

    private fun l(text: String, x: Int, y: Int, h: Int = 20, w: Int = text.length * 11) =
        OcrLine(text, x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat())

    /** Akbank kart ekranından: son ödeme, kalan borç, iki hareket (biri taksit). */
    private val screen = OcrCodec.encode(
        OcrScan(
            listOf(
                listOf(
                    l("•••• 1243", 120, 160),
                    l("Ekstre kesim tarihi:", 120, 715), l("28.09.2026", 640, 715),
                    l("Son ödeme tarihi:", 120, 760), l("08.10.2026", 640, 760),
                    l("Son gün: 8 Ekim", 225, 935), l("16.378,39 TL", 225, 975, h = 24),
                    l("TRENDYOL.COM (04/06)", 80, 1300), l("1.455,00 TL", 680, 1310), l("24.10.2026 - Taksit: 04/06", 80, 1332, h = 18),
                    l("MIGROS SANAL MARKET", 80, 1400), l("612,40 TL", 680, 1410), l("20.10.2026", 80, 1432, h = 18),
                    l("STARBUCKS", 80, 1500), l("95,00 TL", 680, 1510), l("21.10.2026", 80, 1532, h = 18),
                ),
            ),
        ),
    )

    private class Fixture(
        val vm: StatementReviewViewModel,
        val cards: FakeCardRepository,
        val statements: FakeCardStatementRepository,
        val expenses: FakeExpenseRepository,
    ) {
        val state get() = vm.state.value
    }

    private fun testVm(
        cardList: List<Card>,
        route: StatementReviewRoute = StatementReviewRoute(cardId = null, ocr = screen),
        recorded: List<Expense> = emptyList(),
        block: suspend TestScope.(Fixture) -> Unit,
    ) = withTestMain {
        runTest {
            val expenses = FakeExpenseRepository()
            val cards = FakeCardRepository(expenses)
            cardList.forEach { cards.add(it) }
            recorded.forEach { expenses.add(it) }
            val statements = FakeCardStatementRepository()
            val categorizer = Categorizer(corrections = InMemoryCorrectionStore())
            val vm = StatementReviewViewModel(
                route = route,
                reader = StatementReader(),
                cards = cards,
                statements = statements,
                expenses = expenses,
                categorizer = categorizer,
                entryParser = QuickEntryParser(categorizer),
                clock = FakeClock(today),
                ioDispatcher = StandardTestDispatcher(testScheduler),
            )
            runCurrent()
            block(Fixture(vm, cards, statements, expenses))
            vm.viewModelScope.cancel()
        }
    }

    private val qnb = Card(name = "QNB", carriedBalance = Money.of(0, Currency.TRY))
    private val axess = Card(name = "Axess", carriedBalance = Money.of(0, Currency.TRY), last4 = "1243")

    @Test
    fun cardFoundByLast4_fieldsPrefilled_transactionsCompared() = testVm(
        cardList = listOf(qnb, axess),
        // MIGROS zaten girilmiş (iki gün farkla); STARBUCKS eksik; TRENDYOL taksit.
        recorded = listOf(Expense(date = LocalDate(2026, 10, 22), grossAmount = tl(61_240), cardId = 2)),
    ) { f ->
        assertEquals(2L, f.state.cardId)
        assertEquals("16.378,39", f.state.balance.text.toString())
        assertEquals(LocalDate(2026, 10, 8), f.state.dueDate)
        assertEquals(LocalDate(2026, 9, 28), f.state.statementDate)

        val byName = f.state.transactions.associateBy { it.transaction.description }
        assertEquals(TransactionStatus.INSTALLMENT, byName.getValue("TRENDYOL.COM").status)
        assertFalse(byName.getValue("TRENDYOL.COM").selected) // taksit kendiliğinden eklenmez
        assertEquals(TransactionStatus.MATCHED, byName.getValue("MIGROS SANAL MARKET").status)
        assertEquals(TransactionStatus.MISSING, byName.getValue("STARBUCKS").status)
        assertTrue(byName.getValue("STARBUCKS").selected)
        assertEquals(1, f.state.selectedCount)
    }

    @Test
    fun save_storesStatement_updatesCard_addsSelectedMissing() = testVm(cardList = listOf(qnb)) { f ->
        // Tek kart var ama son 4 hanesi yok: o seçilir, hane karta yazılacak.
        assertEquals(1L, f.state.cardId)
        assertEquals("1243", f.state.last4ToSave)

        f.vm.onEvent(StatementReviewEvent.Save)

        assertEquals(StatementReviewEffect.Saved(addedExpenses = 2), f.vm.effect.first()) // MIGROS + STARBUCKS
        val statement = f.statements.all.value.single()
        assertEquals(1L, statement.cardId)
        assertEquals(today, statement.readOn)
        assertEquals(LocalDate(2026, 10, 8), statement.dueDate)
        assertEquals(tl(1_637_839), statement.statementBalance)

        val card = f.cards.get(1)!!
        assertEquals("1243", card.last4)
        assertEquals(28, card.statementDay) // boştu, okunan kesim gününden

        val added = f.expenses.all.value
        assertEquals(setOf("MIGROS SANAL MARKET", "STARBUCKS"), added.map { it.rawText }.toSet())
        assertTrue(added.all { it.cardId == 1L && it.note == "Ekstreden okundu" })
        assertEquals(Category.MARKET, added.single { it.rawText.startsWith("MIGROS") }.category)
    }

    @Test
    fun toggling_installmentAddsIt_unselectingMissingSkipsIt() = testVm(cardList = listOf(axess)) { f ->
        val trendyol = f.state.transactions.indexOfFirst { it.transaction.description == "TRENDYOL.COM" }
        val starbucks = f.state.transactions.indexOfFirst { it.transaction.description == "STARBUCKS" }
        f.vm.onEvent(StatementReviewEvent.TransactionToggled(trendyol))
        f.vm.onEvent(StatementReviewEvent.TransactionToggled(starbucks))
        f.vm.onEvent(StatementReviewEvent.Save)

        assertEquals(StatementReviewEffect.Saved(addedExpenses = 2), f.vm.effect.first()) // TRENDYOL + MIGROS
        assertEquals(setOf("TRENDYOL.COM", "MIGROS SANAL MARKET"), f.expenses.all.value.map { it.rawText }.toSet())
    }

    @Test
    fun noCard_cannotSave() = testVm(cardList = emptyList()) { f ->
        f.vm.onEvent(StatementReviewEvent.Save)
        runCurrent()
        assertTrue(f.state.cardError)
        assertTrue(f.statements.all.value.isEmpty())
    }

    @Test
    fun classify_paymentsAndEachRecordMatchedOnce() {
        val day = LocalDate(2026, 10, 20)
        val rows = classifyTransactions(
            transactions = listOf(
                StatementTransaction(day, "KAHVE", tl(9_500)),
                StatementTransaction(day, "KAHVE", tl(9_500)),
                StatementTransaction(day, "Ödemeniz için Teşekkürler", tl(-220_286)),
                StatementTransaction(day, "TRENDYOL.COM", tl(145_500), Installment(4, 6)),
            ),
            recorded = listOf(Expense(date = day, grossAmount = tl(9_500), cardId = 1)),
        ) { Category.DIGER }

        assertEquals(
            listOf(TransactionStatus.MATCHED, TransactionStatus.MISSING, TransactionStatus.PAYMENT, TransactionStatus.INSTALLMENT),
            rows.map { it.status },
        )
    }
}
