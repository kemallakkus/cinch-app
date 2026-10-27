package com.cinch.shared.domain.repository

import com.cinch.shared.domain.Card
import kotlinx.coroutines.flow.Flow

/** Kredi kartları. */
interface CardRepository {

    fun observeAll(): Flow<List<Card>>

    suspend fun all(): List<Card>

    /** Tek kartı izler; silinirse null yayar. */
    fun observe(id: Long): Flow<Card?>

    suspend fun get(id: Long): Card?

    /** Kartı ekler ve yeni kaydın id'sini döner. [Card.id] yok sayılır. */
    suspend fun add(card: Card): Long

    /** [Card.id]'li kartın adını, borcunu, oranını ve ekstre gününü günceller. Para birimi değişmez. */
    suspend fun update(card: Card)

    /** Kartı siler; ona bağlı harcamalar kalır ve nakit sayılır. */
    suspend fun delete(id: Long)
}
