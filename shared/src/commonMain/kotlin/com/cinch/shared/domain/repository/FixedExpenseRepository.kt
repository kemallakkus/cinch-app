package com.cinch.shared.domain.repository

import com.cinch.shared.domain.FixedExpense
import kotlinx.coroutines.flow.Flow

/** Sabit giderler (ödeme günü sırasıyla). Ödemeleri harcama kayıtlarıdır, bkz. [com.cinch.shared.domain.Expense.fixedExpenseId]. */
interface FixedExpenseRepository {

    fun observeAll(): Flow<List<FixedExpense>>

    suspend fun all(): List<FixedExpense>

    suspend fun get(id: Long): FixedExpense?

    /** Ekler ve yeni kaydın id'sini döner. [FixedExpense.id] yok sayılır. */
    suspend fun add(fixed: FixedExpense): Long

    /** Para birimi dışında her şeyi günceller. */
    suspend fun update(fixed: FixedExpense)

    /** Sabit gideri siler; geçmiş ödemeleri (harcama kayıtları) kalır. */
    suspend fun delete(id: Long)
}
