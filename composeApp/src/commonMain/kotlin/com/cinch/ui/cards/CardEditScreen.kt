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
import androidx.compose.foundation.text.input.then
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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

private val digitsOnly = InputTransformation {
    if (!asCharSequence().all { it.isDigit() }) revertAllChanges()
}

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
                navigationIcon = { TextButton(onClick = onBack) { Text("Geri") } },
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
            OutlinedTextField(
                state = state.name,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading,
                label = { Text("Kart adı") },
                placeholder = { Text("ör. QNB, Akbank Axess") },
                isError = state.nameError,
                supportingText = {
                    Text(
                        if (state.nameError) "Bir ad yaz"
                        else "Hızlı kayıtta bu adı yazınca harcama bu karta bağlanır",
                    )
                },
                inputTransformation = InputTransformation.maxLength(40),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                lineLimits = TextFieldLineLimits.SingleLine,
            )

            OutlinedTextField(
                state = state.statementDay,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading,
                label = { Text("Ekstre günü (isteğe bağlı)") },
                placeholder = { Text("ör. 15") },
                isError = state.statementDayError,
                supportingText = {
                    Text(
                        if (state.statementDayError) "1 ile 31 arasında bir gün yaz"
                        else "Koç bir gün önceden hatırlatır; mutabakat dönemi buna göre hesaplanır",
                    )
                },
                inputTransformation = InputTransformation.maxLength(2).then(digitsOnly),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                lineLimits = TextFieldLineLimits.SingleLine,
            )

            OutlinedTextField(
                state = state.carriedBalance,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading,
                label = { Text("Devreden borç (isteğe bağlı)") },
                placeholder = { Text("0") },
                suffix = { Text("₺") },
                isError = state.balanceError,
                supportingText = {
                    Text(
                        if (state.balanceError) "Geçerli bir tutar yaz (ör. 12.500)"
                        else "Önceki ekstrelerden ödenmeden kalan borç; her ay tam ödüyorsan boş bırak",
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                lineLimits = TextFieldLineLimits.SingleLine,
            )

            OutlinedTextField(
                state = state.monthlyRate,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading,
                label = { Text("Aylık faiz (vergiler dahil)") },
                prefix = { Text("%") },
                isError = state.rateError,
                supportingText = {
                    Text(if (state.rateError) "0 ile 100 arasında bir oran yaz (ör. 4,9)" else "Ekstrede yazan akdi faiz + vergiler")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                onKeyboardAction = { onEvent(CardEditEvent.Save) },
                lineLimits = TextFieldLineLimits.SingleLine,
            )

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
            text = { Text("Bu karta bağlı harcamalar silinmez, nakit olarak kalır.") },
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
