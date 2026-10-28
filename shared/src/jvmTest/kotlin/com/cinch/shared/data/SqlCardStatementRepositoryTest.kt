package com.cinch.shared.data

import com.cinch.shared.domain.Card
import com.cinch.shared.domain.CardStatement
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SqlCardStatementRepositoryTest {

    private fun tl(minor: Long) = Money(minor, Currency.TRY)

    @Test
    fun roundTrip_newestFirst_latestPerCard() = runTest {
        val db = newDatabase()
        val io = StandardTestDispatcher(testScheduler)
        val cards = SqlCardRepository(db, io)
        val repo = SqlCardStatementRepository(db, io)
        val qnb = cards.add(Card(name = "QNB", carriedBalance = tl(0), last4 = "9999"))
        val axess = cards.add(Card(name = "Axess", carriedBalance = tl(0)))

        val first = CardStatement(
            cardId = qnb, readOn = LocalDate(2026, 10, 2), statementDate = LocalDate(2026, 9, 26),
            dueDate = LocalDate(2026, 10, 6), nextStatementDate = LocalDate(2026, 10, 26),
            statementBalance = tl(5_233_100), minimumPayment = tl(1_570_000), periodSpending = tl(576_434),
            futureInstallments = tl(419_535), totalDebt = tl(6_231_069), creditLimit = tl(9_560_000),
            availableLimit = tl(3_331_331),
        )
        val firstId = repo.add(first)
        val secondId = repo.add(CardStatement(cardId = qnb, readOn = LocalDate(2026, 10, 4), dueDate = LocalDate(2026, 10, 6), statementBalance = tl(3_139_355)))
        repo.add(CardStatement(cardId = axess, readOn = LocalDate(2026, 10, 3), availableLimit = tl(-29_625)))

        val qnbReadings = repo.observeForCard(qnb).first()
        assertEquals(listOf(secondId, firstId), qnbReadings.map { it.id })
        assertEquals(first.copy(id = firstId), qnbReadings.last())
        assertEquals(setOf(secondId, firstId + 2), repo.latestPerCard().map { it.id }.toSet())
        assertEquals("9999", cards.get(qnb)?.last4)
    }

    @Test
    fun deletingCard_removesItsStatements() = runTest {
        val db = newDatabase()
        val io = StandardTestDispatcher(testScheduler)
        val cards = SqlCardRepository(db, io)
        val repo = SqlCardStatementRepository(db, io)
        val id = cards.add(Card(name = "QNB", carriedBalance = tl(0)))
        repo.add(CardStatement(cardId = id, readOn = LocalDate(2026, 10, 4), statementBalance = tl(100)))

        cards.delete(id)

        assertTrue(repo.latestPerCard().isEmpty())
    }
}
