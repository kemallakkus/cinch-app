package com.cinch.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cinch.shared.budget.BudgetStatus
import com.cinch.shared.domain.Expense
import com.cinch.ui.common.display
import com.cinch.ui.common.emoji
import com.cinch.ui.common.label
import com.cinch.ui.common.relativeLabel
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeState,
    onEvent: (HomeEvent) -> Unit,
    snackbarHostState: SnackbarHostState,
    onOpenBudget: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cinch") },
                actions = { TextButton(onClick = onOpenBudget) { Text("Bütçe") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { QuickEntryBar(state = state, onEvent = onEvent) },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.status?.let { status -> item(key = "status") { StatusCard(status) } }

            item(key = "header") {
                Text(
                    "Bu hafta",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
            }

            if (state.weekExpenses.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "Henüz harcama yok. Aşağıya \"195 market\" ya da \"dün 80 taksi\" gibi yaz.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(state.weekExpenses, key = { it.id }) { expense ->
                ExpenseRow(
                    expense = expense,
                    today = state.today,
                    onDelete = { onEvent(HomeEvent.DeleteExpense(expense)) },
                )
            }
        }
    }
}

@Composable
private fun StatusCard(status: BudgetStatus) {
    val overToday = status.todayRemaining.isNegative
    val alert = MaterialTheme.colorScheme.error

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (overToday) "Bugünkü payını aştın" else "Bugün harcayabileceğin",
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                status.todayRemaining.absolute.display(),
                style = MaterialTheme.typography.displaySmall,
                color = if (overToday) alert else MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (!status.todaySpent.isZero) {
                Text("Bugün ${status.todaySpent.display()} harcadın", style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { status.pacePercent.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = if (status.isOverLimit) alert else MaterialTheme.colorScheme.primary,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${status.weekSpent.display()} / ${status.weeklyLimit.display()}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    if (status.isOverLimit) "${status.weekRemaining.absolute.display()} aşıldı"
                    else "${status.daysRemaining} gün kaldı",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (status.isOverLimit) alert else MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            if (!status.isOverLimit && status.daysRemaining > 1) {
                Text(
                    "Kalan günler için günde ${status.dailyAllowance.display()}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text("Bu ay toplam ${status.monthTotal.display()}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ExpenseRow(expense: Expense, today: LocalDate, onDelete: () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        onDismiss = { if (it == SwipeToDismissBoxValue.EndToStart) onDelete() },
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer, CardDefaults.shape)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text("Sil", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        val shared = expense.chargeableFraction < 1.0
        ListItem(
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer, CardDefaults.shape),
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            leadingContent = { Text(expense.category.emoji, style = MaterialTheme.typography.titleLarge) },
            headlineContent = {
                Text(
                    expense.rawText.ifBlank { expense.category.label },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                val parts = buildList {
                    add(expense.category.label)
                    add(expense.date.relativeLabel(today))
                    if (shared) add("toplam ${expense.grossAmount.display()}")
                }
                Text(parts.joinToString(" · "))
            },
            trailingContent = {
                Text(expense.chargeableAmount.display(), style = MaterialTheme.typography.titleMedium)
            },
        )
    }
}
