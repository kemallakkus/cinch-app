package com.cinch.ui.cashflow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cinch.shared.budget.CashDay
import com.cinch.shared.budget.CashEvent
import com.cinch.shared.budget.CashEventKind
import com.cinch.shared.budget.CashForecast
import com.cinch.shared.budget.daysAgo
import com.cinch.ui.common.BackButton
import com.cinch.ui.common.HeroCard
import com.cinch.ui.common.InfoTip
import com.cinch.ui.common.display
import com.cinch.ui.common.relativeLabel
import com.cinch.ui.common.shortLabel
import com.cinch.ui.theme.CinchTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashCalendarScreen(state: CashCalendarState, onBack: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Para takvimi") }, navigationIcon = { BackButton(onBack) }) },
    ) { padding ->
        val forecast = state.forecast
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "balance") { BalanceInput(state) }
            if (state.isLoading || forecast == null) {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            } else {
                item(key = "verdict") { Verdict(forecast) }
                item(key = "summary") { Summary(forecast) }
                items(forecast.eventDays, key = { it.date.toString() }) { day -> DayBlock(day, state) }
            }
        }
    }
}

@Composable
private fun BalanceInput(state: CashCalendarState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            state = state.balance,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Hesabında şu an ne kadar var?") },
            placeholder = { Text("ör. 12.400") },
            suffix = { Text("₺") },
            supportingText = {
                val ago = state.balanceDate?.daysAgo(state.today)
                Text(
                    when {
                        ago == null -> "Banka uygulamandaki kullanılabilir bakiye. Yazınca maaşa kadar yetip yetmediğini gösteririz."
                        ago == 0 -> "Bugün girdin"
                        else -> "$ago gün önce girdin; arada harcadıysan güncelle"
                    },
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            lineLimits = TextFieldLineLimits.SingleLine,
        )
    }
}

/** Ana cevap: maaşa kadar yetiyor mu, yetmiyorsa ne zaman ve ne yapılabilir. */
@Composable
private fun Verdict(forecast: CashForecast) {
    val colors = CinchTheme.colors
    val shortfall = forecast.firstShortfall
    val payday = forecast.nextPayday
    val negative = shortfall != null && (payday == null || shortfall < payday)
    val content = if (negative) colors.onHeroOver else colors.onHero
    HeroCard(brush = if (negative) colors.heroOver else colors.hero) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Maaşa kadar yeter mi?", style = MaterialTheme.typography.labelLarge, color = content)
                InfoTip(
                    title = "Nasıl hesaplıyoruz?",
                    text = "Yazdığın bakiyeye maaş günlerini ekliyor; sabit giderleri ödeme günlerinde, kart ödemelerini okunan " +
                        "ekstrenin son ödeme gününde düşüyoruz. Her gün için haftalık sınırının yedide biri kadar harcayacağını " +
                        "varsayıyoruz. Kartla ödenen sabit giderler kart ekstresinin içinde olduğu için ayrıca düşülmez.",
                )
            }
            when {
                forecast.startBalance == null -> Text(
                    "Hesabındaki parayı yaz; günü gününe kalanı gösterelim.",
                    style = MaterialTheme.typography.titleMedium,
                    color = content,
                )
                negative -> {
                    Text("⚠️ ${shortfall?.shortLabel()} günü eksiye düşüyorsun", style = MaterialTheme.typography.headlineSmall, color = content)
                    val cut = forecast.dailyCutNeeded
                    Text(
                        when {
                            cut == null -> ""
                            cut <= forecast.dailySpending ->
                                "Günde ~${cut.display()} az harcarsan ${payday?.let { "maaşa (${it.shortLabel()})" } ?: "ay sonuna"} kadar yetiyor."
                            else -> "Harcamayı tamamen kessen bile yetmiyor; bir ödemeyi ertelemeyi ya da bölmeyi düşün."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = content,
                    )
                }
                payday != null -> {
                    Text("Maaşa (${payday.shortLabel()}) kadar yetiyor 👍", style = MaterialTheme.typography.headlineSmall, color = content)
                    forecast.balanceBeforePayday?.let {
                        Text("Maaştan önceki gün ~${it.display()} kalır.", style = MaterialTheme.typography.bodyMedium, color = content)
                    }
                }
                else -> Text("Önümüzdeki 5 hafta eksiye düşmüyorsun 👍", style = MaterialTheme.typography.headlineSmall, color = content)
            }
        }
    }
}

@Composable
private fun Summary(forecast: CashForecast) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
        Text(
            "Önümüzdeki 5 hafta: +${forecast.totalIn.display()} giriş · −${forecast.totalOut.display()} ödeme · " +
                "günlük harcama payı ~${forecast.dailySpending.display()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (forecast.creditCardsWithoutStatement > 0) {
            Text(
                "${forecast.creditCardsWithoutStatement} kredi kartının güncel ekstresi okunmadı; Kartlar'dan banka ekranını " +
                    "okutursan son ödemeleri de takvime girer.",
                style = MaterialTheme.typography.bodySmall,
                color = CinchTheme.colors.caution,
            )
        }
    }
}

@Composable
private fun DayBlock(day: CashDay, state: CashCalendarState) {
    val balance = day.balance
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(day.date.relativeLabel(state.today), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (balance != null) {
                Text(
                    "gün sonu ${balance.display()}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (balance.isNegative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (day.events.isEmpty()) {
            Text(
                "Bugün için harcama payı ~${day.spending.display()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        day.events.forEach { EventLine(it) }
    }
}

@Composable
private fun EventLine(event: CashEvent) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            when (event.kind) {
                CashEventKind.INCOME -> "💰"
                CashEventKind.FIXED -> "📌"
                CashEventKind.CARD -> "💳"
            },
        )
        Text(event.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(
            (if (event.amount.isIncome) "+" else "−") + event.amount.absolute.display(),
            style = MaterialTheme.typography.titleSmall,
            color = if (event.amount.isIncome) CinchTheme.colors.good else MaterialTheme.colorScheme.onSurface,
        )
    }
}
