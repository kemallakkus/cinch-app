package com.cinch.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.ui.common.display
import com.cinch.ui.theme.CinchTheme
import org.koin.compose.viewmodel.koinViewModel

/**
 * Ana ekranda bütçe kartının altındaki oyunsu şerit.
 *
 * @param onCelebrate yeni rozet ya da kumbaraya atılan para: ana ekran konfeti patlatır ve mesajı gösterir.
 */
@Composable
fun ProgressStripWrapper(
    onOpenAchievements: () -> Unit,
    onOpenSavings: () -> Unit,
    onCelebrate: (String) -> Unit,
    viewModel: ProgressStripViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnCelebrate by rememberUpdatedState(onCelebrate)
    LifecycleResumeEffect(viewModel) {
        viewModel.onEvent(ProgressStripEvent.Resumed)
        onPauseOrDispose { }
    }
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ProgressStripEffect.Celebrate -> currentOnCelebrate(effect.message)
            }
        }
    }
    if (!state.isLoading) {
        ProgressStrip(state, viewModel::onEvent, onOpenAchievements, onOpenSavings)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProgressStrip(
    state: ProgressStripState,
    onEvent: (ProgressStripEvent) -> Unit,
    onOpenAchievements: () -> Unit,
    onOpenSavings: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val streak = state.streaks.budgetStreak
            AssistChip(
                onClick = onOpenAchievements,
                label = {
                    Text(
                        when {
                            streak > 0 -> "🔥 $streak gün sınırda"
                            state.streaks.todayOnTrack -> "🔥 Seri bugün başlıyor"
                            else -> "🏅 Rozetler"
                        },
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (streak > 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                border = null,
            )
            AssistChip(
                onClick = onOpenSavings,
                label = {
                    Text(
                        if (state.savedTotal > 0) "🐷 Kumbara ${Money(state.savedTotal, Currency.TRY).display()}" else "🐷 Kumbara",
                    )
                },
                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = null,
            )
        }

        state.leftover?.let { leftover ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Geçen hafta ${leftover.amount.display()} artırdın 🎉",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    "Bir hedefe ayır: para bankadan çıkmaz, bu ay elinde kalandan düşer, kumbaran dolar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (state.goals.isEmpty()) {
                        FilledTonalButton(onClick = onOpenSavings) { Text("🐷 Hedef oluştur") }
                    } else {
                        state.goals.take(3).forEach { progress ->
                            FilledTonalButton(onClick = { onEvent(ProgressStripEvent.LeftoverToGoal(progress.goal.id)) }) {
                                Text("${progress.goal.emoji} ${progress.goal.name}")
                            }
                        }
                    }
                    TextButton(onClick = { onEvent(ProgressStripEvent.LeftoverDismissed) }) { Text("Şimdi değil") }
                }
            }
        }
        if (state.streaks.budgetStreak >= 3 && !state.streaks.todayOnTrack) {
            Row {
                Text(
                    "Bugün payını aştın; gün böyle biterse ${state.streaks.budgetStreak} günlük seri sıfırlanır.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CinchTheme.colors.caution,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}
