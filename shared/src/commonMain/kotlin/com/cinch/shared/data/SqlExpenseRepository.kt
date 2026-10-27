package com.cinch.shared.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.cinch.shared.db.CinchDatabase
import com.cinch.shared.domain.Expense
import com.cinch.shared.domain.repository.ExpenseRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

class SqlExpenseRepository(
    private val db: CinchDatabase,
    private val ioDispatcher: CoroutineDispatcher,
) : ExpenseRepository {

    private val queries get() = db.cinchQueries

    override fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<Expense>> =
        queries.expensesBetween(from.toEpochDays(), to.toEpochDays())
            .asFlow()
            .mapToList(ioDispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun between(from: LocalDate, to: LocalDate): List<Expense> =
        withContext(ioDispatcher) {
            queries.expensesBetween(from.toEpochDays(), to.toEpochDays())
                .executeAsList()
                .map { it.toDomain() }
        }

    override suspend fun forCard(cardId: Long): List<Expense> =
        withContext(ioDispatcher) {
            queries.expensesForCard(cardId).executeAsList().map { it.toDomain() }
        }

    override fun observeForCardBetween(cardId: Long, from: LocalDate, to: LocalDate): Flow<List<Expense>> =
        queries.expensesForCardBetween(cardId, from.toEpochDays(), to.toEpochDays())
            .asFlow()
            .mapToList(ioDispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun add(expense: Expense): Long =
        withContext(ioDispatcher) {
            queries.transactionWithResult {
                queries.insertExpense(
                    date_epoch_day = expense.date.toEpochDays(),
                    gross_minor = expense.grossAmount.minor,
                    chargeable_fraction = expense.chargeableFraction,
                    currency = expense.grossAmount.currency.code,
                    category = expense.category.name,
                    card_id = expense.cardId,
                    note = expense.note,
                    raw_text = expense.rawText,
                    tokens = encodeTokens(expense.tokens),
                )
                queries.lastInsertedRowId().executeAsOne()
            }
        }

    override suspend fun delete(id: Long) {
        withContext(ioDispatcher) { queries.deleteExpense(id) }
    }
}
