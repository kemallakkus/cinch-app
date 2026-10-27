package com.cinch.ui.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cinch.shared.domain.Card
import com.cinch.ui.common.display
import com.cinch.ui.common.formatPercent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardsScreen(state: CardsState, onBack: () -> Unit, onAdd: () -> Unit, onOpen: (Long) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kartlar") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Geri") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAdd) { Text("Kart ekle") }
        },
    ) { padding ->
        if (!state.isLoading && state.cards.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text(
                    "Henüz kart yok. Kart eklersen hızlı kayıtta adını yazarak (\"195 market qnb\") harcamayı " +
                        "karta bağlarsın, ekstre gününden önce hatırlatma alırsın ve borcunun ne zaman " +
                        "kapanacağını görürsün.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp), // FAB son satırı örtmesin
        ) {
            items(state.cards, key = { it.id }) { card ->
                CardRow(card = card, onClick = { onOpen(card.id) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun CardRow(card: Card, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Text("💳", style = MaterialTheme.typography.titleLarge) },
        headlineContent = { Text(card.name) },
        supportingContent = {
            val parts = buildList {
                card.statementDay?.let { add("Ekstre günü: $it") }
                add("Faiz %${formatPercent(card.monthlyRatePercent)}")
            }
            Text(parts.joinToString(" · "))
        },
        trailingContent = {
            if (!card.carriedBalance.isZero) {
                Text(card.carriedBalance.display(), style = MaterialTheme.typography.titleSmall)
            }
        },
    )
}
