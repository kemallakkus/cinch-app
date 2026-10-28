package com.cinch.ui.month

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cinch.shared.budget.DebtProgress
import com.cinch.shared.budget.FixedPaymentState
import com.cinch.shared.budget.FixedStatus
import com.cinch.shared.budget.MonthSummary
import com.cinch.shared.budget.Payday
import com.cinch.shared.domain.FixedPlan
import com.cinch.shared.domain.Income
import com.cinch.ui.common.AnimatedMoneyText
import com.cinch.ui.common.ConfettiBurst
import com.cinch.ui.common.HeroCard
import com.cinch.ui.common.InfoTip
import com.cinch.ui.common.dayOfMonthLabel
import com.cinch.ui.common.display
import com.cinch.ui.common.emoji
import com.cinch.ui.common.monthYearLabel
import com.cinch.ui.common.shortLabel
import com.cinch.ui.theme.CinchTheme
import com.cinch.ui.theme.colors
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthScreen(
    state: MonthState,
    onEvent: (MonthEvent) -> Unit,
    onAddIncome: () -> Unit,
    onOpenIncome: (Long) -> Unit,
    onAddFixed: () -> Unit,
    onOpenFixed: (Long) -> Unit,
    onOpenDebt: (Long) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    confettiTrigger: Int = 0,
) {
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = { TopAppBar(title = { Text(state.today.monthYearLabel()) }) },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            if (state.isLoading) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Scaffold
            }
            val summary = state.summary

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "summary") {
                    if (summary == null || !summary.hasIncome) EmptySummary(onAddIncome) else SummaryCard(summary)
                }

                item(key = "fixedHeader") {
                    SectionHeader(
                        title = "Sabit giderlerin",
                        info = "Kira, fatura, abonelik, kredi taksiti gibi her ay ödediklerin. Haftalık " +
                            "harcama payına sayılmaz; ay hesabına girer. Ödeyince yanındaki yuvarlağa dokun.",
                        action = "Ekle",
                        onAction = onAddFixed,
                    )
                }
                val fixed = summary?.fixed.orEmpty()
                if (fixed.isEmpty()) {
                    item(key = "fixedEmpty") {
                        Hint("Kira, faturalar, abonelikler gibi her ay ödediğin şeyleri ekle. Günü gelince hatırlatırız.")
                    }
                }
                items(fixed, key = { "fixed-${it.fixed.id}" }) { status ->
                    FixedRow(
                        status = status,
                        today = state.today,
                        // Borçlar (taksitli/esnek) ilerleme ve geçmiş için detaya, diğerleri forma açılır.
                        onOpen = { if (status.fixed.isDebt) onOpenDebt(status.fixed.id) else onOpenFixed(status.fixed.id) },
                        onCheck = { onEvent(MonthEvent.FixedCheckClicked(status)) },
                    )
                }
                val finished = summary?.finishedFixed.orEmpty()
                if (finished.isNotEmpty()) {
                    item(key = "finishedHeader") {
                        Text(
                            "🎉 Biten borçlar",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                        )
                    }
                    items(finished, key = { "finished-${it.fixed.id}" }) { status ->
                        FinishedRow(status, onOpen = { onOpenDebt(status.fixed.id) })
                    }
                }

                item(key = "incomesHeader") {
                    SectionHeader(title = "Gelirlerin", action = "Ekle", onAction = onAddIncome)
                }
                if (state.incomes.isEmpty()) {
                    item(key = "incomesEmpty") { Hint("Maaş, ek iş, kira geliri gibi her ay gelen paralarını ekle.") }
                }
                items(state.incomes, key = { "income-${it.id}" }) { income ->
                    IncomeRow(income, onClick = { onOpenIncome(income.id) })
                }
            }
        }
        ConfettiBurst(trigger = confettiTrigger, originY = 0.4f)
    }

    when (val dialog = state.dialog) {
        is MonthDialog.Pay -> PayDialog(dialog.status, state, onEvent)
        is MonthDialog.Undo -> AlertDialog(
            onDismissRequest = { onEvent(MonthEvent.DialogDismissed) },
            title = { Text("Ödeme geri alınsın mı?") },
            text = { Text("${dialog.status.fixed.name} bu ay ödenmemiş sayılır; ödeme kaydı silinir.") },
            confirmButton = { TextButton(onClick = { onEvent(MonthEvent.UndoConfirmed) }) { Text("Geri al") } },
            dismissButton = { TextButton(onClick = { onEvent(MonthEvent.DialogDismissed) }) { Text("Vazgeç") } },
        )
        null -> Unit
    }
}

@Composable
private fun SummaryCard(summary: MonthSummary) {
    val negative = summary.remaining.isNegative
    val colors = CinchTheme.colors
    val content = if (negative) colors.onHeroOver else colors.onHero
    HeroCard(brush = if (negative) colors.heroOver else colors.hero) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (negative) "Bu ay gelirini aştın" else "Bu ay elinde kalan",
                    style = MaterialTheme.typography.labelLarge,
                    color = content,
                )
                InfoTip(
                    title = "Bu ay kalan",
                    text = "Gelirlerinden sabit giderlerini ve bu ayki harcamalarını çıkarıyoruz. Henüz ödenmemiş " +
                        "sabit giderler de düşülür, ay sonunda gerçekten ne kalacağını görürsün.",
                )
            }
            AnimatedMoneyText(summary.remaining.absolute, style = MaterialTheme.typography.displayMedium, color = content)
            summary.nextPayday?.let { Text(paydayLabel(it), style = MaterialTheme.typography.bodyMedium, color = content) }

            Spacer(Modifier.height(16.dp))
            val used = (summary.fixedTotal + summary.spent).minor.toFloat()
            val ratio = if (summary.income.isZero) 1f else (used / summary.income.minor.toFloat()).coerceIn(0f, 1f)
            val animated by animateFloatAsState(ratio, label = "monthUsed")
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(content.copy(alpha = 0.12f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(animated)
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (negative) MaterialTheme.colorScheme.error else colors.good),
                )
            }
            Spacer(Modifier.height(6.dp))
            SummaryLine("💰 Gelir", summary.income.display(), content)
            SummaryLine(
                "📌 Sabit giderler" + if (summary.pendingFixed > 0) " (${summary.pendingFixed} bekliyor)" else "",
                "− ${summary.fixedTotal.display()}",
                content,
            )
            SummaryLine("🛒 Harcamalar", "− ${summary.spent.display()}", content)
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = color)
        Text(value, style = MaterialTheme.typography.titleSmall, color = color)
    }
}

private fun paydayLabel(payday: Payday): String = when (payday.daysAway) {
    0 -> "${payday.income.name} bugün yatıyor 🎉"
    1 -> "${payday.income.name} yarın yatıyor"
    else -> "${payday.income.name} yatmasına ${payday.daysAway} gün var"
}

@Composable
private fun EmptySummary(onAddIncome: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🐷", style = MaterialTheme.typography.displaySmall)
            Text("Ay sonunda ne kalacak?", style = MaterialTheme.typography.titleLarge)
            Text(
                "Aylık gelirini eklersen, sabit giderlerini ve harcamalarını gelirinden düşüp elinde ne " +
                    "kaldığını gösteririz.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onAddIncome) { Text("Gelir ekle") }
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String, onAction: () -> Unit, info: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (info != null) InfoTip(title = title, text = info)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun FixedRow(status: FixedStatus, today: LocalDate, onOpen: () -> Unit, onCheck: () -> Unit) {
    val colors = CinchTheme.colors
    val paid = status.state == FixedPaymentState.PAID
    val (stateText, stateColor) = when (status.state) {
        FixedPaymentState.PAID -> "Ödendi ✓" to colors.good
        FixedPaymentState.DUE_TODAY -> "Bugün" to colors.caution
        FixedPaymentState.OVERDUE -> "Günü geçti" to MaterialTheme.colorScheme.error
        FixedPaymentState.UPCOMING -> {
            val days = today.daysUntil(status.dueDate)
            (if (days == 1) "Yarın" else "$days gün sonra · ${status.dueDate.shortLabel()}") to
                MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable(onClick = onOpen)
            .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(status.fixed.kind.category.colors().container),
            contentAlignment = Alignment.Center,
        ) { Text(status.fixed.kind.emoji, style = MaterialTheme.typography.titleMedium) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(status.fixed.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (status.isFinished) "🎉 Bu ödemeyle bitti" else stateText,
                style = MaterialTheme.typography.bodySmall,
                color = if (status.isFinished) colors.good else stateColor,
            )
            status.progress?.let { DebtProgressLine(it) }
        }
        Text(status.amountForMonth.display(), style = MaterialTheme.typography.titleSmall)
        PaidCheck(paid = paid, onClick = onCheck)
    }
}

/** Ödendi işareti: boş halka ↔ dolu nane yeşili daire. */
@Composable
private fun PaidCheck(paid: Boolean, onClick: () -> Unit) {
    val good = CinchTheme.colors.good
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .then(
                    if (paid) Modifier.background(good)
                    else Modifier.border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (paid) Icon(Icons.Rounded.Check, contentDescription = "Ödendi", tint = MaterialTheme.colorScheme.surface)
        }
    }
}

@Composable
private fun PayDialog(status: FixedStatus, state: MonthState, onEvent: (MonthEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(MonthEvent.DialogDismissed) },
        title = {
            Text(
                if (status.fixed.plan == FixedPlan.FLEXIBLE) "${status.fixed.kind.emoji} ${status.fixed.name}: bu ay ne kadar ödedin?"
                else "${status.fixed.kind.emoji} ${status.fixed.name} ödendi mi?",
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val progress = status.progress
                Text(
                    when {
                        progress == null -> "Faturada tutar farklıysa düzelt."
                        status.fixed.plan == FixedPlan.INSTALLMENTS ->
                            "${(progress.paidCount ?: 0) + 1}/${progress.totalCount}. taksit. Tutar farklıysa düzelt."
                        else -> "Kalan borç ${progress.remainingDebt.display()}. Ne ödediysen onu yaz."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    state = state.payAmount,
                    label = { Text("Ödenen tutar") },
                    suffix = { Text("₺") },
                    isError = state.payAmountError,
                    supportingText = { if (state.payAmountError) Text("Sıfırdan büyük bir tutar yaz") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    onKeyboardAction = { onEvent(MonthEvent.PayConfirmed) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
                if (status.fixed.cardId != null) {
                    Text(
                        "Kartla ödendiği için kartın \"bankayla karşılaştır\" hesabına da yazılır.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { Button(onClick = { onEvent(MonthEvent.PayConfirmed) }) { Text("Ödendi") } },
        dismissButton = { TextButton(onClick = { onEvent(MonthEvent.DialogDismissed) }) { Text("Vazgeç") } },
    )
}

@Composable
private fun IncomeRow(income: Income, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center,
        ) { Text("💰", style = MaterialTheme.typography.titleMedium) }
        Column(Modifier.weight(1f)) {
            Text(income.name, style = MaterialTheme.typography.titleSmall)
            Text(
                income.dayOfMonth?.let { dayOfMonthLabel(it) } ?: "Günü belli değil",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Eski sürümden taşınan "Maaş" tutarsız gelir; kullanıcıdan tutarı istenir.
        if (income.amount.isZero) {
            Text("Tutarı gir", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleSmall)
        } else {
            Text(income.amount.display(), style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** Borç satırının altındaki kısa ilerleme: yazı + ince çubuk. */
@Composable
private fun DebtProgressLine(progress: DebtProgress) {
    val good = CinchTheme.colors.good
    val text = when (progress.plan) {
        FixedPlan.INSTALLMENTS -> "${progress.paidCount}/${progress.totalCount} taksit · ${progress.remainingCount} kaldı"
        else -> "Kalan ${progress.remainingDebt.display()}"
    }
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.fraction)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(good),
        )
    }
}

@Composable
private fun FinishedRow(status: FixedStatus, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(status.fixed.kind.emoji, style = MaterialTheme.typography.titleMedium)
        Text(status.fixed.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        status.progress?.let {
            Text("Toplam ${it.paidTotal.display()}", style = MaterialTheme.typography.bodySmall, color = CinchTheme.colors.good)
        }
    }
}
