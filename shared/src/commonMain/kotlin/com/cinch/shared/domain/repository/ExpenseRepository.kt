package com.cinch.shared.domain.repository

import com.cinch.shared.domain.Expense
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/** Harcama kayıtları. Tarih aralıkları her iki uç dahil. */
interface ExpenseRepository {

    /** Aralıktaki harcamaları izler; kayıt eklenip silindikçe yeni liste yayar. */
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<Expense>>

    suspend fun between(from: LocalDate, to: LocalDate): List<Expense>

    /** Tüm kayıtlar, eskiden yeniye (geçmişte arama için). */
    fun observeAll(): Flow<List<Expense>>

    suspend fun get(id: Long): Expense?

    suspend fun forCard(cardId: Long): List<Expense>

    /** Bir kartın aralıktaki harcamalarını izler (kart detayı, mutabakat). */
    fun observeForCardBetween(cardId: Long, from: LocalDate, to: LocalDate): Flow<List<Expense>>

    /** Tüm sabit gider ödemeleri (taksit sayısı ve kalan borç bunlardan hesaplanır). */
    fun observeFixedPayments(): Flow<List<Expense>>

    suspend fun fixedPayments(): List<Expense>

    /** Harcamayı ekler ve yeni kaydın id'sini döner. [Expense.id] yok sayılır. */
    suspend fun add(expense: Expense): Long

    /** Kaydı [Expense.id] ile günceller. Para birimi ve [Expense.fixedExpenseId] değişmez. */
    suspend fun update(expense: Expense)

    suspend fun delete(id: Long)
}
