package com.cinch.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cinch.shared.domain.CardType
import com.cinch.ui.common.BackButton
import com.cinch.ui.common.DayOfMonthField
import com.cinch.ui.common.InfoTip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardEditScreen(
    state: CardEditState,
    onEvent: (CardEditEvent) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "Kart ekle" else "Kartı düzenle") },
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
            TypePicker(selected = state.type, enabled = !state.isLoading) { onEvent(CardEditEvent.TypePicked(it)) }
            Text(
                if (state.type == CardType.CREDIT) "Ekstresi, son ödeme günü ve faizi olan kart."
                else "Harcama doğrudan hesabından düşer: ekstre, borç ve faiz yok. Eksik harcamaları bulmak için de ekleyebilirsin.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                state = state.name,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading,
                label = { Text("Kartın adı") },
                placeholder = { Text("ör. QNB, Axess") },
                isError = state.nameError,
                supportingText = {
                    Text(
                        if (state.nameError) "Bir ad yaz"
                        else "Harcarken bu adı yazarsan (\"195 market qnb\") harcama bu karta yazılır",
                    )
                },
                inputTransformation = InputTransformation.maxLength(40),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                lineLimits = TextFieldLineLimits.SingleLine,
            )

            if (state.type == CardType.CREDIT) CreditFields(state, onEvent)

            Button(
                onClick = { onEvent(CardEditEvent.Save) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !state.isLoading && !state.isSaving,
            ) {
                Text("Kaydet")
            }

            if (!state.isNew) {
                TextButton(
                    onClick = { onEvent(CardEditEvent.DeleteRequested) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading && !state.isSaving,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Kartı sil")
                }
            }
        }
    }

    if (state.isConfirmingDelete) {
        AlertDialog(
            onDismissRequest = { onEvent(CardEditEvent.DeleteDismissed) },
            title = { Text("Kart silinsin mi?") },
            text = { Text("Bu kartla yaptığın harcamalar silinmez, nakit harcama olarak kalır.") },
            confirmButton = {
                TextButton(onClick = { onEvent(CardEditEvent.DeleteConfirmed) }) {
                    Text("Sil", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(CardEditEvent.DeleteDismissed) }) { Text("Vazgeç") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypePicker(selected: CardType, enabled: Boolean, onPick: (CardType) -> Unit) {
    val options = CardType.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, type ->
            SegmentedButton(
                selected = type == selected,
                onClick = { onPick(type) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(if (type == CardType.CREDIT) "Kredi kartı" else "Banka kartı")
            }
        }
    }
}

/** Yalnız kredi kartında: hesap kesim günü, ödenmemiş eski borç, faiz. */
@Composable
private fun CreditFields(state: CardEditState, onEvent: (CardEditEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            DayOfMonthField(
                label = "Hesap kesim günü (isteğe bağlı)",
                day = state.statementDay,
                onPick = { onEvent(CardEditEvent.StatementDayPicked(it)) },
                enabled = !state.isLoading,
                supportingText = "Bankanın o ayın harcamalarını topladığı gün. Bir gün önce hatırlatırız.",
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                state = state.carriedBalance,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading,
                label = { Text("Ödenmemiş eski borç (isteğe bağlı)") },
                placeholder = { Text("0") },
                suffix = { Text("₺") },
                trailingIcon = {
                    InfoTip(
                        title = "Ödenmemiş eski borç",
                        text = "Geçmiş ekstrelerin tamamını ödemediysen kalan kısım kartta borç olarak durur ve " +
                            "her ay faiz işler. Ekstrenin tamamını her ay ödüyorsan boş bırak.",
                    )
                },
                isError = state.balanceError,
                supportingText = {
                    Text(if (state.balanceError) "Geçerli bir tutar yaz (ör. 12.500)" else "Bu ayın harcamaları buraya girmez")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                lineLimits = TextFieldLineLimits.SingleLine,
            )

            OutlinedTextField(
                state = state.monthlyRate,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading,
                label = { Text("Aylık faiz oranı") },
                prefix = { Text("%") },
                trailingIcon = {
                    InfoTip(
                        title = "Aylık faiz oranı",
                        text = "Ekstrende \"akdi faiz\" diye yazar. Vergilerle birlikte genelde %4–5 civarıdır; " +
                            "emin değilsen olduğu gibi bırak.",
                    )
                },
                isError = state.rateError,
                supportingText = { if (state.rateError) Text("0 ile 100 arasında bir oran yaz (ör. 4,9)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                onKeyboardAction = { onEvent(CardEditEvent.Save) },
                lineLimits = TextFieldLineLimits.SingleLine,
            )
    }
}
