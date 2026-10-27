package com.cinch.ui.month

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
import com.cinch.ui.common.BackButton
import com.cinch.ui.common.DayOfMonthField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomeEditScreen(
    state: IncomeEditState,
    onEvent: (IncomeEditEvent) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "Gelir ekle" else "Geliri düzenle") },
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
            OutlinedTextField(
                state = state.name,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading,
                label = { Text("Ne geliri?") },
                placeholder = { Text("ör. Maaş, ek iş, kira geliri") },
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
                placeholder = { Text("ör. 40.000") },
                suffix = { Text("₺") },
                isError = state.amountError,
                supportingText = {
                    Text(if (state.amountError) "Sıfırdan büyük bir tutar yaz" else "Eline geçen net tutar")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                lineLimits = TextFieldLineLimits.SingleLine,
            )

            DayOfMonthField(
                label = "Hangi gün yatıyor? (isteğe bağlı)",
                day = state.dayOfMonth,
                onPick = { onEvent(IncomeEditEvent.DayPicked(it)) },
                enabled = !state.isLoading,
                supportingText = "Maaş gününde hatırlatırız ve sonraki gelire kaç gün kaldığını gösteririz",
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = { onEvent(IncomeEditEvent.Save) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !state.isLoading && !state.isSaving,
            ) {
                Text("Kaydet")
            }

            if (!state.isNew) {
                TextButton(
                    onClick = { onEvent(IncomeEditEvent.DeleteRequested) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading && !state.isSaving,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Geliri sil")
                }
            }
        }
    }

    if (state.isConfirmingDelete) {
        AlertDialog(
            onDismissRequest = { onEvent(IncomeEditEvent.DeleteDismissed) },
            title = { Text("Gelir silinsin mi?") },
            confirmButton = {
                TextButton(onClick = { onEvent(IncomeEditEvent.DeleteConfirmed) }) {
                    Text("Sil", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(IncomeEditEvent.DeleteDismissed) }) { Text("Vazgeç") }
            },
        )
    }
}
