package com.cinch.shared.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.cinch.shared.db.CinchDatabase
import com.cinch.shared.domain.SavingsDeposit
import com.cinch.shared.domain.SavingsGoal
import com.cinch.shared.domain.repository.SavingsRepository
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

class SqlSavingsRepository(
    private val db: CinchDatabase,
    private val ioDispatcher: CoroutineDispatcher,
) : SavingsRepository {

    private val queries get() = db.cinchQueries

    override fun observeGoals(): Flow<List<SavingsGoal>> =
        queries.allGoals().asFlow().mapToList(ioDispatcher).map { rows ->
            rows.map {
                SavingsGoal(
                    id = it.id,
                    name = it.name,
                    emoji = it.emoji,
                    target = Money(it.target_minor, Currency.fromCode(it.currency)),
                    createdOn = LocalDate.fromEpochDays(it.created_epoch_day),
                )
            }
        }

    override fun observeDeposits(): Flow<List<SavingsDeposit>> =
        queries.allDeposits().asFlow().mapToList(ioDispatcher).map { rows ->
            rows.map {
                SavingsDeposit(
                    id = it.id,
                    goalId = it.goal_id,
                    amount = Money(it.amount_minor, Currency.fromCode(it.currency)),
                    date = LocalDate.fromEpochDays(it.date_epoch_day),
                    note = it.note,
                )
            }
        }

    override suspend fun addGoal(goal: SavingsGoal): Long = withContext(ioDispatcher) {
        queries.transactionWithResult {
            queries.insertGoal(goal.name, goal.emoji, goal.target.minor, goal.target.currency.code, goal.createdOn.toEpochDays())
            queries.lastInsertedRowId().executeAsOne()
        }
    }

    override suspend fun updateGoal(goal: SavingsGoal) {
        withContext(ioDispatcher) { queries.updateGoal(goal.name, goal.emoji, goal.target.minor, goal.id) }
    }

    override suspend fun deleteGoal(id: Long) {
        withContext(ioDispatcher) {
            queries.transaction {
                queries.deleteDepositsForGoal(id)
                queries.deleteGoal(id)
            }
        }
    }

    override suspend fun addDeposit(deposit: SavingsDeposit): Long = withContext(ioDispatcher) {
        queries.transactionWithResult {
            queries.insertDeposit(
                deposit.goalId, deposit.amount.minor, deposit.amount.currency.code, deposit.date.toEpochDays(), deposit.note,
            )
            queries.lastInsertedRowId().executeAsOne()
        }
    }

    override suspend fun deleteDeposit(id: Long) {
        withContext(ioDispatcher) { queries.deleteDeposit(id) }
    }
}
