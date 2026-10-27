package com.cinch.shared.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.cinch.shared.db.CinchDatabase
import com.cinch.shared.domain.Income
import com.cinch.shared.domain.repository.IncomeRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SqlIncomeRepository(
    private val db: CinchDatabase,
    private val ioDispatcher: CoroutineDispatcher,
) : IncomeRepository {

    private val queries get() = db.cinchQueries

    override fun observeAll(): Flow<List<Income>> =
        queries.allIncomes()
            .asFlow()
            .mapToList(ioDispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun all(): List<Income> =
        withContext(ioDispatcher) { queries.allIncomes().executeAsList().map { it.toDomain() } }

    override suspend fun get(id: Long): Income? =
        withContext(ioDispatcher) { queries.incomeById(id).executeAsOneOrNull()?.toDomain() }

    override suspend fun add(income: Income): Long =
        withContext(ioDispatcher) {
            queries.transactionWithResult {
                queries.insertIncome(
                    name = income.name,
                    amount_minor = income.amount.minor,
                    currency = income.currency.code,
                    day_of_month = income.dayOfMonth?.toLong(),
                )
                queries.lastInsertedRowId().executeAsOne()
            }
        }

    override suspend fun update(income: Income) {
        withContext(ioDispatcher) {
            queries.updateIncome(
                name = income.name,
                amount_minor = income.amount.minor,
                day_of_month = income.dayOfMonth?.toLong(),
                id = income.id,
            )
        }
    }

    override suspend fun delete(id: Long) {
        withContext(ioDispatcher) { queries.deleteIncome(id) }
    }
}
