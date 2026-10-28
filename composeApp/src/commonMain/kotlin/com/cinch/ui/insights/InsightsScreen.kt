package com.cinch.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cinch.shared.budget.SpendingBreakdown
import com.cinch.shared.budget.TopWord
import com.cinch.shared.domain.Category
import com.cinch.ui.common.AnimatedMoneyText
import com.cinch.ui.common.BackButton
import com.cinch.ui.common.DonutChart
import com.cinch.ui.common.InfoTip
import com.cinch.ui.common.MonthSwitcher
import com.cinch.ui.common.display
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    state: InsightsState,
    onEvent: (InsightsEvent) -> Unit,
    onOpenCategory: (Category, LocalDate) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nereye gitti?") },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        val breakdown = state.breakdown
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "month") {
                MonthSwitcher(
                    month = state.month,
                    canGoNext = state.canGoNext,
                    onPrevious = { onEvent(InsightsEvent.PreviousMonth) },
                    onNext = { onEvent(InsightsEvent.NextMonth) },
                )
            }

            if (state.isLoading || breakdown == null) {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                return@LazyColumn
            }

            item(key = "hero-${state.month}") { BreakdownHero(breakdown) }

            if (breakdown.categories.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "Bu ay henüz harcama kaydı yok. Kayıt girdikçe burada nereye gittiğini görürsün.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                    )
                }
                return@LazyColumn
            }

            item(key = "categoriesHeader") {
                Text(
                    "Kategoriler",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp, start = 4.dp),
                )
            }
            items(breakdown.categories, key = { "category-${it.category.name}" }) { spend ->
                CategorySpendRow(spend, onClick = { onOpenCategory(spend.category, state.month) })
            }

            if (breakdown.topWords.isNotEmpty()) {
                item(key = "wordsHeader") {
                    Row(Modifier.padding(top = 12.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("En çok neye gitti?", style = MaterialTheme.typography.titleMedium)
                        InfoTip(
                            title = "En çok neye gitti?",
                            text = "Kayıtlarına yazdığın kelimelere bakıyoruz: \"migros\", \"büfe\", \"taksi\". Bir kayıtta " +
                                "iki kelime varsa tutar ikisine de sayılır.",
                        )
                    }
                }
                item(key = "words") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                            .padding(vertical = 4.dp),
                    ) {
                        breakdown.topWords.forEachIndexed { index, word ->
                            key(word.word) { TopWordRow(rank = index + 1, word = word) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BreakdownHero(breakdown: SpendingBreakdown) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DonutChart(slices = breakdown.donutSlices(), modifier = Modifier.size(200.dp), thickness = 26.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedMoneyText(breakdown.total, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${breakdown.count} harcama",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                breakdown.comparisonSentence(),
                style = MaterialTheme.typography.bodyMedium,
                color = changeColor(breakdown.change),
                textAlign = TextAlign.Center,
            )
            InfoTip(
                title = "Nasıl hesaplıyoruz?",
                text = "Kira, fatura gibi sabit giderler burada yok; onlar \"Bu ay\" sekmesinde. Ay bitmeden geçen ayın " +
                    "tamamıyla değil, aynı günleriyle kıyaslıyoruz (ör. 1–20 Ekim ile 1–20 Eylül). Paylaştığın " +
                    "harcamalarda yalnız senin payın sayılır.",
            )
        }
        if (!breakdown.previousTotal.isZero) {
            Text(
                (if (breakdown.period.isCurrentMonth) "Geçen ay bu günlerde " else "Geçen ay ") +
                    breakdown.previousTotal.display(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TopWordRow(rank: Int, word: TopWord) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("$rank.", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(word.word, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("${word.count} kez", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(word.amount.display(), style = MaterialTheme.typography.titleSmall)
    }
}
