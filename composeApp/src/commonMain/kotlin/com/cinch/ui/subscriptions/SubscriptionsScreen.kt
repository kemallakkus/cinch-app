package com.cinch.ui.subscriptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cinch.shared.recurring.RecurringPayment
import com.cinch.ui.common.BackButton
import com.cinch.ui.common.CategoryBadge
import com.cinch.ui.common.display
import com.cinch.ui.common.shortLabel
import com.cinch.ui.theme.CinchTheme
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.abs

@Composable
fun SubscriptionsWrapper(onBack: () -> Unit, viewModel: SubscriptionsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is SubscriptionsEffect.Message -> launch { snackbarHostState.showSnackbar(effect.text) }
            }
        }
    }
    SubscriptionsScreen(state, viewModel::onEvent, onBack, snackbarHostState)
}

/** Kendiliğinden bulunan düzenli ödemeler; her biri sabit gidere çevrilebilir ya da "abonelik değil" denebilir. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    state: SubscriptionsState,
    onEvent: (SubscriptionsEvent) -> Unit,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Düzenli ödemeler") }, navigationIcon = { BackButton(onBack) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.items.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("🔁", style = MaterialTheme.typography.displaySmall)
                Text("Henüz düzenli ödeme yok", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Aynı şeyi üst üste 3 ay, ayın aşağı yukarı aynı gününde yazınca (\"199 netflix\") burada görünür. " +
                        "Sabit gidere çevirirsen her ay planlanır ve ödeme gününden önce hatırlatırız.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "summary") { Summary(state) }
                items(state.items, key = { it.key }) { RecurringCard(it, onEvent) }
            }
        }
    }
}

@Composable
private fun Summary(state: SubscriptionsState) {
    Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        state.monthlyTotal?.let {
            Text("Ayda ~${it.display()} düzenli ödeme", style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            "${state.items.size} tane kendiliğinden bulundu. Sabit gidere çevirirsen ay hesabında planlanır, ödeme gününden " +
                "önce hatırlatırız; adını yazınca o ayın ödemesi sayılır.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecurringCard(item: RecurringPayment, onEvent: (SubscriptionsEvent) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CategoryBadge(item.category)
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Ayın ~${item.dayOfMonth}. günü · ${item.months} aydır · son ${item.lastDate.shortLabel()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(item.lastAmount.display(), style = MaterialTheme.typography.titleMedium)
            }
            item.priceChange?.let { change ->
                val previous = item.previousAmount
                val percent = if (previous == null || previous.isZero) null else abs(change.minor) * 100 / previous.minor
                Text(
                    if (change.isNegative) "↓ Ucuzladı: ${previous?.display()} → ${item.lastAmount.display()}"
                    else "↑ Zamlandı: ${previous?.display()} → ${item.lastAmount.display()}" + (percent?.let { " (+%$it)" } ?: ""),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (change.isNegative) CinchTheme.colors.good else CinchTheme.colors.caution,
                )
            }
            if (item.isLate) {
                Text(
                    "Bu ay henüz görünmedi (${item.expectedNext.shortLabel()} bekleniyordu). İptal ettiysen güzel 👏",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { onEvent(SubscriptionsEvent.MakeFixed(item)) }) { Text("Sabit gider yap") }
                TextButton(onClick = { onEvent(SubscriptionsEvent.Dismiss(item)) }) { Text("Abonelik değil") }
            }
        }
    }
}
