package com.cinch.shared.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.cinch.shared.db.CinchDatabase
import com.cinch.shared.domain.FixedExpense
import com.cinch.shared.domain.repository.FixedExpenseRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SqlFixedExpenseRepository(
    private val db: CinchDatabase,
    private val ioDispatcher: CoroutineDispatcher,
) : FixedExpenseRepository {

    private val queries get() = db.cinchQueries

    override fun observeAll(): Flow<List<FixedExpense>> =
        queries.allFixedExpenses()
            .asFlow()
            .mapToList(ioDispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun all(): List<FixedExpense> =
        withContext(ioDispatcher) { queries.allFixedExpenses().executeAsList().map { it.toDomain() } }

    override suspend fun get(id: Long): FixedExpense? =
        withContext(ioDispatcher) { queries.fixedExpenseById(id).executeAsOneOrNull()?.toDomain() }

    override suspend fun add(fixed: FixedExpense): Long =
        withContext(ioDispatcher) {
            queries.transactionWithResult {
                queries.insertFixedExpense(
                    name = fixed.name,
                    amount_minor = fixed.amount.minor,
                    currency = fixed.currency.code,
                    day_of_month = fixed.dayOfMonth.toLong(),
                    kind = fixed.kind.name,
                    card_id = fixed.cardId,
                )
                queries.lastInsertedRowId().executeAsOne()
            }
        }

    override suspend fun update(fixed: FixedExpense) {
        withContext(ioDispatcher) {
            queries.updateFixedExpense(
                name = fixed.name,
                amount_minor = fixed.amount.minor,
                day_of_month = fixed.dayOfMonth.toLong(),
                kind = fixed.kind.name,
                card_id = fixed.cardId,
                id = fixed.id,
            )
        }
    }

    override suspend fun delete(id: Long) {
        withContext(ioDispatcher) { queries.deleteFixedExpense(id) }
    }
}
