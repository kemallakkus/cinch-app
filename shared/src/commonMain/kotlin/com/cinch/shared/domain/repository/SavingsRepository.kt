package com.cinch.shared.domain.repository

import com.cinch.shared.domain.SavingsDeposit
import com.cinch.shared.domain.SavingsGoal
import kotlinx.coroutines.flow.Flow

/** Kumbara hedefleri ve atılan paralar. */
interface SavingsRepository {

    fun observeGoals(): Flow<List<SavingsGoal>>

    /** Tüm hareketler, eskiden yeniye. */
    fun observeDeposits(): Flow<List<SavingsDeposit>>

    suspend fun addGoal(goal: SavingsGoal): Long

    /** Ad, simge ve hedef tutarı günceller. */
    suspend fun updateGoal(goal: SavingsGoal)

    /** Hedefi ve hareketlerini siler. */
    suspend fun deleteGoal(id: Long)

    suspend fun addDeposit(deposit: SavingsDeposit): Long

    suspend fun deleteDeposit(id: Long)
}
