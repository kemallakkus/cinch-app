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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cinch.shared.cards.CardOverview
import com.cinch.shared.cards.CardStatus
import com.cinch.shared.debt.CardPayoffResult
import com.cinch.shared.domain.Card as CreditCard
import com.cinch.shared.domain.CardStatement
import com.cinch.shared.money.Money
import com.cinch.shared.reconcile.ReconciliationKind
import com.cinch.shared.reconcile.ReconciliationResult
import com.cinch.ui.common.AnimatedMoneyText
import com.cinch.ui.common.InfoTip
import com.cinch.ui.common.dayOfMonthLabel
import com.cinch.ui.common.display
import com.cinch.ui.common.formatPercent
import com.cinch.ui.common.shortLabel
import com.cinch.ui.statement.withYear
import com.cinch.ui.theme.CinchTheme
import kotlinx.datetime.LocalDate
import kotlin.math.roundToLong

// ───────────────────────── 1. Ne kadar, ne zaman ─────────────────────────

/**
 * Kartın renginde özet. Kredi kartında: ödenecek tutar ve son ödemeye kalan gün, ödeme ilerlemesi, asgarinin
 * faizi, limit; "Ödedim" ve "Bankadan güncelle". Okuma yoksa ya da eskidiyse okutma çağrısı. Banka kartında bu ayın
 * harcaması ve hareketlerden eksikleri bulma.
 */
@Composable
internal fun CardHero(
    overview: CardOverview,
    state: CardDetailState,
    onEvent: (CardDetailEvent) -> Unit,
    onScan: (() -> Unit)?,
    onPick: (() -> Unit)?,
) {
    val card = overview.card
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(cardBrush(card))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            card.name + (card.last4?.let { "  •••• $it" } ?: ""),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.9f),
        )
        when (overview.status) {
            CardStatus.DEBIT -> DebitHero(overview)
            CardStatus.NO_READING -> ReadPrompt(
                title = "Ne kadar, ne zaman ödeyeceksin?",
                body = "Banka uygulamasındaki kart ekranının fotoğrafını seç: ödenecek tutarı ve son ödeme gününü okuyup " +
                    "günü gelmeden hatırlatalım. Uygulama ekran görüntüsüne izin vermiyorsa başka bir telefonla çek.",
            )
            CardStatus.STALE -> ReadPrompt(
                title = "Yeni ekstreyi okut",
                body = overview.dueDate?.let { "Son okuduğun ekstrenin son ödemesi ${it.withYear()} idi; yeni ekstre kesildi." }
                    ?: "Son okuma eski; yeni ekstre kesilmiş olmalı.",
            )
            CardStatus.PAID -> PaidHero(overview)
            CardStatus.DUE, CardStatus.OVERDUE -> DueHero(overview, state)
        }

        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val owes = overview.status == CardStatus.DUE || overview.status == CardStatus.OVERDUE
            if (owes) {
                Button(onClick = { onEvent(CardDetailEvent.MarkPaidClicked) }, colors = whiteButton()) { Text("Ödedim ✓") }
            }
            val readLabel = when {
                !card.isCredit -> "🖼️ Hareketleri oku"
                overview.statement == null -> "🖼️ Fotoğraf seç"
                else -> "Bankadan güncelle"
            }
            onPick?.let { pick ->
                if (owes) TextButton(onClick = pick, colors = whiteText()) { Text(readLabel) }
                else Button(onClick = pick, colors = whiteButton()) { Text(readLabel) }
            }
            onScan?.let { TextButton(onClick = it, colors = whiteText()) { Text("📷 Tara") } }
        }
        overview.statement?.let { reading ->
            Text(
                "${reading.readOn.shortLabel()} tarihinde okundu",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun DueHero(overview: CardOverview, state: CardDetailState) {
    val due = overview.dueDate
    val days = overview.daysLeft
    val overdue = overview.status == CardStatus.OVERDUE
    Text(
        when {
            due == null -> "Ödenecek · son ödeme tarihi okunamadı"
            overdue -> "Son ödeme ${due.withYear()} · ${dueCountdown(days ?: 0)}"
            else -> "Ödenecek · son ödeme ${due.withYear()}, ${dueCountdown(days ?: 0)}"
        },
        style = MaterialTheme.typography.labelLarge,
        color = Color.White,
    )
    val toPay = overview.toPay
    if (toPay != null) {
        AnimatedMoneyText(toPay, style = MaterialTheme.typography.displaySmall, color = Color.White)
    } else {
        Text("Tutar okunamadı", style = MaterialTheme.typography.headlineSmall, color = Color.White)
    }
    if (overdue) {
        HeroNote("Ödediysen \"Ödedim\"e dokun ya da ekranı yeniden okut. Ödemediysen gecikme faizi işler; aşağıdaki planla bak.")
    }

    // "52.331 → 31.393": aynı ekstrenin ilk okumasından bu yana ödenen.
    val start = state.statementStartBalance
    val paid = state.statementPaid
    if (start != null && paid != null && !start.isZero) {
        val fraction = (paid.minor.toFloat() / start.minor.toFloat()).coerceIn(0f, 1f)
        HeroBar(fraction)
        HeroNote("${start.display()} borcun ${paid.display()} kadarını ödedin (%${(fraction * 100).toInt()})")
    }

    val reading = overview.statement
    reading?.minimumPayment?.let { minimum ->
        val rest = toPay?.takeIf { it > minimum }?.let { it - minimum }
        if (rest == null) {
            HeroNote("Asgari ödeme ${minimum.display()}")
        } else {
            val interest = Money((rest.minor * overview.card.monthlyRatePercent / 100.0).roundToLong(), rest.currency)
            HeroNote("Asgari ${minimum.display()} · yalnız asgariyi ödersen kalan için ayda ~${interest.display()} faiz işler")
        }
    }
    reading?.let { HeroLimit(it) }
}

@Composable
private fun PaidHero(overview: CardOverview) {
    Text("Bu ekstre", style = MaterialTheme.typography.labelLarge, color = Color.White)
    Text("Ödendi 🎉", style = MaterialTheme.typography.displaySmall, color = Color.White)
    val next = overview.statement?.nextStatementDate
    val day = overview.card.statementDay
    HeroNote(
        when {
            next != null -> "Sonraki ekstre ${next.withYear()} kesilir; kesilince yeniden okut."
            day != null -> "Sonraki ekstre ${dayOfMonthLabel(day).lowercase()} kesilir; kesilince yeniden okut."
            else -> "Yeni ekstre kesilince yeniden okut."
        },
    )
}

@Composable
private fun DebitHero(overview: CardOverview) {
    Text("Bu ay bu kartla", style = MaterialTheme.typography.labelLarge, color = Color.White)
    AnimatedMoneyText(overview.periodSpent, style = MaterialTheme.typography.displaySmall, color = Color.White)
    HeroNote(
        "Banka uygulamasındaki hesap hareketlerinin fotoğrafını seç; burada olmayan harcamaları bulup tek dokunuşla ekleyelim.",
    )
}

@Composable
private fun ReadPrompt(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
    HeroNote(body)
}

@Composable
private fun HeroNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f))
}

/** Limit kullanımı; kullanılabilir limit eksiyse aşım. */
@Composable
private fun HeroLimit(statement: CardStatement) {
    val limit = statement.creditLimit
    val available = statement.availableLimit ?: return
    when {
        available.isNegative -> HeroNote("⚠️ Limitini ${available.absolute.display()} aştın")
        limit == null || limit.isZero || limit.currency != available.currency ->
            HeroNote("Kullanılabilir limit ${available.display()}")
        else -> {
            val used = ((limit.minor - available.minor).toFloat() / limit.minor.toFloat()).coerceIn(0f, 1f)
            HeroNote("Limit ${limit.display()} · kullanılabilir ${available.display()} (%${(used * 100).toInt()} dolu)")
        }
    }
}

@Composable
private fun HeroBar(fraction: Float) {
    val animated by animateFloatAsState(fraction, label = "paid")
    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.25f))) {
        Box(Modifier.fillMaxWidth(animated).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.White))
    }
}

@Composable
private fun whiteButton() = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF3B0A00))

@Composable
private fun whiteText() = ButtonDefaults.textButtonColors(contentColor = Color.White)

// ───────────────────────── Borç planı ─────────────────────────

/**
 * "Tamamını ödeyemeyecek misin?": kalan borca her ay aynı tutar ödenirse ne zaman biter, ne kadar faiz. Kapalıyken
 * tek satır; son ödeme geçip ödenmediyse açık gelir.
 */
@Composable
internal fun PayoffPlan(card: CreditCard, state: CardDetailState, onEvent: (CardDetailEvent) -> Unit) {
    val debt = state.payoffDebt ?: return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(onClick = { onEvent(CardDetailEvent.PayoffToggled) }, color = Color.Transparent) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (state.payoffExpanded) "Borç ne zaman biter?" else "Tamamını ödeyemeyecek misin?",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(if (state.payoffExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                }
            }
            if (state.payoffExpanded) {
                Text(
                    "Kalan ${debt.display()} borca her ay %${formatPercent(card.monthlyRatePercent)} faiz eklenir. " +
                        "Her ay ne kadar ödersen ne zaman biter, bak:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    state = state.monthlyPayment,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Her ay ödeyeceğin tutar") },
                    suffix = { Text("₺") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
                state.payoff?.let { PayoffResult(it, state.firstMonthInterest) }
            }
        }
    }
}

@Composable
private fun PayoffResult(result: CardPayoffResult, firstMonthInterest: Money?) {
    if (!result.closes) {
        Text("Bu ödemeyle borç hiç bitmez.", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
        firstMonthInterest?.let {
            Text(
                "Her ay ${it.display()} faiz ekleniyor; borcun azalması için bundan fazla ödemelisin.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        return
    }
    Text("Borç ${durationLabel(result.months)} biter", style = MaterialTheme.typography.titleSmall)
    Text(
        "Bu sürede ${result.totalInterest.display()} faiz ödersin (toplam ${result.totalPaid.display()}).",
        style = MaterialTheme.typography.bodySmall,
    )
}

/** 14 → "1 yıl 2 ayda", 3 → "3 ayda". */
private fun durationLabel(months: Int): String {
    val years = months / 12
    val rest = months % 12
    return when {
        years == 0 -> "$months ayda"
        rest == 0 -> "$years yılda"
        else -> "$years yıl $rest ayda"
    }
}

// ───────────────────────── 2. Sıradaki ekstre ─────────────────────────

/**
 * Son kesimden bu yana bu karta yazılanlar (banka kartında bu ay). Banka ekranı bu dönemde okunduysa tek bir sonuç:
 * tutuyor ya da "X eksik" (hareketleri okut ya da farkı ekle). Okutamayan için tutarı elle yazma, katlanmış.
 */
@Composable
internal fun PeriodBlock(
    overview: CardOverview,
    state: CardDetailState,
    onEvent: (CardDetailEvent) -> Unit,
    onPick: (() -> Unit)?,
) {
    val card = overview.card
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (card.isCredit) "Sıradaki ekstreye girecek" else "Bu ay bu kartla", style = MaterialTheme.typography.labelLarge)
            AnimatedMoneyText(overview.periodSpent, style = MaterialTheme.typography.headlineMedium)
            val range = "${overview.period.start.shortLabel()} – ${overview.period.endInclusive.shortLabel()}"
            // Başka modülün sınıfında akıllı dönüşüm olmaz: yerel değişkene alınır.
            val statementDay = card.statementDay
            Text(
                when {
                    !card.isCredit -> range
                    statementDay != null -> "$range · kesim ${dayOfMonthLabel(statementDay).lowercase()}"
                    else -> "$range · kesim günü bilinmiyor, ayın başından sayıyoruz"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val bank = overview.bankPeriodSpending
            val missing = overview.missing
            when {
                bank != null && missing != null -> {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Bankaya göre ${missing.display()} eksik görünüyor",
                        style = MaterialTheme.typography.titleSmall,
                        color = CinchTheme.colors.caution,
                    )
                    Text(
                        "Banka bu dönem ${bank.display()} diyor, sen ${overview.periodSpent.display()} yazmışsın. Hareketlerin " +
                            "fotoğrafını okutursan eksikleri tek tek bulalım; hatırlamıyorsan farkı tek harcama olarak ekle.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        onPick?.let { OutlinedButton(onClick = it) { Text("📸 Hareketleri okut") } }
                        TextButton(onClick = { onEvent(CardDetailEvent.AddStatementDifference) }) { Text("Farkı ekle") }
                    }
                }
                bank != null -> Text(
                    "Bankayla tutuyor 👏 (${bank.display()})",
                    style = MaterialTheme.typography.titleSmall,
                    color = CinchTheme.colors.good,
                )
            }

            if (card.isCredit) ManualCompare(state, onEvent)
        }
    }
}

/** Banka ekranını okutamayan için: bankadaki dönem içi tutarı elle yazıp karşılaştırma. */
@Composable
private fun ManualCompare(state: CardDetailState, onEvent: (CardDetailEvent) -> Unit) {
    TextButton(onClick = { onEvent(CardDetailEvent.ManualCompareToggled) }) {
        Text(if (state.manualCompareOpen) "Elle karşılaştırmayı kapat" else "Bankadaki tutarı elle yaz")
    }
    if (!state.manualCompareOpen) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            state = state.statedSpending,
            modifier = Modifier.weight(1f),
            label = { Text("Bankadaki dönem içi tutar") },
            suffix = { Text("₺") },
            isError = state.statedSpendingError,
            trailingIcon = {
                InfoTip(
                    title = "Dönem içi harcama",
                    text = "Banka uygulaması, hesap kesim gününden beri kartla yaptığın harcamaları toplar. O toplamı " +
                        "buraya yazdıklarınla karşılaştırırız; fark varsa bir harcamayı yazmayı unutmuş olabilirsin.",
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            onKeyboardAction = { onEvent(CardDetailEvent.Reconcile) },
            lineLimits = TextFieldLineLimits.SingleLine,
        )
        Button(onClick = { onEvent(CardDetailEvent.Reconcile) }, modifier = Modifier.height(56.dp)) { Text("Karşılaştır") }
    }
    if (state.statedSpendingError) Text("Geçerli bir tutar yaz (ör. 1.240,50)", color = MaterialTheme.colorScheme.error)
    state.reconciliation?.let { ReconcileResult(it, onEvent) }
}

@Composable
private fun ReconcileResult(result: ReconciliationResult, onEvent: (CardDetailEvent) -> Unit) {
    when (result.kind) {
        ReconciliationKind.MATCH -> Text("Tutuyor, her şeyi yazmışsın 👍", style = MaterialTheme.typography.titleSmall)
        ReconciliationKind.MISSING_EXPENSE -> {
            Text(
                "${result.difference.display()} tutarında harcama yazılmamış görünüyor.",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                "Sen ${result.recordedTotal.display()} yazmışsın, banka ${result.statedActual.display()} diyor.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = { onEvent(CardDetailEvent.AddMissingDifference) }) { Text("Farkı harcama olarak ekle") }
        }
        ReconciliationKind.EXTRA_RECORDED -> {
            Text(
                "Yazdıkların bankadakinden ${result.difference.absolute.display()} fazla.",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                "Bir harcama iki kez, yanlış tutarla ya da yanlış karta yazılmış olabilir. Bazen banka bir harcamayı " +
                    "birkaç gün sonra gösterir; o zaman beklemek yeter.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

// ───────────────────────── 3. Geçmiş ─────────────────────────

/** Son ekstrelerin küçük çubuk grafiği, öncekine göre değişim ve gelecek dönem taksitleri. */
@Composable
internal fun HistoryBlock(state: CardDetailState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Geçmiş", style = MaterialTheme.typography.labelLarge)
            if (state.statementHistory.size >= 2) StatementHistory(state.statementHistory)
            state.statement?.futureInstallments?.takeIf { !it.isZero }?.let {
                Text("Gelecek ekstrelere yansıyacak taksitler: ${it.display()}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun StatementHistory(history: List<StatementPoint>) {
    val max = history.maxOf { it.amount.minor }.coerceAtLeast(1)
    val last = history.last().amount
    val previous = history[history.size - 2].amount
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
                change > 0 -> "Son ekstre öncekinden %$change fazla (${previous.display()} → ${last.display()})"
                change < 0 -> "Son ekstre öncekinden %${-change} az 👏 (${previous.display()} → ${last.display()})"
                else -> "Son ekstre öncekiyle aynı"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (change > 0) CinchTheme.colors.caution else CinchTheme.colors.good,
        )
    }
}

/** Ekstrenin son ödeme ayı: "Eki". */
private fun LocalDate.monthShort(): String = shortLabel().substringAfter(' ')
