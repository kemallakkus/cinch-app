package com.cinch.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cinch.shared.debt.CardPayoffResult
import com.cinch.shared.domain.Card as CreditCard
import com.cinch.shared.domain.Expense
import com.cinch.shared.money.Money
import com.cinch.shared.reconcile.ReconciliationKind
import com.cinch.shared.reconcile.ReconciliationResult
import com.cinch.ui.common.display
import com.cinch.ui.common.emoji
import com.cinch.ui.common.formatPercent
import com.cinch.ui.common.label
import com.cinch.ui.common.relativeLabel
import com.cinch.ui.common.shortLabel
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(
    state: CardDetailState,
    onEvent: (CardDetailEvent) -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val card = state.card
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(card?.name ?: "Kart") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Geri") } },
                actions = { if (card != null) TextButton(onClick = onEdit) { Text("Düzenle") } },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            card == null -> Box(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text("Bu kart artık yok.")
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "period") { PeriodCard(card, state) }
                item(key = "reconcile") { ReconcileSection(state, onEvent) }
                item(key = "payoff") { PayoffSection(card, state) }
                if (state.periodExpenses.isNotEmpty()) {
                    item(key = "expensesHeader") {
                        Text("Bu dönemin harcamaları", style = MaterialTheme.typography.titleMedium)
                    }
                    items(state.periodExpenses, key = { it.id }) { ExpenseLine(it, state.today) }
                }
            }
        }
    }
}

@Composable
private fun PeriodCard(card: CreditCard, state: CardDetailState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val period = state.period
            Text(
                if (period == null) "Bu dönem" else "Bu dönem · ${period.start.shortLabel()} – ${period.endInclusive.shortLabel()}",
                style = MaterialTheme.typography.labelLarge,
            )
            Text((state.periodTotal ?: Money.zero(card.currency)).display(), style = MaterialTheme.typography.displaySmall)
            val details = buildList {
                add(card.statementDay?.let { "Ekstre günü: $it" } ?: "Ekstre günü yok, dönem ayın başından")
                add("Faiz %${formatPercent(card.monthlyRatePercent)}")
            }
            Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ReconcileSection(state: CardDetailState, onEvent: (CardDetailEvent) -> Unit) {
    Section(
        title = "Mutabakat",
        description = "Banka uygulamasındaki dönem içi harcamayı yaz; kayıtlarınla karşılaştırayım.",
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                state = state.statedSpending,
                modifier = Modifier.weight(1f),
                label = { Text("Bankadaki tutar") },
                suffix = { Text("₺") },
                isError = state.statedSpendingError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                onKeyboardAction = { onEvent(CardDetailEvent.Reconcile) },
                lineLimits = TextFieldLineLimits.SingleLine,
            )
            Button(onClick = { onEvent(CardDetailEvent.Reconcile) }, modifier = Modifier.height(56.dp)) {
                Text("Karşılaştır")
            }
        }
        if (state.statedSpendingError) {
            Text("Geçerli bir tutar yaz (ör. 1.240,50)", color = MaterialTheme.colorScheme.error)
        }
        state.reconciliation?.let { ReconcileResult(it, onEvent) }
    }
}

@Composable
private fun ReconcileResult(result: ReconciliationResult, onEvent: (CardDetailEvent) -> Unit) {
    when (result.kind) {
        ReconciliationKind.MATCH -> Text("Kayıtların bankayla tutuyor 👍", style = MaterialTheme.typography.titleSmall)
        ReconciliationKind.MISSING_EXPENSE -> {
            Text(
                "${result.difference.display()} yazmayı unutmuşsun.",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                "Kayıtlarda ${result.recordedTotal.display()}, bankada ${result.statedActual.display()} var. " +
                    "Hatırlamıyorsan farkı tek kayıt olarak ekleyebilirsin; bütçene de sayılır.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = { onEvent(CardDetailEvent.AddMissingDifference) }) {
                Text("Farkı harcama olarak ekle")
            }
        }
        ReconciliationKind.EXTRA_RECORDED -> {
            Text(
                "Kayıtların bankadan ${result.difference.absolute.display()} fazla.",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                "Yanlış karta, yanlış tutarla ya da iki kez girilmiş bir kayıt olabilir. " +
                    "Banka henüz işlememiş bir harcama da olabilir.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PayoffSection(card: CreditCard, state: CardDetailState) {
    if (card.carriedBalance.isZero) {
        Section(
            title = "Borç kapanışı",
            description = "Devreden borç yok. Ekstreyi her ay tam ödedikçe faiz ödemezsin.",
        ) {}
        return
    }
    Section(
        title = "Borç kapanışı",
        description = "Devreden borç ${card.carriedBalance.display()}, aylık %${formatPercent(card.monthlyRatePercent)} faiz. " +
            "Her ay ne kadar ödersen ne zaman biter?",
    ) {
        OutlinedTextField(
            state = state.monthlyPayment,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Aylık ödeme") },
            suffix = { Text("₺") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            lineLimits = TextFieldLineLimits.SingleLine,
        )
        state.payoff?.let { PayoffResult(it, state.firstMonthInterest) }
    }
}

@Composable
private fun PayoffResult(result: CardPayoffResult, firstMonthInterest: Money?) {
    if (!result.closes) {
        Text(
            "Bu ödemeyle borç hiç kapanmaz.",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.error,
        )
        firstMonthInterest?.let {
            Text(
                "Aylık faiz ${it.display()}; ödeme bunun üstünde olmalı, yoksa borç küçülmez.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        return
    }
    Text("${durationLabel(result.months)} kapanır", style = MaterialTheme.typography.titleSmall)
    Text(
        "Toplam faiz ${result.totalInterest.display()} · toplam ödeme ${result.totalPaid.display()}",
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

@Composable
private fun Section(title: String, description: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun ExpenseLine(expense: Expense, today: LocalDate) {
    ListItem(
        leadingContent = { Text(expense.category.emoji, style = MaterialTheme.typography.titleLarge) },
        headlineContent = { Text(expense.rawText.ifBlank { expense.category.label }) },
        supportingContent = { Text("${expense.category.label} · ${expense.date.relativeLabel(today)}") },
        // Mutabakat brüt tutarla yapılır: karta giren tam tutar gösterilir.
        trailingContent = { Text(expense.grossAmount.display(), style = MaterialTheme.typography.titleSmall) },
    )
}
