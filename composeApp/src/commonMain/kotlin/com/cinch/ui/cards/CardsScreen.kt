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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cinch.ui.onboarding.CoachMarkSteps
import com.cinch.ui.onboarding.coachMarkTarget
import com.cinch.shared.cards.CardOverview
import com.cinch.ui.common.display

/**
 * @param onPickStatement banka ekranı fotoğraflarını seç (kart, fotoğraftaki son 4 haneden bulunur); platformda
 *   okuma yoksa null.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardsScreen(
    state: CardsState,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onPickStatement: (() -> Unit)? = null,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kartlar") },
                actions = {
                    if (onPickStatement != null && state.cards.isNotEmpty()) {
                        TextButton(
                            onClick = onPickStatement,
                            modifier = Modifier.coachMarkTarget(CoachMarkSteps.CARDS[1].key),
                        ) { Text("📸 Ekstre oku") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                modifier = Modifier.coachMarkTarget(CoachMarkSteps.CARDS[0].key),
            ) { Text("Kart ekle") }
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
                    "Kartını eklersen harcarken adını yazman yeter (\"195 market qnb\"). Banka uygulamasındaki kart " +
                        "ekranının fotoğrafından ne kadar ve ne zaman ödeyeceğini okuruz; günü gelmeden hatırlatırız.",
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
            items(state.cards, key = { it.card.id }) { overview ->
                CardTile(overview = overview, onClick = { onOpen(overview.card.id) })
            }
        }
    }
}

/**
 * Kart görünümlü kutucuk: kartın adı ve en çok merak edilen şey; kredi kartında ödenecek tutar ve son ödemeye kalan
 * gün, ekstre okunmadıysa bu dönemin harcaması ve okutma çağrısı, banka kartında bu ayın harcaması.
 */
@Composable
fun CardTile(overview: CardOverview, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val card = overview.card
    val headline = overview.headline
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.75f)
            .clip(RoundedCornerShape(24.dp))
            .background(cardBrush(card))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(20.dp),
    ) {
        Column(Modifier.align(Alignment.TopStart), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                card.name + (card.last4?.let { "  •••• $it" } ?: ""),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )
            Text(
                if (card.isCredit) "Kredi kartı" else "Banka kartı",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
        // Kartın "çipi": küçük, oyuncak gibi bir dokunuş.
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .size(width = 40.dp, height = 30.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFFFD98A).copy(alpha = 0.9f)),
        )
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(headline.label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
            Text(headline.amount.display(), style = MaterialTheme.typography.headlineMedium, color = Color.White)
        }
        overview.statusPill?.let { pill ->
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd),
                shape = RoundedCornerShape(12.dp),
                color = if (overview.needsAttention) Color.White else Color.White.copy(alpha = 0.22f),
            ) {
                Text(
                    pill,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (overview.needsAttention) MaterialTheme.colorScheme.error else Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }
}
