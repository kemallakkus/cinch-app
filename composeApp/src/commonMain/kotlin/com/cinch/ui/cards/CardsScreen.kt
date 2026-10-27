package com.cinch.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cinch.shared.domain.Card
import com.cinch.ui.common.dayOfMonthLabel
import com.cinch.ui.common.display
import com.cinch.ui.common.formatPercent
import com.cinch.ui.theme.CinchTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardsScreen(state: CardsState, onAdd: () -> Unit, onOpen: (Long) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Kartlar") })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAdd) { Text("Kart ekle") }
        },
    ) { padding ->
        if (!state.isLoading && state.cards.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("💳", style = MaterialTheme.typography.displaySmall)
                Text("Kartını ekle", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Henüz kart yok. Kredi kartını eklersen harcarken kartın adını yazman yeter " +
                        "(\"195 market qnb\"). Hesap kesim gününden bir gün önce hatırlatırız; kartta " +
                        "ödenmemiş borç varsa ne zaman biteceğini gösteririz.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            // Alttaki boşluk: FAB son kartı örtmesin.
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(state.cards, key = { it.id }) { card ->
                CardTile(card = card, onClick = { onOpen(card.id) })
            }
        }
    }
}

/** Kredi kartı görünümlü kutucuk; rengi kartın id'sine göre sabit kalır. */
@Composable
fun CardTile(card: Card, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val gradients = CinchTheme.colors.cardGradients
    val brush = gradients[(card.id % gradients.size).toInt().let { if (it < 0) it + gradients.size else it }]
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.75f)
            .clip(RoundedCornerShape(24.dp))
            .background(brush)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(20.dp),
    ) {
        Column(Modifier.align(Alignment.TopStart), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(card.name, style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text(
                card.statementDay?.let { "Hesap kesim: ${dayOfMonthLabel(it).lowercase()}" } ?: "Hesap kesim günü yok",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
        // Kartın "çipi": küçük, oyuncak gibi bir dokunuş.
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .size(width = 40.dp, height = 30.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFFFD98A).copy(alpha = 0.9f)),
        )
        Column(Modifier.align(Alignment.BottomEnd), horizontalAlignment = Alignment.End) {
            if (card.carriedBalance.isZero) {
                Text("Eski borç yok ✨", style = MaterialTheme.typography.titleSmall, color = Color.White)
            } else {
                Text(card.carriedBalance.display(), style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text("ödenmemiş eski borç", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
            }
        }
        Text(
            "Aylık faiz %${formatPercent(card.monthlyRatePercent)}",
            modifier = Modifier.align(Alignment.BottomStart),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.85f),
        )
    }
}
