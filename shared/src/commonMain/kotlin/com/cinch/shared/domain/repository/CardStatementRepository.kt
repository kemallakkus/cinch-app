package com.cinch.shared.domain.repository

import com.cinch.shared.domain.CardStatement
import kotlinx.coroutines.flow.Flow

/** Banka ekranından okunan ekstreler; her okuma ayrı kayıt. */
interface CardStatementRepository {

    /** Kartın okumaları, en yeni üstte. */
    fun observeForCard(cardId: Long): Flow<List<CardStatement>>

    /** Her kartın en son okuması (koç hatırlatmaları için). */
    suspend fun latestPerCard(): List<CardStatement>

    /** Ekler ve yeni kaydın id'sini döner. [CardStatement.id] yok sayılır. */
    suspend fun add(statement: CardStatement): Long
}
