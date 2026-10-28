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
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cinch.shared.debt.CardPayoffResult
import com.cinch.shared.domain.Card as CreditCard
import com.cinch.shared.domain.Expense
import com.cinch.shared.money.Money
import com.cinch.shared.reconcile.ReconciliationKind
import com.cinch.shared.reconcile.ReconciliationResult
import com.cinch.ui.common.BackButton
import com.cinch.ui.common.CategoryBadge
import com.cinch.ui.common.ConfettiBurst
import com.cinch.ui.common.TitleWithInfo
import com.cinch.ui.common.dayOfMonthLabel
import com.cinch.ui.common.display
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
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onScanStatement: (() -> Unit)? = null,
    onPickStatement: (() -> Unit)? = null,
) {
    val card = state.card
    // Bankayla tuttuğunda (ya da eksik fark eklenip tutunca) küçük bir kutlama.
    var confetti by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.reconciliation) {
        if (state.reconciliation?.kind == ReconciliationKind.MATCH) confetti++
    }
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(card?.name ?: "Kart") },
                    navigationIcon = { BackButton(onBack) },
                    actions = { if (card != null) TextButton(onClick = onEdit) { Text("Düzenle") } },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    item(key = "tile") { CardTile(card) }
                    item(key = "statement") { StatementCard(card, state, onScan = onScanStatement, onPick = onPickStatement) }
                    item(key = "period") { PeriodCard(card, state) }
                    // Banka kartında ekstre, borç ve faiz yok.
                    if (card.isCredit) {
                        item(key = "reconcile") { ReconcileSection(state, onEvent) }
                        item(key = "payoff") { PayoffSection(card, state) }
                    }
                    if (state.periodExpenses.isNotEmpty()) {
                        item(key = "expensesHeader") {
                            Text("Bu dönemin harcamaları", style = MaterialTheme.typography.titleMedium)
                        }
                        items(state.periodExpenses, key = { it.id }) { ExpenseLine(it, state.today) }
                    }
                }
            }
        }
        ConfettiBurst(trigger = confetti, originY = 0.5f)
    }
}

@Composable
private fun PeriodCard(card: CreditCard, state: CardDetailState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val period = state.period
            Text(if (card.isCredit) "Bu ekstreye girecek harcamalar" else "Bu ayın harcamaları", style = MaterialTheme.typography.labelLarge)
            Text((state.periodTotal ?: Money.zero(card.currency)).display(), style = MaterialTheme.typography.displaySmall)
            val range = period?.let { "${it.start.shortLabel()} – ${it.endInclusive.shortLabel()}" }.orEmpty()
            Text(
                when {
                    !card.isCredit -> range
                    card.statementDay != null -> "$range · hesap kesim ${dayOfMonthLabel(card.statementDay).lowercase()}"
                    else -> "$range · hesap kesim günü girilmedi, ayın başından sayıyoruz"
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ReconcileSection(state: CardDetailState, onEvent: (CardDetailEvent) -> Unit) {
    Section(
        title = "Bankayla karşılaştır",
        description = "Banka uygulamandaki \"dönem içi harcama\" tutarını yaz; buraya girdiklerinle tutuyor mu bakalım.",
        info = "Banka, hesap kesim gününden beri kartla yaptığın harcamaları toplar. O toplamı Cinch'e girdiğin " +
            "harcamalarla karşılaştırırız; fark varsa bir harcamayı yazmayı unutmuş olabilirsin.",
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
        ReconciliationKind.MATCH -> Text("Tutuyor, her şeyi yazmışsın 👍", style = MaterialTheme.typography.titleSmall)
        ReconciliationKind.MISSING_EXPENSE -> {
            Text(
                "${result.difference.display()} tutarında harcama yazılmamış görünüyor.",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                "Sen ${result.recordedTotal.display()} yazmışsın, banka ${result.statedActual.display()} diyor. " +
                    "Neye harcadığını hatırlamıyorsan farkı tek seferde ekleyebilirsin; bütçene de sayılır.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = { onEvent(CardDetailEvent.AddMissingDifference) }) {
                Text("Farkı harcama olarak ekle")
            }
        }
        ReconciliationKind.EXTRA_RECORDED -> {
            Text(
                "Yazdıkların bankadakinden ${result.difference.absolute.display()} fazla.",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                "Bir harcama iki kez, yanlış tutarla ya da yanlış karta yazılmış olabilir. " +
                    "Bazen banka bir harcamayı birkaç gün sonra gösterir; o zaman beklemek yeter.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PayoffSection(card: CreditCard, state: CardDetailState) {
    if (card.carriedBalance.isZero) {
        Section(
            title = "Borcun ne zaman biter?",
            description = "Bu kartta ödenmemiş eski borç yok 🎉 Ekstrenin tamamını her ay ödedikçe faiz ödemezsin.",
        ) {}
        return
    }
    Section(
        title = "Borcun ne zaman biter?",
        description = "Kartta ${card.carriedBalance.display()} ödenmemiş eski borç var ve her ay " +
            "%${formatPercent(card.monthlyRatePercent)} faiz ekleniyor. Her ay ne kadar ödersen ne zaman biter, bak:",
        info = "Kartın borcunun tamamını ödemezsen kalan kısma faiz eklenir. Aynı tutarı her ay ödediğini " +
            "varsayıp borcun kaç ayda biteceğini ve toplam ne kadar faiz ödeyeceğini hesaplıyoruz.",
    ) {
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

@Composable
private fun PayoffResult(result: CardPayoffResult, firstMonthInterest: Money?) {
    if (!result.closes) {
        Text(
            "Bu ödemeyle borç hiç bitmez.",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.error,
        )
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

@Composable
private fun Section(title: String, description: String, info: String? = null, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (info != null) TitleWithInfo(title = title, info = info)
            else Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun ExpenseLine(expense: Expense, today: LocalDate) {
    ListItem(
        modifier = Modifier.clip(MaterialTheme.shapes.medium),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        leadingContent = { CategoryBadge(expense.category) },
        headlineContent = { Text(expense.rawText.ifBlank { expense.category.label }) },
        supportingContent = { Text("${expense.category.label} · ${expense.date.relativeLabel(today)}") },
        // Mutabakat brüt tutarla yapılır: karta giren tam tutar gösterilir.
        trailingContent = { Text(expense.grossAmount.display(), style = MaterialTheme.typography.titleSmall) },
    )
}
