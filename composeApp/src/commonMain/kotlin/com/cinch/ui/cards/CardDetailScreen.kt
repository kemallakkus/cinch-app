package com.cinch.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.cinch.shared.domain.Expense
import com.cinch.shared.reconcile.ReconciliationKind
import com.cinch.ui.common.BackButton
import com.cinch.ui.common.CategoryBadge
import com.cinch.ui.common.ConfettiBurst
import com.cinch.ui.common.display
import com.cinch.ui.common.label
import com.cinch.ui.common.relativeLabel
import kotlinx.datetime.LocalDate

/**
 * Kart detayı üç blok: en üstte kartın renginde "ne kadar, ne zaman ödenecek" ([CardHero]), ardından sıradaki
 * ekstreye girenler ve bankaya göre eksik ([PeriodBlock]), son ekstrelerin geçmişi ([HistoryBlock]). Borç planı
 * yalnız gerektiğinde ya da kullanıcı isteyince açılır.
 *
 * @param onScanStatement belge tarayıcı (banka uygulaması başka telefondaysa onun ekranı taranır).
 * @param onPickStatement galeriden fotoğraf(lar): başka telefonla çekilip gönderilen ekran fotoğrafları.
 */
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
            val overview = state.overview
            when {
                state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                card == null || overview == null -> Box(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                    Text("Bu kart artık yok.")
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "hero") { CardHero(overview, state, onEvent, onScan = onScanStatement, onPick = onPickStatement) }
                    if (card.isCredit && state.payoffDebt != null) {
                        item(key = "payoff") { PayoffPlan(card, state, onEvent) }
                    }
                    item(key = "period") { PeriodBlock(overview, state, onEvent, onPick = onPickStatement) }
                    if (card.isCredit && (state.statementHistory.size >= 2 || state.statement?.futureInstallments?.isZero == false)) {
                        item(key = "history") { HistoryBlock(state) }
                    }
                    if (state.periodExpenses.isNotEmpty()) {
                        item(key = "expensesHeader") {
                            Text(
                                if (card.isCredit) "Sıradaki ekstrenin harcamaları" else "Bu ayın harcamaları",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        items(state.periodExpenses, key = { it.id }) { ExpenseLine(it, state.today) }
                    }
                }
            }
        }
        ConfettiBurst(trigger = confetti, originY = 0.5f)
    }

    if (state.confirmingPaid) {
        AlertDialog(
            onDismissRequest = { onEvent(CardDetailEvent.MarkPaidDismissed) },
            title = { Text("Ekstreyi ödedin mi?") },
            text = {
                Text(
                    "Bu ekstreyi ödenmiş sayarız; son ödeme hatırlatmaları durur. Kısmen ödediysen banka ekranını " +
                        "yeniden okut, kalan borç doğru görünsün.",
                )
            },
            confirmButton = { TextButton(onClick = { onEvent(CardDetailEvent.MarkPaidConfirmed) }) { Text("Ödedim") } },
            dismissButton = { TextButton(onClick = { onEvent(CardDetailEvent.MarkPaidDismissed) }) { Text("Vazgeç") } },
        )
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
