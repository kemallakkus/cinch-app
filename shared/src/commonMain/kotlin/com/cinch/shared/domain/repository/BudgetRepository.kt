package com.cinch.shared.domain.repository

import com.cinch.shared.budget.BudgetConfig
import kotlinx.coroutines.flow.Flow

/** Tek satırlık bütçe ayarı. Kullanıcı henüz bütçe kurmadıysa null. */
interface BudgetRepository {

    fun observe(): Flow<BudgetConfig?>

    suspend fun get(): BudgetConfig?

    suspend fun save(config: BudgetConfig)
}
