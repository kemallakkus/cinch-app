package com.cinch.ui.savings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cinch.shared.domain.GoalProgress
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.ui.common.BackButton
import com.cinch.ui.common.ConfettiBurst
import com.cinch.ui.common.display
import com.cinch.ui.common.shortLabel
import com.cinch.ui.theme.CinchTheme
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SavingsWrapper(onBack: () -> Unit, viewModel: SavingsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confetti by remember { mutableIntStateOf(0) }
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is SavingsEffect.GoalReached -> {
                    confetti++
                    launch { snackbarHostState.showSnackbar("🌟 ${effect.name} hedefine ulaştın!") }
                }
            }
        }
    }
    SavingsScreen(state, viewModel::onEvent, onBack, snackbarHostState, confetti)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsScreen(
    state: SavingsState,
    onEvent: (SavingsEvent) -> Unit,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    confettiTrigger: Int = 0,
) {
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Kumbara") },
                    navigationIcon = { BackButton(onBack) })
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(onClick = { onEvent(SavingsEvent.AddGoalClicked) }) {
                    Text(
                        "Hedef ekle"
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "intro") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        if (state.total > 0) {
                            Text(
                                "Toplam ${
                                    Money(
                                        state.total,
                                        Currency.TRY
                                    ).display()
                                } biriktirdin 🐷", style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Text(
                            "Haftadan artanı ya da istediğin tutarı bir hedefe ayır. Para bankadan çıkmaz; o hedef için " +
                                    "ayrılmış sayılır ve bu ay elinde kalandan düşer.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!state.isLoading && state.goals.isEmpty()) {
                    item(key = "empty") {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("🐷", style = MaterialTheme.typography.displayMedium)
                            Text("İlk hedefini koy", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Tatil, telefon, acil durum fonu… Hedefin olunca artan para daha anlamlı.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                items(state.goals, key = { it.goal.id }) { GoalCard(it, onEvent) }
            }
        }
        ConfettiBurst(trigger = confettiTrigger, originY = 0.4f)
    }
    Dialogs(state, onEvent)
}

@Composable
private fun GoalCard(progress: GoalProgress, onEvent: (SavingsEvent) -> Unit) {
    val goal = progress.goal
    val fraction by animateFloatAsState(progress.fraction, label = "goal")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(goal.emoji, style = MaterialTheme.typography.headlineSmall)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    goal.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (progress.isReached) "🌟 Hedefe ulaştın!"
                    else "${progress.saved.display()} / ${goal.target.display()} · ${progress.remaining.display()} kaldı",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (progress.isReached) CinchTheme.colors.good else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "%${(progress.fraction * 100).toInt()}",
                style = MaterialTheme.typography.titleSmall
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(CinchTheme.colors.good),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onEvent(SavingsEvent.AddMoneyClicked(goal)) }) { Text("+ Ekle") }
            if (!progress.saved.isZero) OutlinedButton(onClick = {
                onEvent(
                    SavingsEvent.WithdrawClicked(
                        goal
                    )
                )
            }) { Text("Çek") }
            TextButton(onClick = { onEvent(SavingsEvent.EditGoalClicked(goal)) }) { Text("Düzenle") }
        }
        // Son hareketler; yanlışsa ✕ ile silinir.
        progress.deposits.takeLast(3).reversed().forEach { deposit ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${deposit.date.shortLabel()} · ${deposit.note.ifBlank { if (deposit.amount.isNegative) "Çekildi" else "Eklendi" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    (if (deposit.amount.isNegative) "−" else "+") + deposit.amount.absolute.display(),
                    style = MaterialTheme.typography.labelLarge,
                )
                TextButton(onClick = { onEvent(SavingsEvent.DepositDeleted(deposit)) }) { Text("✕") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Dialogs(state: SavingsState, onEvent: (SavingsEvent) -> Unit) {
    when (val dialog = state.dialog) {
        is SavingsDialog.EditGoal -> AlertDialog(
            onDismissRequest = { onEvent(SavingsEvent.DialogDismissed) },
            title = { Text(if (dialog.goal == null) "Yeni hedef" else "Hedefi düzenle") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SavingsState.EMOJIS.forEach { emoji ->
                            FilterChip(
                                selected = emoji == state.emoji,
                                onClick = { onEvent(SavingsEvent.EmojiPicked(emoji)) },
                                label = { Text(emoji) },
                            )
                        }
                    }
                    OutlinedTextField(
                        state = state.name,
                        label = { Text("Ne için?") },
                        placeholder = { Text("ör. Tatil") },
                        isError = state.nameError,
                        lineLimits = TextFieldLineLimits.SingleLine,
                    )
                    OutlinedTextField(
                        state = state.target,
                        label = { Text("Hedef tutar") },
                        suffix = { Text("₺") },
                        isError = state.amountError,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        lineLimits = TextFieldLineLimits.SingleLine,
                    )
                    if (dialog.goal != null) {
                        TextButton(onClick = { onEvent(SavingsEvent.DeleteGoalClicked(dialog.goal)) }) {
                            Text("Hedefi sil", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { onEvent(SavingsEvent.GoalSaved) }) { Text("Kaydet") } },
            dismissButton = {
                TextButton(onClick = { onEvent(SavingsEvent.DialogDismissed) }) {
                    Text(
                        "Vazgeç"
                    )
                }
            },
        )

        is SavingsDialog.Move -> AlertDialog(
            onDismissRequest = { onEvent(SavingsEvent.DialogDismissed) },
            title = { Text(if (dialog.withdraw) "${dialog.goal.emoji} ${dialog.goal.name}: çek" else "${dialog.goal.emoji} ${dialog.goal.name}: ekle") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        state = state.amount,
                        label = { Text("Tutar") },
                        suffix = { Text("₺") },
                        isError = state.amountError,
                        supportingText = {
                            if (state.amountError) Text(if (dialog.withdraw) "Kumbaradakinden fazla olamaz" else "Sıfırdan büyük bir tutar yaz")
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        onKeyboardAction = { onEvent(SavingsEvent.MoveConfirmed) },
                        lineLimits = TextFieldLineLimits.SingleLine,
                    )
                    val leftover = state.leftover
                    if (!dialog.withdraw && leftover != null) {
                        AssistChip(
                            onClick = { onEvent(SavingsEvent.UseLeftover) },
                            label = { Text("Geçen haftanın artanı: ${leftover.amount.display()}") },
                        )
                    }
                }
            },
            confirmButton = { Button(onClick = { onEvent(SavingsEvent.MoveConfirmed) }) { Text(if (dialog.withdraw) "Çek" else "Ekle") } },
            dismissButton = {
                TextButton(onClick = { onEvent(SavingsEvent.DialogDismissed) }) {
                    Text(
                        "Vazgeç"
                    )
                }
            },
        )

        is SavingsDialog.DeleteGoal -> AlertDialog(
            onDismissRequest = { onEvent(SavingsEvent.DialogDismissed) },
            title = { Text("${dialog.goal.name} silinsin mi?") },
            text = { Text("Hedef ve içindeki hareketler silinir; ayrılan para bu ay elinde kalana geri döner.") },
            confirmButton = {
                TextButton(onClick = { onEvent(SavingsEvent.DeleteConfirmed) }) {
                    Text(
                        "Sil",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(SavingsEvent.DialogDismissed) }) {
                    Text(
                        "Vazgeç"
                    )
                }
            },
        )

        null -> Unit
    }
}
