package com.cinch.ui.cards

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import com.cinch.shared.cards.CardOverview
import com.cinch.shared.cards.CardStatus
import com.cinch.shared.domain.Card
import com.cinch.shared.money.Money
import com.cinch.ui.common.shortLabel
import com.cinch.ui.theme.CinchTheme

/** Kartın rengi: id'sine göre sabit degrade (liste kutucuğu ve detaydaki özet aynı renkte). */
@Composable
internal fun cardBrush(card: Card): Brush {
    val gradients = CinchTheme.colors.cardGradients
    val index = (card.id % gradients.size).toInt().let { if (it < 0) it + gradients.size else it }
    return gradients[index]
}

/** "bugün", "yarın", "3 gün kaldı", "2 gün geçti". */
internal fun dueCountdown(daysLeft: Int): String = when {
    daysLeft == 0 -> "bugün"
    daysLeft == 1 -> "yarın"
    daysLeft > 1 -> "$daysLeft gün kaldı"
    else -> "${-daysLeft} gün geçti"
}

/** Kutucukta ve özette gösterilen ana rakam ve üstündeki etiket. */
internal data class CardHeadline(val label: String, val amount: Money)

internal val CardOverview.headline: CardHeadline
    get() {
        val due = toPay
        return when {
            due != null -> CardHeadline("Ödenecek", due)
            status == CardStatus.DEBIT -> CardHeadline("Bu ay harcanan", periodSpent)
            else -> CardHeadline("Bu dönem harcanan", periodSpent)
        }
    }

/** Kutucuğun sağ altındaki kısa durum: son ödeme, ödendi, okutma çağrısı. Banka kartında yok. */
internal val CardOverview.statusPill: String?
    get() {
        val due = dueDate
        val days = daysLeft
        return when (status) {
            CardStatus.DEBIT -> null
            CardStatus.NO_READING -> "📸 Ekstreni okut"
            CardStatus.STALE -> "📸 Yeni ekstreyi okut"
            CardStatus.PAID -> "Ekstre ödendi ✓"
            CardStatus.OVERDUE -> "Son ödeme geçti"
            CardStatus.DUE ->
                if (due != null && days != null) "${due.shortLabel()} · ${dueCountdown(days)}" else "Son ödeme tarihi okunamadı"
        }
    }

/** Kullanıcının dikkat etmesi gereken durum: son ödeme 2 gün içinde ya da geçti. */
internal val CardOverview.needsAttention: Boolean
    get() {
        val days = daysLeft
        return status == CardStatus.OVERDUE || (status == CardStatus.DUE && days != null && days <= 2)
    }
