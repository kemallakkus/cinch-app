package com.cinch.shared.domain.repository

import com.cinch.shared.domain.Income
import kotlinx.coroutines.flow.Flow

/** Düzenli aylık gelirler. */
interface IncomeRepository {

    fun observeAll(): Flow<List<Income>>

    suspend fun all(): List<Income>

    suspend fun get(id: Long): Income?

    /** Geliri ekler ve yeni kaydın id'sini döner. [Income.id] yok sayılır. */
    suspend fun add(income: Income): Long

    /** Adı, tutarı ve günü günceller; para birimi değişmez. */
    suspend fun update(income: Income)

    suspend fun delete(id: Long)
}
