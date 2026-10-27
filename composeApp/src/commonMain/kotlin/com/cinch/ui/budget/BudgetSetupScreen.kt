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
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cinch.ui.common.BackButton
import com.cinch.ui.common.display
import com.cinch.ui.common.longLabel
import kotlinx.datetime.DayOfWeek

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
                    navigationIcon = { BackButton(onBack) },
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
                Text("👋", style = MaterialTheme.typography.displayMedium)
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
                label = { Text("Haftalık harcama sınırı") },
                placeholder = { Text("ör. 1.500") },
                suffix = { Text("₺") },
                isError = state.limitError,
                supportingText = {
                    when {
                        state.limitError -> Text("Sıfırdan büyük bir tutar yaz (ör. 1.500)")
                        state.dailyHint != null -> Text("Günde yaklaşık ${state.dailyHint.display()}")
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                onKeyboardAction = { onEvent(BudgetSetupEvent.Save) },
                lineLimits = TextFieldLineLimits.SingleLine,
            )

            SuggestionCard(state.suggestion, onUse = { onEvent(BudgetSetupEvent.UseSuggestion) })

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Hafta hangi gün başlasın?", style = MaterialTheme.typography.titleSmall)
                WeekStartPicker(selected = state.weekStart, onPick = { onEvent(BudgetSetupEvent.WeekStartPicked(it)) })
            }

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

/**
 * Gelir ve sabit giderler girildiyse haftalık sınır önerisi; gelir yoksa "Bu ay" sekmesine davet.
 */
@Composable
private fun SuggestionCard(suggestion: LimitSuggestion?, onUse: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                suggestion == null -> {
                    Text("💡 Sana bir sınır önerelim mi?", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "\"Bu ay\" sekmesinden gelirini ve kira, fatura gibi sabit giderlerini eklersen, " +
                            "haftada ne kadar harcayabileceğini hesaplarız.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                suggestion.weekly == null -> {
                    Text("💡 Sabit giderler gelirini aşıyor", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Gelirin ${suggestion.monthlyIncome.display()}, sabit giderlerin " +
                            "${suggestion.monthlyFixed.display()}. Harcamaya para kalmıyor; önce sabit giderlere bakalım.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                else -> {
                    Text(
                        "💡 Önerimiz: haftada en fazla ${suggestion.weekly.display()}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "Gelirin ${suggestion.monthlyIncome.display()}, sabit giderlerin ${suggestion.monthlyFixed.display()}. " +
                            "Kalanı ayın haftalarına böldük. Biraz biriktirmek istersen daha düşük yaz.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    FilledTonalButton(onClick = onUse) { Text("Bunu kullan") }
                }
            }
        }
    }
}
