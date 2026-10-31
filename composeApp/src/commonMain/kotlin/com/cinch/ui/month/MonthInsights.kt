package com.cinch.ui.month

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cinch.shared.budget.MonthRecap
import com.cinch.shared.budget.MonthRecaps
import com.cinch.shared.budget.ObserveMonthRecap
import com.cinch.shared.money.Money
import com.cinch.shared.recurring.ObserveRecurringPayments
import com.cinch.shared.time.LocalClock
import com.cinch.ui.common.display
import com.cinch.ui.common.monthName
import com.cinch.ui.recap.changeLine
import com.cinch.ui.theme.CinchTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.koin.compose.viewmodel.koinViewModel

/**
 * @param recap geçen ayın özeti; [recapFresh] ayın ilk günlerinde öne çıkarılır.
 * @param recurringCount kendiliğinden bulunan düzenli ödeme; [recurringTotal] aylık toplamı.
 * @param priceChanges zamlanan düzenli ödeme sayısı.
 */
@Immutable
data class MonthInsightsState(
    val recap: MonthRecap? = null,
    val recapFresh: Boolean = false,
    val recurringCount: Int = 0,
    val recurringTotal: Money? = null,
    val priceChanges: Int = 0,
)

/** "Bu ay" sekmesindeki iki kısayol: geçen ayın özeti ve düzenli ödemeler. */
class MonthInsightsViewModel(recaps: ObserveMonthRecap, recurring: ObserveRecurringPayments, clock: LocalClock) : ViewModel() {

    private val today = clock.today()

    val state: StateFlow<MonthInsightsState> = combine(recaps(MonthRecaps.previousMonth(today)), recurring()) { recap, items ->
        val currency = items.firstOrNull()?.lastAmount?.currency
        MonthInsightsState(
            recap = recap.takeIf { !it.isEmpty },
            recapFresh = today.day <= FRESH_DAYS,
            recurringCount = items.size,
            recurringTotal = currency?.let { c -> Money.sum(items.filter { it.lastAmount.currency == c }.map { it.lastAmount }, c) },
            priceChanges = items.count { it.priceChange?.isNegative == false },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthInsightsState())

    private companion object {
        /** Ayın ilk bu kadar gününde geçen ayın özeti kartla öne çıkar; sonra küçük bir bağlantı. */
        const val FRESH_DAYS = 10
    }
}

@Composable
fun MonthInsightsWrapper(
    onOpenRecap: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    viewModel: MonthInsightsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MonthInsights(state, onOpenRecap, onOpenSubscriptions)
}

@Composable
fun MonthInsights(state: MonthInsightsState, onOpenRecap: () -> Unit, onOpenSubscriptions: () -> Unit) {
    val recap = state.recap
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (recap != null && state.recapFresh) {
            Teaser(
                emoji = "✨",
                title = "${recap.month.monthName()} özetin hazır",
                body = "${recap.spent.display()} harcadın · ${changeLine(recap)}",
                highlighted = true,
                onClick = onOpenRecap,
            )
        }
        if (state.recurringCount > 0) {
            Teaser(
                emoji = "🔁",
                title = "Düzenli ödemeler" + (state.recurringTotal?.let { " · ayda ~${it.display()}" } ?: ""),
                body = if (state.priceChanges > 0) "${state.priceChanges} tanesine zam geldi" else "${state.recurringCount} tane bulundu; sabit gidere çevirebilirsin",
                warning = state.priceChanges > 0,
                onClick = onOpenSubscriptions,
            )
        }
        if (recap != null && !state.recapFresh) {
            TextButton(onClick = onOpenRecap) { Text("📊 ${recap.month.monthName()} özetine bak") }
        }
    }
}

@Composable
private fun Teaser(
    emoji: String,
    title: String,
    body: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
    warning: Boolean = false,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(emoji, style = MaterialTheme.typography.headlineSmall)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (warning) CinchTheme.colors.caution else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("›", style = MaterialTheme.typography.titleLarge)
        }
    }
}
