package com.cinch.shared.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.cinch.shared.db.CinchDatabase
import com.cinch.shared.domain.Card
import com.cinch.shared.domain.repository.CardRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SqlCardRepository(
    private val db: CinchDatabase,
    private val ioDispatcher: CoroutineDispatcher,
) : CardRepository {

    private val queries get() = db.cinchQueries

    override fun observeAll(): Flow<List<Card>> =
        queries.allCards()
            .asFlow()
            .mapToList(ioDispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun all(): List<Card> =
        withContext(ioDispatcher) { queries.allCards().executeAsList().map { it.toDomain() } }

    override suspend fun add(card: Card): Long =
        withContext(ioDispatcher) {
            queries.transactionWithResult {
                queries.insertCard(
                    name = card.name,
                    currency = card.currency.code,
                    carried_balance_minor = card.carriedBalance.minor,
                    monthly_rate_percent = card.monthlyRatePercent,
                    statement_day = card.statementDay?.toLong(),
                )
                queries.lastInsertedRowId().executeAsOne()
            }
        }
}
