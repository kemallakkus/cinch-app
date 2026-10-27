package com.cinch.shared.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.cinch.shared.budget.BudgetConfig
import com.cinch.shared.db.CinchDatabase
import com.cinch.shared.domain.repository.BudgetRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.isoDayNumber

class SqlBudgetRepository(
    private val db: CinchDatabase,
    private val ioDispatcher: CoroutineDispatcher,
) : BudgetRepository {

    private val queries get() = db.cinchQueries

    override fun observe(): Flow<BudgetConfig?> =
        queries.currentBudget()
            .asFlow()
            .mapToOneOrNull(ioDispatcher)
            .map { it?.toDomain() }

    override suspend fun get(): BudgetConfig? =
        withContext(ioDispatcher) { queries.currentBudget().executeAsOneOrNull()?.toDomain() }

    override suspend fun save(config: BudgetConfig) {
        // Tabloda tek para birimi sütunu var; hedef sınırla aynı birimde olmalı.
        require(config.weeklyGoal == null || config.weeklyGoal.currency == config.currency) {
            "Haftalık hedef ile sınır aynı para biriminde olmalı"
        }
        withContext(ioDispatcher) {
            queries.upsertBudget(
                weekly_limit_minor = config.weeklyLimit.minor,
                currency = config.currency.code,
                week_start_iso = config.weekStart.isoDayNumber.toLong(),
                weekly_goal_minor = config.weeklyGoal?.minor,
            )
        }
    }
}
