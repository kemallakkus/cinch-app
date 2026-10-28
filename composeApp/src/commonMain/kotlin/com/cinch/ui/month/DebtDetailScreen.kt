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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cinch.shared.budget.DebtProgress
import com.cinch.shared.domain.Expense
import com.cinch.shared.domain.FixedExpense
import com.cinch.shared.domain.FixedPlan
import com.cinch.ui.common.AnimatedMoneyText
import com.cinch.ui.common.BackButton
import com.cinch.ui.common.ConfettiBurst
import com.cinch.ui.common.HeroCard
import com.cinch.ui.common.display
import com.cinch.ui.common.emoji
import com.cinch.ui.common.monthYearLabel
import com.cinch.ui.common.shortLabel
import com.cinch.ui.theme.CinchTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtDetailScreen(
    state: DebtDetailState,
    onEvent: (DebtDetailEvent) -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    snackbarHostState: SnackbarHostState,
    confettiTrigger: Int = 0,
) {
    val fixed = state.fixed
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(fixed?.let { "${it.kind.emoji} ${it.name}" } ?: "") },
                    navigationIcon = { BackButton(onBack) },
                    actions = { if (fixed != null) TextButton(onClick = onEdit) { Text("Düzenle") } },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            val progress = state.progress
            if (state.isLoading || fixed == null || progress == null) {
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
                item(key = "hero") { ProgressHero(fixed, progress) }
                if (!progress.isFinished) {
                    item(key = "add") {
                        Button(
                            onClick = { onEvent(DebtDetailEvent.AddPaymentClicked) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) { Text("Ödeme ekle") }
                    }
                }
                item(key = "historyHeader") {
                    Text(
                        if (state.payments.isEmpty()) "Henüz ödeme işaretlenmedi" else "Ödemeler",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp, start = 4.dp),
                    )
                }
                items(state.payments, key = { it.id }) { payment ->
                    PaymentRow(payment, onClick = { onEvent(DebtDetailEvent.PaymentClicked(payment)) })
                }
            }
        }
        ConfettiBurst(trigger = confettiTrigger, originY = 0.45f)
    }

    when (val dialog = state.dialog) {
        DebtDialog.AddPayment -> AlertDialog(
            onDismissRequest = { onEvent(DebtDetailEvent.DialogDismissed) },
            title = { Text("Ne kadar ödedin?") },
            text = {
                OutlinedTextField(
                    state = state.payAmount,
                    label = { Text("Ödenen tutar") },
                    suffix = { Text("₺") },
                    isError = state.payAmountError,
                    supportingText = { if (state.payAmountError) Text("Sıfırdan büyük bir tutar yaz") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    onKeyboardAction = { onEvent(DebtDetailEvent.PayConfirmed) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
            },
            confirmButton = { Button(onClick = { onEvent(DebtDetailEvent.PayConfirmed) }) { Text("Ekle") } },
            dismissButton = { TextButton(onClick = { onEvent(DebtDetailEvent.DialogDismissed) }) { Text("Vazgeç") } },
        )
        is DebtDialog.DeletePayment -> AlertDialog(
            onDismissRequest = { onEvent(DebtDetailEvent.DialogDismissed) },
            title = { Text("Bu ödeme silinsin mi?") },
            text = {
                Text("${dialog.payment.date.shortLabel()} ${dialog.payment.date.year} · ${dialog.payment.grossAmount.display()} kaydı silinir; kalan borç geri artar.")
            },
            confirmButton = {
                TextButton(onClick = { onEvent(DebtDetailEvent.DeleteConfirmed) }) {
                    Text("Sil", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { onEvent(DebtDetailEvent.DialogDismissed) }) { Text("Vazgeç") } },
        )
        null -> Unit
    }
}

@Composable
private fun ProgressHero(fixed: FixedExpense, progress: DebtProgress) {
    val colors = CinchTheme.colors
    val content = colors.onHero
    HeroCard(brush = colors.hero) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (progress.isFinished) {
                Text("🎉 Bitti!", style = MaterialTheme.typography.displaySmall, color = content)
                Text(
                    "Toplam ${progress.paidTotal.display()} ödedin" +
                        (progress.endDate?.let { " · son ödeme ${it.shortLabel()} ${it.year}" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = content,
                )
            } else {
                when (fixed.plan) {
                    FixedPlan.INSTALLMENTS -> {
                        Text("Ödenen taksit", style = MaterialTheme.typography.labelLarge, color = content)
                        Text(
                            "${progress.paidCount} / ${progress.totalCount}",
                            style = MaterialTheme.typography.displayMedium,
                            color = content,
                        )
                        Text(
                            "${progress.remainingCount} taksit kaldı · kalan ${progress.remainingDebt.display()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = content,
                        )
                    }
                    else -> {
                        Text("Kalan borç", style = MaterialTheme.typography.labelLarge, color = content)
                        AnimatedMoneyText(progress.remainingDebt, style = MaterialTheme.typography.displayMedium, color = content)
                        Text(
                            "Şimdiye kadar ${progress.paidTotal.display()} ödedin",
                            style = MaterialTheme.typography.bodyMedium,
                            color = content,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                ProgressBar(progress.fraction, track = content.copy(alpha = 0.12f), fill = colors.good)
                Spacer(Modifier.height(8.dp))
                Text(estimateLine(fixed, progress), style = MaterialTheme.typography.bodyMedium, color = content)
            }
        }
    }
}

/** "Son taksit Mart 2026" / "Ayda ~25.000 ₺ ile ~6 ayda biter (Nisan 2026)". */
private fun estimateLine(fixed: FixedExpense, progress: DebtProgress): String {
    val end = progress.endDate?.monthYearLabel()
    return when {
        fixed.plan == FixedPlan.INSTALLMENTS -> end?.let { "Son taksit: $it" } ?: ""
        progress.monthsLeft == null ->
            "Bitiş tahmini için formdan \"genelde ödediğin\" tutarı gir ya da birkaç ödeme işaretle."
        else -> "Ayda ~${progress.estimateBase?.display()} ile ~${progress.monthsLeft} ayda biter" +
            (end?.let { " ($it)" } ?: "")
    }
}

@Composable
private fun ProgressBar(fraction: Float, track: Color, fill: Color) {
    val animated by animateFloatAsState(fraction, label = "debtProgress")
    Box(
        Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(track),
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(fill),
        )
    }
}

@Composable
private fun PaymentRow(payment: Expense, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${payment.date.shortLabel()} ${payment.date.year}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(payment.grossAmount.display(), style = MaterialTheme.typography.titleSmall)
    }
}
