package com.cinch.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cinch.shared.budget.BudgetStatus
import com.cinch.ui.common.AnimatedMoneyText
import com.cinch.ui.common.ConfettiBurst
import com.cinch.ui.common.ExpenseRow
import com.cinch.ui.common.HeroCard
import com.cinch.ui.common.InfoTip
import com.cinch.ui.common.display
import com.cinch.ui.common.initial
import com.cinch.ui.common.longDayLabel
import com.cinch.ui.theme.CinchTheme

/**
 * @param confettiTrigger her arttığında ekranda küçük bir konfeti patlar (kayıt payın içinde kaldıysa).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeState,
    onEvent: (HomeEvent) -> Unit,
    snackbarHostState: SnackbarHostState,
    confettiTrigger: Int = 0,
    onOpenExpense: (Long) -> Unit = {},
    onOpenHistory: () -> Unit = {},
    prompt: @Composable () -> Unit = {},
) {
    val cardNames = remember(state.cards) { state.cards.associate { it.id to it.name } }
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Cinch", style = MaterialTheme.typography.titleLarge)
                            Text(
                                state.today.longDayLabel(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
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
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "status") {
                    // İzin kartı boşsa (izin var ya da ertelendi) yer kaplamaz.
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.status?.let { TodayHero(it, state.weekDays) }
                        prompt()
                    }
                }

                item(key = "header") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, start = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (state.weekExpenses.isEmpty()) "Bu hafta" else "Bu hafta · ${state.weekExpenses.size} harcama",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        // Geçmiş haftalar, arama ve sabit gider ödemeleri tüm geçmişte.
                        TextButton(onClick = onOpenHistory) { Text("Tüm geçmiş") }
                    }
                }

                if (state.weekExpenses.isEmpty()) {
                    item(key = "empty") { EmptyWeek() }
                }

                items(state.weekExpenses, key = { it.id }) { expense ->
                    ExpenseRow(
                        expense = expense,
                        today = state.today,
                        cardName = expense.cardId?.let { cardNames[it] },
                        onClick = { onOpenExpense(expense.id) },
                        onDelete = { onEvent(HomeEvent.DeleteExpense(expense)) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
        ConfettiBurst(trigger = confettiTrigger)
    }
}

@Composable
private fun TodayHero(status: BudgetStatus, days: List<DaySpend>) {
    val overToday = status.todayRemaining.isNegative
    val colors = CinchTheme.colors
    val content = if (overToday || status.isOverLimit) colors.onHeroOver else colors.onHero

    HeroCard(brush = if (overToday || status.isOverLimit) colors.heroOver else colors.hero) {
        Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (overToday) "Bugünkü payını aştın" else "Bugün harcayabileceğin",
                    style = MaterialTheme.typography.labelLarge,
                    color = content,
                )
                InfoTip(
                    title = "Günlük pay",
                    text = "Haftalık bütçende kalan parayı, haftanın kalan günlerine eşit bölüyoruz. " +
                        "Bugün az harcarsan yarınki payın büyür; çok harcarsan küçülür.",
                )
            }
            AnimatedMoneyText(
                money = status.todayRemaining.absolute,
                style = MaterialTheme.typography.displayMedium,
                color = content,
            )
            Text(moodLine(status), style = MaterialTheme.typography.bodyMedium, color = content)

            Spacer(Modifier.height(16.dp))
            WeekStrip(days = days, content = content)
            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Bu hafta ${status.weekSpent.display()} / ${status.weeklyLimit.display()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = content,
                )
                Text(
                    if (status.isOverLimit) "${status.weekRemaining.absolute.display()} aşıldı"
                    else "${status.daysRemaining} gün kaldı",
                    style = MaterialTheme.typography.labelLarge,
                    color = content,
                )
            }
            if (!status.isOverLimit && status.daysRemaining > 1) {
                Text(
                    "Kalan günler için günde ${status.dailyAllowance.display()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = content,
                )
            }
        }
    }
}

/** Kısa, cesaretlendirici bir cümle: suçlamaz, yol gösterir. */
private fun moodLine(status: BudgetStatus): String = when {
    status.isOverLimit -> "Bu hafta sınırı geçtin. Sorun değil, kalan günleri hafif tutalım 💪"
    status.todayRemaining.isNegative -> "Bugün biraz fazla oldu; yarın payın kendini toplar 🌤️"
    status.todaySpent.isZero -> "Bugün henüz hiç harcamadın 🌱"
    else -> "Bugün ${status.todaySpent.display()} harcadın, iyi gidiyorsun ✨"
}

/**
 * Haftanın 7 günü için küçük çubuklar: yükseklik, o günün harcamasının adil paya (haftalık sınır / 7)
 * oranı. Nane = payın içinde, bal = biraz fazla, kırmızı = çok fazla; gelecek günler boş.
 */
@Composable
private fun WeekStrip(days: List<DaySpend>, content: Color) {
    val good = CinchTheme.colors.good
    val caution = CinchTheme.colors.caution
    val over = MaterialTheme.colorScheme.error
    Row(
        modifier = Modifier.fillMaxWidth().height(76.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        days.forEach { day ->
            val target = if (day.isFuture) 0f else (day.ratio / MAX_RATIO).coerceIn(MIN_BAR, 1f)
            val fraction by animateFloatAsState(target, label = "dayBar")
            val barColor = when {
                day.ratio <= 1f -> good
                day.ratio <= MAX_RATIO -> caution
                else -> over
            }
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .width(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(content.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction)
                            .clip(RoundedCornerShape(7.dp))
                            .background(barColor),
                    )
                }
                Text(
                    day.date.dayOfWeek.initial,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (day.isToday) FontWeight.ExtraBold else FontWeight.Normal,
                    color = content.copy(alpha = if (day.isFuture) 0.5f else 1f),
                )
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (day.isToday) content else Color.Transparent),
                )
            }
        }
    }
}

private const val MAX_RATIO = 1.5f
private const val MIN_BAR = 0.06f

@Composable
private fun EmptyWeek() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("🧾", style = MaterialTheme.typography.displaySmall)
        Text("Bu hafta henüz kayıt yok", style = MaterialTheme.typography.titleMedium)
        Text(
            "Aşağıya \"195 market\" ya da \"dün 80 taksi\" yazman yeter.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
