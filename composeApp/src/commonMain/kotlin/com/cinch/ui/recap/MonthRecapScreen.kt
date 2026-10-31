package com.cinch.ui.recap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cinch.navigation.MonthRecapRoute
import com.cinch.shared.budget.MonthRecap
import com.cinch.ui.common.AnimatedMoneyText
import com.cinch.ui.common.BackButton
import com.cinch.ui.common.HeroCard
import com.cinch.ui.common.display
import com.cinch.ui.common.emoji
import com.cinch.ui.common.label
import com.cinch.ui.common.monthYearLabel
import com.cinch.ui.theme.CinchTheme
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun MonthRecapWrapper(
    route: MonthRecapRoute,
    onBack: () -> Unit,
    onOpenInsights: (LocalDate) -> Unit,
    viewModel: MonthRecapViewModel = koinViewModel { parametersOf(route) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MonthRecapScreen(state, viewModel::onEvent, onBack, onOpenInsights)
}

/** Bir ayın tek ekranlık özeti: ne kadar harcandı, geçen aya göre, en çok nereye, kaç gün payın içinde, kumbara. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthRecapScreen(
    state: MonthRecapState,
    onEvent: (MonthRecapEvent) -> Unit,
    onBack: () -> Unit,
    onOpenInsights: (LocalDate) -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Ay özeti") }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onEvent(MonthRecapEvent.Previous) }, enabled = state.canGoBack) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Önceki ay")
                }
                Text(
                    state.month.monthYearLabel(),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onEvent(MonthRecapEvent.Next) }, enabled = state.canGoNext) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Sonraki ay")
                }
            }

            val recap = state.recap
            when {
                state.isLoading || recap == null -> Unit
                recap.isEmpty -> Text(
                    "Bu ay kayıt yok.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                else -> RecapContent(recap, onOpenInsights)
            }
        }
    }
}

@Composable
private fun RecapContent(recap: MonthRecap, onOpenInsights: (LocalDate) -> Unit) {
    val colors = CinchTheme.colors
    HeroCard(brush = colors.hero) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Harcadığın", style = MaterialTheme.typography.labelLarge, color = colors.onHero)
            AnimatedMoneyText(recap.spent, style = MaterialTheme.typography.displayMedium, color = colors.onHero)
            Text(changeLine(recap), style = MaterialTheme.typography.bodyMedium, color = colors.onHero)
            Text(moodLine(recap), style = MaterialTheme.typography.bodySmall, color = colors.onHero)
        }
    }

    val tiles = buildList {
        recap.topCategory?.let { add(Tile(it.category.emoji, "En çok", "${it.category.label} · ${it.amount.display()}")) }
        recap.daysOnTrack?.takeIf { recap.daysCounted > 0 }?.let { add(Tile("🔥", "Günlük payında", "${it} / ${recap.daysCounted} gün")) }
        if (!recap.saved.isZero) add(Tile("💰", "Kumbaraya", recap.saved.display()))
        if (!recap.fixedPaid.isZero) add(Tile("🧾", "Sabit giderler", recap.fixedPaid.display()))
        add(Tile("✍️", "Kayıt", "${recap.entries} harcama"))
        recap.topWord?.let { add(Tile("🔎", "En çok giden kelime", "${it.word} · ${it.amount.display()}")) }
    }
    tiles.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { TileCard(it, Modifier.weight(1f)) }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }

    OutlinedButton(onClick = { onOpenInsights(recap.month) }, modifier = Modifier.fillMaxWidth()) {
        Text("Nereye gitti? ayrıntısı")
    }
}

private data class Tile(val emoji: String, val title: String, val value: String)

@Composable
private fun TileCard(tile: Tile, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(tile.emoji, style = MaterialTheme.typography.titleLarge)
            Text(tile.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(tile.value, style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** "Geçen aydan %8 az 👏" / "%12 fazla" / ilk ay. */
internal fun changeLine(recap: MonthRecap): String {
    val change = recap.change ?: return "Cinch'le ilk ayın 🎉"
    val percent = (abs(change) * 100).roundToInt()
    return when {
        percent == 0 -> "Geçen ayla aynı"
        change < 0 -> "Geçen aydan %$percent az 👏"
        else -> "Geçen aydan %$percent fazla"
    }
}

/** Kısa, suçlamayan bir değerlendirme. */
private fun moodLine(recap: MonthRecap): String {
    val onTrack = recap.daysOnTrack ?: return "Haftalık bütçeni kurarsan günlük payına göre de değerlendiririz."
    if (recap.daysCounted == 0) return ""
    val ratio = onTrack.toDouble() / recap.daysCounted
    return when {
        ratio >= 0.9 -> "Harika bir ay: neredeyse her gün payının içinde kaldın."
        ratio >= 0.7 -> "İyi bir ay: günlerin çoğunda payının içindeydin."
        ratio >= 0.5 -> "İdare eder: bazı günler payını aştın; yeni ay yeni seri."
        else -> "Zorlu bir ay oldu. Bu ay günlük payına göz kulak olalım."
    }
}
