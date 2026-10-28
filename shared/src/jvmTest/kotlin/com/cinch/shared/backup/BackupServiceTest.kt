package com.cinch.shared.backup

import com.cinch.shared.budget.BudgetConfig
import com.cinch.shared.data.SqlBudgetRepository
import com.cinch.shared.data.SqlCardRepository
import com.cinch.shared.data.SqlCardStatementRepository
import com.cinch.shared.data.SqlCorrectionStore
import com.cinch.shared.data.SqlExpenseRepository
import com.cinch.shared.data.SqlFixedExpenseRepository
import com.cinch.shared.data.SqlIncomeRepository
import com.cinch.shared.data.SqlSavingsRepository
import com.cinch.shared.data.newDatabase
import com.cinch.shared.db.CinchDatabase
import com.cinch.shared.domain.Card
import com.cinch.shared.domain.CardStatement
import com.cinch.shared.domain.CardType
import com.cinch.shared.domain.Category
import com.cinch.shared.domain.Expense
import com.cinch.shared.domain.FixedExpense
import com.cinch.shared.domain.FixedExpenseKind
import com.cinch.shared.domain.FixedPlan
import com.cinch.shared.domain.Income
import com.cinch.shared.domain.SavingsDeposit
import com.cinch.shared.domain.SavingsGoal
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BackupServiceTest {

    private fun tl(units: Long) = Money.of(units, Currency.TRY)
    private val day = LocalDate(2026, 10, 20)

    /** Bir veritabanının her şeyi, karşılaştırmak için. */
    private suspend fun TestScope.snapshot(db: CinchDatabase): List<Any?> {
        val io = StandardTestDispatcher(testScheduler)
        return listOf(
            SqlBudgetRepository(db, io).get(),
            SqlCardRepository(db, io).all(),
            SqlExpenseRepository(db, io).observeAll().first(),
            SqlIncomeRepository(db, io).all(),
            SqlFixedExpenseRepository(db, io).all(),
            SqlCardStatementRepository(db, io).observeLatestPerCard().first(),
            SqlSavingsRepository(db, io).observeGoals().first(),
            SqlSavingsRepository(db, io).observeDeposits().first(),
        )
    }

    @Test
    fun exportThenRestore_replacesEverything_keepsLinks() = runTest {
        val io = StandardTestDispatcher(testScheduler)
        val source = newDatabase()
        SqlBudgetRepository(source, io).save(BudgetConfig(weeklyLimit = tl(700)))
        val cardId = SqlCardRepository(source, io).add(Card(name = "Akbank Kart", carriedBalance = tl(0), last4 = "1221", type = CardType.DEBIT))
        val qnb = SqlCardRepository(source, io).add(Card(name = "QNB", carriedBalance = tl(5_000), statementDay = 26))
        val rent = SqlFixedExpenseRepository(source, io).add(
            FixedExpense(name = "Ablama borç", amount = tl(5_000), dayOfMonth = 10, kind = FixedExpenseKind.LOAN, plan = FixedPlan.INSTALLMENTS, totalInstallments = 6, paidBefore = 4),
        )
        val expenses = SqlExpenseRepository(source, io)
        expenses.add(Expense(date = day, grossAmount = tl(195), category = Category.MARKET, cardId = cardId, rawText = "195 market", tokens = listOf("market")))
        expenses.add(Expense(date = day, grossAmount = tl(5_000), category = Category.DIGER, fixedExpenseId = rent))
        SqlIncomeRepository(source, io).add(Income(name = "Maaş", amount = tl(40_000), dayOfMonth = 15))
        SqlCardStatementRepository(source, io).add(
            CardStatement(cardId = qnb, readOn = day, dueDate = LocalDate(2026, 11, 6), statementBalance = tl(52_331), availableLimit = tl(-296)),
        )
        SqlCorrectionStore(source).learn("zipzip", Category.YEMEK)
        val savings = SqlSavingsRepository(source, io)
        val trip = savings.addGoal(SavingsGoal(name = "Tatil", emoji = "🏖️", target = tl(20_000), createdOn = day))
        savings.addDeposit(SavingsDeposit(goalId = trip, amount = tl(340), date = day, note = "Haftalık artan"))
        val file = BackupService(source, SqlCorrectionStore(source), io).export(LocalDateTime(2026, 10, 28, 12, 0))

        // Hedefte başka veri var: geri yükleme hepsini yedektekilerle değiştirir.
        val target = newDatabase()
        SqlExpenseRepository(target, io).add(Expense(date = day, grossAmount = tl(1)))
        val targetCorrections = SqlCorrectionStore(target)
        targetCorrections.categoryFor("zipzip") // önbellek dolsun

        val summary = BackupService(target, targetCorrections, io).restore(file)

        assertEquals(2, summary.expenses)
        assertEquals(2, summary.cards)
        assertEquals(snapshot(source), snapshot(target))
        assertEquals(Category.YEMEK, targetCorrections.categoryFor("zipzip")) // öğrenilenler de geldi
    }

    @Test
    fun peek_describesWithoutChangingAnything() = runTest {
        val io = StandardTestDispatcher(testScheduler)
        val db = newDatabase()
        SqlExpenseRepository(db, io).add(Expense(date = day, grossAmount = tl(10)))
        val service = BackupService(db, SqlCorrectionStore(db), io)
        val file = service.export(LocalDateTime(2026, 10, 28, 12, 0))

        val summary = service.peek(file)

        assertEquals(1, summary.expenses)
        assertTrue(summary.createdAt.startsWith("2026-10-28"))
    }

    @Test
    fun invalidFiles_areRejected_andNothingChanges() = runTest {
        val io = StandardTestDispatcher(testScheduler)
        val db = newDatabase()
        SqlExpenseRepository(db, io).add(Expense(date = day, grossAmount = tl(10)))
        val service = BackupService(db, SqlCorrectionStore(db), io)

        assertFailsWith<BackupException> { service.restore("{}") }
        assertFailsWith<BackupException> { service.restore("bu bir yedek değil") }
        assertFailsWith<BackupException> { service.peek("""{"format":"baska","createdAt":"x"}""") }
        val newer = assertFailsWith<BackupException> { service.peek("""{"format":"cinch-backup","version":99,"createdAt":"x"}""") }
        assertTrue("daha yeni" in newer.message.orEmpty())

        assertEquals(1, SqlExpenseRepository(db, io).observeAll().first().size)
    }
}
