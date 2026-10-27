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
                placeholder = { Text("ör. Kira, İnternet, Netflix") },
                isError = state.nameError,
                supportingText = { if (state.nameError) Text("Bir ad yaz") },
                inputTransformation = InputTransformation.maxLength(40),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                lineLimits = TextFieldLineLimits.SingleLine,
            )

            OutlinedTextField(
                state = state.amount,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading,
                label = { Text("Aylık tutar") },
                placeholder = { Text("ör. 15.000") },
                suffix = { Text("₺") },
                isError = state.amountError,
                supportingText = {
                    Text(
                        if (state.amountError) "Sıfırdan büyük bir tutar yaz"
                        else "Fatura gibi değişiyorsa ortalamasını yaz; öderken gerçeğini düzeltirsin",
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                lineLimits = TextFieldLineLimits.SingleLine,
            )

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
        Text("Nasıl ödüyorsun?", style = MaterialTheme.typography.titleSmall)
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
