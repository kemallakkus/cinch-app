package com.cinch.shared.cards

import com.cinch.shared.domain.Card
import com.cinch.shared.domain.CardStatement
import com.cinch.shared.domain.Expense
import com.cinch.shared.domain.repository.CardRepository
import com.cinch.shared.domain.repository.CardStatementRepository
import com.cinch.shared.domain.repository.ExpenseRepository
import com.cinch.shared.money.Money
import com.cinch.shared.time.LocalClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/** Kartın durumu: kullanıcıya ne söylenecek. */
enum class CardStatus {
    /** Banka kartı: ekstre ve borç yok, yalnız bu ayın harcaması. */
    DEBIT,

    /** Kredi kartı, banka ekranı hiç okunmadı: ödenecek tutar bilinmiyor. */
    NO_READING,

    /** Son okuma eski (son ödeme günü çoktan geçti): yeni ekstre kesilmiştir. */
    STALE,

    /** Ödenecek borç var, son ödeme günü gelmedi. */
    DUE,

    /** Son ödeme günü geçti, okunan borç ödenmemiş görünüyor (ödendiyse "Ödedim" ya da yeniden okuma). */
    OVERDUE,

    /** Ekstre ödendi. */
    PAID,
}

/**
 * Kartın üç sorusunun cevabı: ne kadar, ne zaman ödenecek; bu dönem karta ne harcandı; eksik yazılan var mı.
 * Kart listesi de detay ekranı da bunu gösterir.
 *
 * @param periodSpent bu ekstre döneminde (banka kartında bu ay) Cinch'e bu kartla yazılanlar, brüt.
 * @param statement son okunan ekstre; hiç okunmadıysa null.
 * @param toPay kalan ekstre borcu; yalnız [CardStatus.DUE] ve [CardStatus.OVERDUE]'da.
 * @param daysLeft son ödemeye kalan gün (0 = bugün, eksi = geçti).
 * @param bankPeriodSpending okuma bu döneme aitse bankanın gösterdiği dönem içi harcama.
 * @param missing bankaya göre Cinch'e yazılmamış görünen tutar (1 ₺'den fazlaysa).
 */
data class CardOverview(
    val card: Card,
    val status: CardStatus,
    val period: ClosedRange<LocalDate>,
    val periodSpent: Money,
    val statement: CardStatement?,
    val toPay: Money?,
    val dueDate: LocalDate?,
    val daysLeft: Int?,
    val bankPeriodSpending: Money?,
    val missing: Money?,
)

object CardOverviews {

    /** Son ödeme gününden bu kadar gün sonra okuma eskimiş sayılır: yeni ekstre kesilmiş, yeniden okunmalı. */
    const val STALE_AFTER_DUE_DAYS = 10

    /** Son ödeme tarihi okunamamış okumanın geçerli sayıldığı süre. */
    const val UNDATED_READING_DAYS = 35

    private const val MISSING_TOLERANCE_MINOR = 100L

    /**
     * @param periodExpenses bu kartın [Card.currentPeriod] içindeki harcamaları.
     * @param readings kartın okumaları, en yeni önce (yalnız ilki kullanılır).
     */
    fun of(card: Card, periodExpenses: List<Expense>, readings: List<CardStatement>, today: LocalDate): CardOverview {
        val period = card.currentPeriod(today)
        val spent = Money.sum(
            periodExpenses.filter { it.cardId == card.id && it.date in period && it.grossAmount.currency == card.currency }
                .map { it.grossAmount },
            card.currency,
        )
        val latest = readings.firstOrNull { it.cardId == card.id }
        val bank = latest?.periodSpending
            ?.takeIf { latest.readOn >= period.start && it.currency == card.currency && !it.isNegative }
        val missing = bank?.let { it - spent }?.takeIf { it.minor > MISSING_TOLERANCE_MINOR }

        val due = latest?.dueDate
        val daysLeft = due?.let { today.daysUntil(it) }
        val status = when {
            !card.isCredit -> CardStatus.DEBIT
            latest == null -> CardStatus.NO_READING
            daysLeft == null ->
                if (latest.readOn.daysUntil(today) > UNDATED_READING_DAYS) CardStatus.STALE
                else if (latest.isPaidOff) CardStatus.PAID else CardStatus.DUE
            daysLeft < -STALE_AFTER_DUE_DAYS -> CardStatus.STALE
            latest.isPaidOff -> CardStatus.PAID
            daysLeft < 0 -> CardStatus.OVERDUE
            else -> CardStatus.DUE
        }
        val toPay = latest?.statementBalance
            ?.takeIf { status == CardStatus.DUE || status == CardStatus.OVERDUE }
            ?.takeIf { !it.isZero && !it.isNegative }
        return CardOverview(
            card = card,
            status = status,
            period = period,
            periodSpent = spent,
            statement = latest,
            toPay = toPay,
            dueDate = due?.takeIf { card.isCredit },
            daysLeft = daysLeft?.takeIf { card.isCredit },
            bankPeriodSpending = bank,
            missing = missing,
        )
    }

    /**
     * "Ödedim": kullanıcı ekstreyi bankadan ödedi ama ekranı yeniden okutmadı. Son okumanın sıfır borçlu bir kopyası;
     * geçmiş ve ödeme ilerlemesi bozulmaz (ilk okunan borç kalır), hatırlatmalar susar.
     */
    fun paidReading(latest: CardStatement, today: LocalDate): CardStatement =
        latest.copy(
            id = 0,
            readOn = today,
            statementBalance = latest.statementBalance?.let { Money.zero(it.currency) },
            minimumPayment = null,
        )
}

/** Bütün kartların özeti; kart, harcama ya da okuma değişince yeniden hesaplanır. */
@OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest
class ObserveCardOverviews(
    private val cards: CardRepository,
    private val expenses: ExpenseRepository,
    private val statements: CardStatementRepository,
    private val clock: LocalClock,
) {
    operator fun invoke(): Flow<List<CardOverview>> {
        val today = clock.today()
        return cards.observeAll().flatMapLatest { list ->
            if (list.isEmpty()) return@flatMapLatest flowOf(emptyList<CardOverview>())
            val from = list.minOf { it.currentPeriod(today).start }
            combine(expenses.observeBetween(from, today), statements.observeLatestPerCard()) { recent, latest ->
                list.map { card ->
                    CardOverviews.of(
                        card = card,
                        periodExpenses = recent.filter { it.cardId == card.id },
                        readings = latest.filter { it.cardId == card.id },
                        today = today,
                    )
                }
            }
        }
    }
}
