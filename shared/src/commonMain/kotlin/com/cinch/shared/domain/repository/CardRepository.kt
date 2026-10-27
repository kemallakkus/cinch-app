package com.cinch.shared.domain.repository

import com.cinch.shared.domain.Card
import kotlinx.coroutines.flow.Flow

/** Kredi kartları. */
interface CardRepository {

    fun observeAll(): Flow<List<Card>>

    suspend fun all(): List<Card>

    /** Kartı ekler ve yeni kaydın id'sini döner. [Card.id] yok sayılır. */
    suspend fun add(card: Card): Long
}
