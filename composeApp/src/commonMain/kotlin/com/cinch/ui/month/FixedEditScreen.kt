package com.cinch.ui.month

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.input.then
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import com.cinch.shared.domain.FixedPlan
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cinch.shared.domain.Card
import com.cinch.shared.domain.FixedExpenseKind
import com.cinch.ui.common.BackButton
import com.cinch.ui.common.DayOfMonthField
import com.cinch.ui.common.emoji
import com.cinch.ui.common.label

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FixedEditScreen(
    state: FixedEditState,
    onEvent: (FixedEditEvent) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "Sabit gider ekle" else "Sabit gideri düzenle") },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Nasıl ödüyorsun?", style = MaterialTheme.typography.titleSmall)
            PlanPicker(selected = state.plan, enabled = !state.isLoading, onPick = { onEvent(FixedEditEvent.PlanPicked(it)) })
            Text(
                planHint(state.plan),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("Ne için ödüyorsun?", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FixedExpenseKind.entries.forEach { kind ->
                    FilterChip(
                        selected = kind == state.kind,
                        onClick = { onEvent(FixedEditEvent.KindPicked(kind)) },
                        label = { Text("${kind.emoji} ${kind.label}") },
                        enabled = !state.isLoading,
                    )
                }
            }

            OutlinedTextField(
                state = state.name,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading,
                label = { Text("Adı") },
                placeholder = { Text("ör. Kira, İnternet, Eminevim, Ablama borç") },
                isError = state.nameError,
                supportingText = { if (state.nameError) Text("Bir ad yaz") },
                inputTransformation = InputTransformation.maxLength(40),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                lineLimits = TextFieldLineLimits.SingleLine,
            )

            if (state.plan == FixedPlan.FLEXIBLE) {
                OutlinedTextField(
                    state = state.debt,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading,
                    label = { Text(if (state.isNew) "Kalan borç" else "Eklediğindeki kalan borç") },
                    placeholder = { Text("ör. 180.000") },
                    suffix = { Text("₺") },
                    isError = state.debtError,
                    supportingText = {
                        Text(
                            if (state.debtError) "Sıfırdan büyük bir tutar yaz"
                            else "Bundan sonra \"ödendi\" dediğin her tutar bu borçtan düşer",
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
            }

            OutlinedTextField(
                state = state.amount,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading,
                label = {
                    Text(
                        when (state.plan) {
                            FixedPlan.MONTHLY -> "Aylık tutar"
                            FixedPlan.INSTALLMENTS -> "Taksit tutarı"
                            FixedPlan.FLEXIBLE -> "Genelde ödediğin (isteğe bağlı)"
                        },
                    )
                },
                placeholder = { Text("ör. 15.000") },
                suffix = { Text("₺") },
                isError = state.amountError,
                supportingText = {
                    Text(
                        when {
                            state.amountError -> "Sıfırdan büyük bir tutar yaz"
                            state.plan == FixedPlan.FLEXIBLE ->
                                "Ay özetine ve bitiş tahminine bununla yazarız; boşsa son ödemelerinin ortalamasını kullanırız"
                            else -> "Fatura gibi değişiyorsa ortalamasını yaz; öderken gerçeğini düzeltirsin"
                        },
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                lineLimits = TextFieldLineLimits.SingleLine,
            )

            if (state.plan == FixedPlan.INSTALLMENTS) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        state = state.totalInstallments,
                        modifier = Modifier.weight(1f),
                        enabled = !state.isLoading,
                        label = { Text("Kaç taksit?") },
                        placeholder = { Text("ör. 6") },
                        isError = state.installmentsError,
                        inputTransformation = InputTransformation.maxLength(3).then(digitsOnly),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        lineLimits = TextFieldLineLimits.SingleLine,
                    )
                    OutlinedTextField(
                        state = state.paidBefore,
                        modifier = Modifier.weight(1f),
                        enabled = !state.isLoading,
                        label = { Text("Kaçı ödendi?") },
                        placeholder = { Text("0") },
                        isError = state.installmentsError,
                        inputTransformation = InputTransformation.maxLength(3).then(digitsOnly),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        lineLimits = TextFieldLineLimits.SingleLine,
                    )
                }
                Text(
                    if (state.installmentsError) "Taksit sayısını yaz; ödenen, toplamdan fazla olamaz"
                    else "Uygulamadan önce ödediklerini say. Son taksiti \"ödendi\" deyince kendiliğinden biter.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.installmentsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            DayOfMonthField(
                label = "Ödeme günü",
                day = state.dayOfMonth,
                onPick = { onEvent(FixedEditEvent.DayPicked(it)) },
                optional = false,
                enabled = !state.isLoading,
                isError = state.dayError,
                supportingText = if (state.dayError) "Ödeme gününü seç" else "Bir gün önce ve o gün akşam hatırlatırız",
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.cards.isNotEmpty()) {
                PaidWithPicker(cards = state.cards, selectedId = state.cardId, onPick = { onEvent(FixedEditEvent.CardPicked(it)) })
            }

            Button(
                onClick = { onEvent(FixedEditEvent.Save) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !state.isLoading && !state.isSaving,
            ) {
                Text("Kaydet")
            }

            if (!state.isNew) {
                TextButton(
                    onClick = { onEvent(FixedEditEvent.DeleteRequested) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading && !state.isSaving,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Sabit gideri sil")
                }
            }
        }
    }

    if (state.isConfirmingDelete) {
        AlertDialog(
            onDismissRequest = { onEvent(FixedEditEvent.DeleteDismissed) },
            title = { Text("Sabit gider silinsin mi?") },
            text = { Text("Bundan sonra hatırlatılmaz. Geçmiş ödemelerin kayıtları silinmez.") },
            confirmButton = {
                TextButton(onClick = { onEvent(FixedEditEvent.DeleteConfirmed) }) {
                    Text("Sil", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(FixedEditEvent.DeleteDismissed) }) { Text("Vazgeç") }
            },
        )
    }
}

/** Nasıl ödendiği: nakit/havale ya da bir kart. Kartla ödeniyorsa "ödendi" kaydı o karta yazılır. */
@Composable
private fun PaidWithPicker(cards: List<Card>, selectedId: Long?, onPick: (Long?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = cards.firstOrNull { it.id == selectedId }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Formun başındaki "Nasıl ödüyorsun?" ödeme şeklini soruyor; bu, ödeme aracı.
        Text("Neyle ödüyorsun?", style = MaterialTheme.typography.titleSmall)
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(if (selected == null) "💵 Nakit / havale ▾" else "💳 ${selected.name} ▾")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("💵  Nakit / havale") },
                    onClick = {
                        onPick(null)
                        expanded = false
                    },
                )
                cards.forEach { card ->
                    DropdownMenuItem(
                        text = { Text("💳  ${card.name}") },
                        onClick = {
                            onPick(card.id)
                            expanded = false
                        },
                    )
                }
            }
        }
        Text(
            "Kartla ödüyorsan, ödendi dediğinde kartın hesabına da yazılır.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val digitsOnly = InputTransformation {
    if (!asCharSequence().all { it.isDigit() }) revertAllChanges()
}

private fun planHint(plan: FixedPlan): String = when (plan) {
    FixedPlan.MONTHLY -> "Her ay ödenir, bitişi yok: kira, internet, abonelik."
    FixedPlan.INSTALLMENTS -> "Belli sayıda sabit taksit: kredi, telefon, birine olan borç. Son taksitte biter."
    FixedPlan.FLEXIBLE -> "Her ay farklı tutar ödersin (Eminevim gibi). Kalan borç sıfırlanınca biter."
}

@Composable
private fun PlanPicker(selected: FixedPlan, enabled: Boolean, onPick: (FixedPlan) -> Unit) {
    val options = FixedPlan.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, plan ->
            SegmentedButton(
                selected = plan == selected,
                onClick = { onPick(plan) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(
                    when (plan) {
                        FixedPlan.MONTHLY -> "Her ay"
                        FixedPlan.INSTALLMENTS -> "Taksitli"
                        FixedPlan.FLEXIBLE -> "Esnek borç"
                    },
                )
            }
        }
    }
}
