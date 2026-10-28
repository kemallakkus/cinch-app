package com.cinch.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cinch.shared.domain.Category
import com.cinch.ui.common.BackButton
import com.cinch.ui.common.ExpenseRow
import com.cinch.ui.common.MonthSwitcher
import com.cinch.ui.common.display
import com.cinch.ui.common.emoji
import com.cinch.ui.common.label
import com.cinch.ui.common.relativeLabel
import com.cinch.ui.theme.colors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    state: HistoryState,
    onEvent: (HistoryEvent) -> Unit,
    onOpenExpense: (Long) -> Unit,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Geçmiş") },
                navigationIcon = { BackButton(onBack) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "search") {
                OutlinedTextField(
                    state = state.query,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Ara: migros, taksi, qnb…") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.isSearching) {
                            IconButton(onClick = { state.query.clearText() }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Aramayı temizle")
                            }
                        }
                    },
                    shape = MaterialTheme.shapes.extraLarge,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
            }

            item(key = "categories") {
                CategoryFilter(selected = state.category, onPick = { onEvent(HistoryEvent.CategoryPicked(it)) })
            }

            item(key = "period") {
                if (state.isSearching) {
                    Text(
                        "Tüm geçmişte aranıyor",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                } else {
                    MonthSwitcher(
                        month = state.month,
                        canGoNext = state.canGoNext,
                        onPrevious = { onEvent(HistoryEvent.PreviousMonth) },
                        onNext = { onEvent(HistoryEvent.NextMonth) },
                    )
                }
            }

            item(key = "total") {
                val total = state.total
                Text(
                    if (total == null) "" else "${state.count} kayıt · toplam ${total.display()}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            if (!state.isLoading && state.days.isEmpty()) {
                item(key = "empty") { EmptyHistory(state) }
            }

            state.days.forEach { day ->
                item(key = "day-${day.date}") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val label = day.date.relativeLabel(state.today) +
                            if (day.date.year != state.today.year) " ${day.date.year}" else ""
                        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        Text(
                            day.total.display(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(day.expenses, key = { "expense-${it.id}" }) { expense ->
                    ExpenseRow(
                        expense = expense,
                        today = state.today,
                        cardName = expense.cardId?.let { state.cardNames[it] },
                        onClick = { onOpenExpense(expense.id) },
                        onDelete = { onEvent(HistoryEvent.DeleteExpense(expense)) },
                        showDate = false,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryFilter(selected: Category?, onPick: (Category?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "all") {
            FilterChip(selected = selected == null, onClick = { onPick(null) }, label = { Text("Tümü") })
        }
        items(Category.entries, key = { it.name }) { category ->
            val colors = category.colors()
            FilterChip(
                selected = category == selected,
                // Seçili olana tekrar dokununca süzgeç kalkar.
                onClick = { onPick(if (category == selected) null else category) },
                label = { Text("${category.emoji} ${category.label}") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = colors.container,
                    selectedLabelColor = colors.content,
                ),
            )
        }
    }
}

@Composable
private fun EmptyHistory(state: HistoryState) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(if (state.isSearching) "🔍" else "🗓️", style = MaterialTheme.typography.displaySmall)
        Text(
            when {
                state.isSearching -> "\"${state.searchText}\" için kayıt yok"
                state.category != null -> "Bu ay ${state.category.label} harcaması yok"
                else -> "Bu ay kayıt yok"
            },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        if (state.isSearching) {
            Text(
                "Açıklama, not, kategori ve kart adında arıyoruz.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
