package com.cinch.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cinch.shared.gamification.BadgeState
import com.cinch.shared.gamification.GamificationState
import com.cinch.shared.gamification.ObserveGamification
import com.cinch.shared.time.LocalClock
import com.cinch.ui.common.BackButton
import com.cinch.ui.common.HeroCard
import com.cinch.ui.common.shortLabel
import com.cinch.ui.theme.CinchTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.compose.viewmodel.koinViewModel

@Immutable
data class AchievementsState(val isLoading: Boolean = true, val data: GamificationState? = null)

/** Seriler ve rozetler; yalnız gösterir (rozet kaydı ve kutlama ana ekranda). */
class AchievementsViewModel(gamification: ObserveGamification, clock: LocalClock) : ViewModel() {
    val state: StateFlow<AchievementsState> = gamification(clock.today())
        .map { AchievementsState(isLoading = false, data = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AchievementsState())
}

@Composable
fun AchievementsWrapper(onBack: () -> Unit, viewModel: AchievementsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AchievementsScreen(state, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(state: AchievementsState, onBack: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Seriler ve rozetler") }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        val data = state.data
        if (data == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { StreakHero(data) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Rozetler · ${data.badges.count { it.isEarned }}/${data.badges.size}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                )
            }
            items(data.badges, key = { it.badge.name }) { BadgeTile(it) }
        }
    }
}

@Composable
private fun StreakHero(data: GamificationState) {
    val s = data.streaks
    val colors = CinchTheme.colors
    HeroCard(brush = colors.hero) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Sınır serin", style = MaterialTheme.typography.labelLarge, color = colors.onHero)
            Text(
                if (s.budgetStreak > 0) "🔥 ${s.budgetStreak} gün" else "🔥 0 gün",
                style = MaterialTheme.typography.displaySmall,
                color = colors.onHero,
            )
            Text(
                when {
                    s.todayOnTrack -> "Bugün de payının içindesin; gün böyle biterse seri ${s.budgetStreak + 1} olur."
                    else -> "Bugün payını aştın; yarın yeniden başlarsın. Rozetlerin gitmez."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onHero,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 6.dp)) {
                Stat("En uzun", "${s.bestBudgetStreak} gün", colors.onHero)
                Stat("Kayıt serisi", "${s.loggingStreak} gün", colors.onHero)
                Stat("Sınırda haftalar", "${s.weeksOnTrack}", colors.onHero)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun BadgeTile(state: BadgeState) {
    val earned = state.isEarned
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(if (earned) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(state.badge.emoji, style = MaterialTheme.typography.displaySmall, modifier = Modifier.alpha(if (earned) 1f else 0.35f))
        Text(state.badge.title, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        Text(
            state.badge.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        // Rozet ortak modülde: akıllı tür dönüşümü için yerel değişken.
        val progress = state.progress
        when {
            earned -> Text(
                "✓ ${state.earnedOn?.shortLabel()}",
                style = MaterialTheme.typography.labelMedium,
                color = CinchTheme.colors.good,
            )
            progress != null -> {
                val (now, goal) = progress
                Text("$now/$goal", style = MaterialTheme.typography.labelMedium)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((now.toFloat() / goal).coerceIn(0f, 1f))
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(CinchTheme.colors.good),
                    )
                }
            }
        }
    }
}
