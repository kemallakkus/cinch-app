package com.cinch.ui.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cinch.ui.common.display
import com.cinch.ui.common.longLabel
import kotlinx.datetime.DayOfWeek

private val digitsOnly = InputTransformation {
    if (!asCharSequence().all { it.isDigit() }) revertAllChanges()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetSetupScreen(
    state: BudgetSetupState,
    onEvent: (BudgetSetupEvent) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            if (!state.isOnboarding) {
                TopAppBar(
                    title = { Text("Bütçe") },
                    navigationIcon = { TextButton(onClick = onBack) { Text("Geri") } },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (state.isOnboarding) {
                Spacer(Modifier.height(24.dp))
                Text("Haftada ne kadar harcamak istiyorsun?", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Cinch bu sınırı kalan günlere böler ve her gün ne kadar harcayabileceğini söyler. " +
                        "Sonra değiştirebilirsin.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                state = state.weeklyLimit,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading,
                label = { Text("Haftalık sınır") },
                placeholder = { Text("ör. 1.500") },
                suffix = { Text("₺") },
                isError = state.limitError,
                supportingText = {
                    when {
                        state.limitError -> Text("Sıfırdan büyük bir tutar yaz (ör. 1.500)")
                        state.dailyHint != null -> Text("Günde yaklaşık ${state.dailyHint.display()}")
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                lineLimits = TextFieldLineLimits.SingleLine,
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Hafta hangi gün başlasın?", style = MaterialTheme.typography.titleSmall)
                WeekStartPicker(selected = state.weekStart, onPick = { onEvent(BudgetSetupEvent.WeekStartPicked(it)) })
            }

            OutlinedTextField(
                state = state.salaryDay,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading,
                label = { Text("Maaş günü (isteğe bağlı)") },
                placeholder = { Text("ör. 15") },
                isError = state.salaryDayError,
                supportingText = {
                    Text(
                        if (state.salaryDayError) "1 ile 31 arasında bir gün yaz"
                        else "Koç o gün haftanın planını hatırlatır",
                    )
                },
                inputTransformation = InputTransformation.maxLength(2).then(digitsOnly),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                onKeyboardAction = { onEvent(BudgetSetupEvent.Save) },
                lineLimits = TextFieldLineLimits.SingleLine,
            )

            Button(
                onClick = { onEvent(BudgetSetupEvent.Save) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !state.isLoading && !state.isSaving,
            ) {
                Text(if (state.isOnboarding) "Başla" else "Kaydet")
            }
        }
    }
}

@Composable
private fun WeekStartPicker(selected: DayOfWeek, onPick: (DayOfWeek) -> Unit) {
    // Çoğu kullanıcı için iki seçenek yeter; başka bir gün kayıtlıysa o da gösterilir.
    val options = listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY).let { if (selected in it) it else it + selected }
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, day ->
            SegmentedButton(
                selected = day == selected,
                onClick = { onPick(day) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(day.longLabel)
            }
        }
    }
}
