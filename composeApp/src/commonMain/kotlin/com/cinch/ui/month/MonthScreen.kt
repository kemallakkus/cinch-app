package com.cinch.ui.month

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.cinch.shared.budget.MonthSummary
import com.cinch.shared.budget.Payday
import com.cinch.shared.domain.Income
import com.cinch.ui.common.AnimatedMoneyText
import com.cinch.ui.common.HeroCard
import com.cinch.ui.common.InfoTip
import com.cinch.ui.common.dayOfMonthLabel
import com.cinch.ui.common.display
import com.cinch.ui.common.monthYearLabel
import com.cinch.ui.theme.CinchTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthScreen(state: MonthState, onAddIncome: () -> Unit, onOpenIncome: (Long) -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(state.today.monthYearLabel()) }) },
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
            item(key = "summary") {
                val summary = state.summary
                if (summary == null || !summary.hasIncome) EmptySummary(onAddIncome) else SummaryCard(summary)
            }
            item(key = "incomesHeader") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Gelirlerin", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onAddIncome) { Text("Gelir ekle") }
                }
            }
            if (state.incomes.isEmpty()) {
                item(key = "incomesEmpty") {
                    Text(
                        "Maaş, ek iş, kira geliri gibi her ay gelen paralarını ekle.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.incomes, key = { it.id }) { income ->
                IncomeRow(income, onClick = { onOpenIncome(income.id) })
            }
        }
    }
}

@Composable
private fun SummaryCard(summary: MonthSummary) {
    val negative = summary.remaining.isNegative
    val colors = CinchTheme.colors
    val content = if (negative) colors.onHeroOver else colors.onHero
    HeroCard(brush = if (negative) colors.heroOver else colors.hero) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (negative) "Bu ay gelirini aştın" else "Bu ay elinde kalan",
                    style = MaterialTheme.typography.labelLarge,
                    color = content,
                )
                InfoTip(
                    title = "Bu ay kalan",
                    text = "Bu ayki gelirlerinin toplamından bu ay harcadıklarını çıkarıyoruz. Arkadaşınla " +
                        "paylaştığın harcamalarda yalnız senin payın sayılır.",
                )
            }
            AnimatedMoneyText(summary.remaining.absolute, style = MaterialTheme.typography.displayMedium, color = content)
            summary.nextPayday?.let { Text(paydayLabel(it), style = MaterialTheme.typography.bodyMedium, color = content) }

            Spacer(Modifier.height(16.dp))
            val ratio = if (summary.income.isZero) 1f
            else (summary.spent.minor.toFloat() / summary.income.minor.toFloat()).coerceIn(0f, 1f)
            val animated by animateFloatAsState(ratio, label = "monthSpent")
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(content.copy(alpha = 0.12f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(animated)
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (negative) MaterialTheme.colorScheme.error else colors.good),
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Harcadın ${summary.spent.display()}", style = MaterialTheme.typography.bodyMedium, color = content)
                Text("Gelir ${summary.income.display()}", style = MaterialTheme.typography.bodyMedium, color = content)
            }
        }
    }
}

private fun paydayLabel(payday: Payday): String = when (payday.daysAway) {
    0 -> "${payday.income.name} bugün yatıyor 🎉"
    1 -> "${payday.income.name} yarın yatıyor"
    else -> "${payday.income.name} yatmasına ${payday.daysAway} gün var"
}

@Composable
private fun EmptySummary(onAddIncome: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🐷", style = MaterialTheme.typography.displaySmall)
            Text("Ay sonunda ne kalacak?", style = MaterialTheme.typography.titleLarge)
            Text(
                "Aylık gelirini eklersen, bu ay harcadıklarını gelirinden düşüp elinde ne kaldığını gösteririz.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onAddIncome) { Text("Gelir ekle") }
        }
    }
}

@Composable
private fun IncomeRow(income: Income, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        leadingContent = {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) { Text("💰", style = MaterialTheme.typography.titleMedium) }
        },
        headlineContent = { Text(income.name) },
        supportingContent = { Text(income.dayOfMonth?.let { dayOfMonthLabel(it) } ?: "Günü belli değil") },
        trailingContent = {
            // Eski sürümden taşınan "Maaş" tutarsız gelir; kullanıcıdan tutarı istenir.
            if (income.amount.isZero) {
                Text("Tutarı gir", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleSmall)
            } else {
                Text(income.amount.display(), style = MaterialTheme.typography.titleSmall)
            }
        },
    )
}
