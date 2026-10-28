package com.cinch.ui.cards

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cinch.shared.domain.Card as CreditCard
import com.cinch.shared.domain.CardStatement
import com.cinch.shared.money.Money
import com.cinch.ui.common.AnimatedMoneyText
import com.cinch.ui.common.InfoTip
import com.cinch.ui.common.display
import com.cinch.ui.common.shortLabel
import com.cinch.ui.statement.withYear
import com.cinch.ui.theme.CinchTheme
import kotlinx.datetime.LocalDate
import kotlin.math.roundToLong

/**
 * Banka ekranından okunan son ekstre: son ödeme, kalan borç ve ödeme ilerlemesi, asgari ödemenin faizi, limit,
 * gelecek taksitler. Hiç okunmadıysa nasıl okutulacağını anlatır.
 *
 * @param onScan belge tarayıcı (banka uygulaması başka telefondaysa onun ekranı taranır).
 * @param onPick galeriden fotoğraf(lar): başka telefonla çekilip gönderilen ekran fotoğrafları.
 */
@Composable
internal fun StatementCard(
    card: CreditCard,
    state: CardDetailState,
    onScan: (() -> Unit)?,
    onPick: (() -> Unit)?,
) {
    val statement = state.statement
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!card.isCredit) {
                DebitEmpty()
            } else if (statement == null) {
                Empty()
            } else {
                Summary(card, statement, state)
            }
            if (onScan != null || onPick != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    onPick?.let { OutlinedButton(onClick = it) { Text("🖼️ Fotoğraf seç") } }
                    onScan?.let { TextButton(onClick = it) { Text(if (statement == null) "📷 Tara" else "📷 Yeniden tara") } }
                }
            }
        }
    }
}

/** Banka kartında ekstre yok; hareket ekranından eksik harcamalar bulunur. */
@Composable
private fun DebitEmpty() {
    Text("📸 Hesap hareketlerinden oku", style = MaterialTheme.typography.titleMedium)
    Text(
        "Banka uygulamasındaki kart ya da hesap hareketlerinin fotoğrafını seç; burada olmayan harcamaları bulup " +
            "tek dokunuşla ekleyelim.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Empty() {
    Row {
        Text("📸 Bankadan oku", style = MaterialTheme.typography.titleMedium)
        InfoTip(
            title = "Banka ekranından okuma",
            text = "Banka uygulamaları ekran görüntüsü almaya izin vermez. Kartın ekranını (son ödeme tarihi ve ekstre " +
                "borcunun yazdığı) başka bir telefonla çek ve buraya gönder; birden çok fotoğraf seçebilirsin. Yazılar " +
                "telefonunda okunur, fotoğraflar saklanmaz.",
        )
    }
    Text(
        "Banka uygulamasındaki kart ekranının fotoğrafını seç: son ödeme tarihini ve ekstre borcunu okuyup günü gelmeden " +
            "hatırlatalım, eksik girdiğin harcamaları bulalım.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Summary(card: CreditCard, statement: CardStatement, state: CardDetailState) {
    val colors = CinchTheme.colors
    val days = statement.daysUntilDue(state.today)
    val dueText = statement.dueDate?.let { due ->
        val left = when {
            days == null -> ""
            days < 0 -> " · günü geçti"
            days == 0 -> " · bugün!"
            days == 1 -> " · yarın"
            else -> " · $days gün kaldı"
        }
        "Son ödeme ${due.withYear()}$left"
    } ?: "Son ödeme tarihi okunamadı"
    Text(
        dueText,
        style = MaterialTheme.typography.labelLarge,
        color = if (days != null && days <= 2 && !statement.isPaidOff) colors.caution else MaterialTheme.colorScheme.onSurface,
    )

    val balance = statement.statementBalance
    if (balance != null && statement.isPaidOff) {
        Text("Ekstre ödendi 🎉", style = MaterialTheme.typography.headlineSmall, color = colors.good)
    } else if (balance != null) {
        Text("Kalan ekstre borcu", style = MaterialTheme.typography.bodySmall)
        AnimatedMoneyText(balance, style = MaterialTheme.typography.displaySmall)
    }

    // "52.331 → 31.393": aynı ekstrenin ilk okumasından bu yana ödenen.
    val start = state.statementStartBalance
    val paid = state.statementPaid
    if (start != null && paid != null && !start.isZero) {
        val fraction = (paid.minor.toFloat() / start.minor.toFloat()).coerceIn(0f, 1f)
        ProgressBar(fraction, colors.good)
        Text(
            "${start.display()} borcun ${paid.display()} kadarını ödedin (%${(fraction * 100).toInt()})",
            style = MaterialTheme.typography.bodySmall,
        )
    }

    statement.minimumPayment?.let { minimum ->
        Text("Asgari ödeme ${minimum.display()}", style = MaterialTheme.typography.bodyMedium)
        if (balance != null && balance > minimum) {
            val rest = balance - minimum
            val interest = Money((rest.minor * card.monthlyRatePercent / 100.0).roundToLong(), rest.currency)
            Text(
                "Yalnız asgariyi ödersen kalan ${rest.display()} için ayda ~${interest.display()} faiz işler.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.caution,
            )
        }
    }

    LimitLine(statement)

    statement.futureInstallments?.takeIf { !it.isZero }?.let {
        Text("Gelecek dönemlere yansıyacak taksitler: ${it.display()}", style = MaterialTheme.typography.bodySmall)
    }
    RecordedShare(statement, state)
    if (state.statementHistory.size >= 2) StatementHistory(state.statementHistory)
    Spacer(Modifier.height(2.dp))
    Text(
        "${statement.readOn.shortLabel()} tarihinde okundu",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** "Kart harcamalarının %92'sini girmişsin": bankanın dönem içi tutarı ile Cinch'teki kayıtlar. */
@Composable
private fun RecordedShare(statement: CardStatement, state: CardDetailState) {
    val bank = statement.periodSpending ?: return
    val share = state.recordedShare
    if (share == null) {
        Text("Bankaya göre dönem içi harcama ${bank.display()}", style = MaterialTheme.typography.bodySmall)
        return
    }
    val percent = (share * 100).toInt()
    val missing = state.periodTotal?.let { bank - it }?.takeIf { !it.isNegative && !it.isZero }
    Text(
        when {
            percent >= 100 -> "Bu dönemki kart harcamalarının hepsini girmişsin 👏"
            percent >= 90 -> "Kart harcamalarının %$percent'ini girmişsin 👏"
            else -> "Kart harcamalarının %$percent'ini girmişsin; bankaya göre ${missing?.display() ?: ""} eksik. " +
                "Hareketleri okutursan eksikleri bulalım."
        },
        style = MaterialTheme.typography.bodySmall,
        color = if (percent >= 90) CinchTheme.colors.good else CinchTheme.colors.caution,
    )
}

/** Son ekstrelerin küçük çubuk grafiği ve geçen aya göre değişim. */
@Composable
private fun StatementHistory(history: List<StatementPoint>) {
    val max = history.maxOf { it.amount.minor }.coerceAtLeast(1)
    val last = history.last().amount
    val previous = history[history.size - 2].amount
    Spacer(Modifier.height(6.dp))
    Text("Son ekstreler", style = MaterialTheme.typography.titleSmall)
    Row(
        modifier = Modifier.fillMaxWidth().height(96.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        history.forEachIndexed { index, point ->
            val fraction by animateFloatAsState((point.amount.minor.toFloat() / max).coerceIn(0.04f, 1f), label = "history")
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(fraction * 0.8f)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(
                            if (index == history.lastIndex) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                )
                Text(point.dueDate.monthShort(), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    if (previous.currency == last.currency && !previous.isZero) {
        val change = ((last.minor - previous.minor) * 100.0 / previous.minor).roundToLong()
        Text(
            when {
                change > 0 -> "Bu ekstre öncekinden %$change fazla (${previous.display()} → ${last.display()})"
                change < 0 -> "Bu ekstre öncekinden %${-change} az 👏 (${previous.display()} → ${last.display()})"
                else -> "Bu ekstre öncekiyle aynı"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (change > 0) CinchTheme.colors.caution else CinchTheme.colors.good,
        )
    }
}

/** Ekstrenin son ödeme ayı: "Eki". */
private fun LocalDate.monthShort(): String = shortLabel().substringAfter(' ')

/** Limit kullanımı; kullanılabilir limit eksiyse aşım. */
@Composable
private fun LimitLine(statement: CardStatement) {
    val limit = statement.creditLimit
    val available = statement.availableLimit ?: return
    if (available.isNegative) {
        Text(
            "Limitini ${available.absolute.display()} aştın",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.error,
        )
        return
    }
    if (limit == null || limit.isZero || limit.currency != available.currency) {
        Text("Kullanılabilir limit ${available.display()}", style = MaterialTheme.typography.bodySmall)
        return
    }
    val used = ((limit.minor - available.minor).toFloat() / limit.minor.toFloat()).coerceIn(0f, 1f)
    ProgressBar(used, if (used > 0.8f) CinchTheme.colors.caution else MaterialTheme.colorScheme.primary)
    Text(
        "Limit ${limit.display()} · kullanılabilir ${available.display()} (%${(used * 100).toInt()} dolu)",
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun ProgressBar(fraction: Float, color: Color) {
    val animated by animateFloatAsState(fraction, label = "statementBar")
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color),
        )
    }
}
