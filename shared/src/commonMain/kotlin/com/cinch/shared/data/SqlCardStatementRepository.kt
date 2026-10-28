package com.cinch.shared.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.cinch.shared.db.CinchDatabase
import com.cinch.shared.domain.CardStatement
import com.cinch.shared.domain.repository.CardStatementRepository
import com.cinch.shared.money.Currency
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SqlCardStatementRepository(
    private val db: CinchDatabase,
    private val ioDispatcher: CoroutineDispatcher,
) : CardStatementRepository {

    private val queries get() = db.cinchQueries

    override fun observeForCard(cardId: Long): Flow<List<CardStatement>> =
        queries.statementsForCard(cardId)
            .asFlow()
            .mapToList(ioDispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    /** Okumalar azdır (kart başına ayda birkaç tane); en yeni okuma Kotlin'de seçilir. */
    override suspend fun latestPerCard(): List<CardStatement> =
        withContext(ioDispatcher) {
            queries.allStatements().executeAsList()
                .map { it.toDomain() }
                .distinctBy { it.cardId }
        }

    override fun observeLatestPerCard(): Flow<List<CardStatement>> =
        queries.allStatements()
            .asFlow()
            .mapToList(ioDispatcher)
            .map { rows -> rows.map { it.toDomain() }.distinctBy { it.cardId } }

    override suspend fun add(statement: CardStatement): Long =
        withContext(ioDispatcher) {
            // Tutarlar tek para biriminde; okunan ilk tutarın birimi, yoksa TL.
            val currency = listOfNotNull(
                statement.statementBalance, statement.minimumPayment, statement.periodSpending,
                statement.futureInstallments, statement.totalDebt, statement.creditLimit, statement.availableLimit,
            ).firstOrNull()?.currency ?: Currency.TRY
            queries.transactionWithResult {
                queries.insertStatement(
                    card_id = statement.cardId,
                    read_epoch_day = statement.readOn.toEpochDays(),
                    statement_epoch_day = statement.statementDate?.toEpochDays(),
                    due_epoch_day = statement.dueDate?.toEpochDays(),
                    next_statement_epoch_day = statement.nextStatementDate?.toEpochDays(),
                    currency = currency.code,
                    statement_balance_minor = statement.statementBalance?.minor,
                    minimum_payment_minor = statement.minimumPayment?.minor,
                    period_spending_minor = statement.periodSpending?.minor,
                    future_installments_minor = statement.futureInstallments?.minor,
                    total_debt_minor = statement.totalDebt?.minor,
                    credit_limit_minor = statement.creditLimit?.minor,
                    available_limit_minor = statement.availableLimit?.minor,
                )
                queries.lastInsertedRowId().executeAsOne()
            }
        }
}
