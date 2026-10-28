package com.cinch.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cinch.shared.domain.Category
import com.cinch.ui.common.BackButton
import com.cinch.ui.common.CardSelector
import com.cinch.ui.common.CinchDatePickerDialog
import com.cinch.ui.common.InfoTip
import com.cinch.ui.common.emoji
import com.cinch.ui.common.label
import com.cinch.ui.common.relativeLabel
import com.cinch.ui.common.shortLabel
import com.cinch.ui.theme.colors
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExpenseEditScreen(
    state: ExpenseEditState,
    onEvent: (ExpenseEditEvent) -> Unit,
    onBack: () -> Unit,
) {
    var pickingDate by remember { mutableStateOf(false) }
    val enabled = !state.isLoading && !state.isSaving

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isFixedPayment) "Ödemeyi düzenle" else "Harcamayı düzenle") },
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
            if (state.isFixedPayment) {
                Text(
                    "📌 ${state.fixedName ?: "Sabit gider"} ödemesi. Tutarı ya da tarihi düzeltirsen ay özeti " +
                        "ve borç ilerlemesi de güncellenir.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                state = state.amount,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                label = { Text("Tutar") },
                suffix = { Text("₺") },
                isError = state.amountError,
                supportingText = { if (state.amountError) Text("Sıfırdan büyük bir tutar yaz") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                lineLimits = TextFieldLineLimits.SingleLine,
            )

            if (!state.isFixedPayment) {
                OutlinedTextField(
                    state = state.text,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    label = { Text("Açıklama") },
                    placeholder = { Text("ör. migros, öğle yemeği") },
                    supportingText = { Text("Listede ve aramada bu yazı görünür") },
                    inputTransformation = InputTransformation.maxLength(80),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
                    lineLimits = TextFieldLineLimits.SingleLine,
                )

                Text("Kategori", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Category.entries.forEach { category ->
                        val colors = category.colors()
                        FilterChip(
                            selected = category == state.category,
                            onClick = { onEvent(ExpenseEditEvent.CategoryPicked(category)) },
                            label = { Text("${category.emoji} ${category.label}") },
                            enabled = enabled,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.container,
                                selectedLabelColor = colors.content,
                            ),
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Tarih", style = MaterialTheme.typography.titleSmall)
                OutlinedButton(onClick = { pickingDate = true }, enabled = enabled) {
                    val relative = state.date.relativeLabel(state.today)
                    // "Bugün" ve "Dün"de tarih de yazılır; diğerleri zaten tarihli ("Pzt 13 Oca").
                    val text = if (relative == "Bugün" || relative == "Dün") "$relative · ${state.date.shortLabel()}" else relative
                    Text("📅 $text ▾")
                }
            }

            if (!state.isFixedPayment) {
                SharePicker(selected = state.share, options = state.shareOptions, enabled = enabled) {
                    onEvent(ExpenseEditEvent.SharePicked(it))
                }
            }

            if (state.cards.isNotEmpty()) {
                CardSelector(cards = state.cards, selectedId = state.cardId, enabled = enabled) {
                    onEvent(ExpenseEditEvent.CardPicked(it))
                }
            }

            Button(
                onClick = { onEvent(ExpenseEditEvent.Save) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = enabled,
            ) {
                Text("Kaydet")
            }
            TextButton(
                onClick = { onEvent(ExpenseEditEvent.DeleteRequested) },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(if (state.isFixedPayment) "Ödemeyi sil" else "Harcamayı sil")
            }
        }
    }

    if (pickingDate) {
        CinchDatePickerDialog(
            selected = state.date,
            maxDate = state.today,
            onPick = { onEvent(ExpenseEditEvent.DatePicked(it)) },
            onDismiss = { pickingDate = false },
        )
    }

    if (state.isConfirmingDelete) {
        AlertDialog(
            onDismissRequest = { onEvent(ExpenseEditEvent.DeleteDismissed) },
            title = { Text(if (state.isFixedPayment) "Ödeme silinsin mi?" else "Harcama silinsin mi?") },
            text = {
                Text(
                    if (state.isFixedPayment) "Bu ay ödenmemiş sayılır; borçsa kalan borç geri artar."
                    else "Haftalık payın ve ay özetin buna göre güncellenir.",
                )
            },
            confirmButton = {
                TextButton(onClick = { onEvent(ExpenseEditEvent.DeleteConfirmed) }) {
                    Text("Sil", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(ExpenseEditEvent.DeleteDismissed) }) { Text("Vazgeç") }
            },
        )
    }
}

/** Paylaşılan ya da kısmen geri gelen harcamada sınıra yalnız kullanıcının payı sayılır. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SharePicker(selected: Double, options: List<Double>, enabled: Boolean, onPick: (Double) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Senin payın", style = MaterialTheme.typography.titleSmall)
            InfoTip(
                title = "Senin payın",
                text = "Hesabı arkadaşınla böldüysen ya da bir kısmı geri geldiyse, haftalık sınırına yalnız " +
                    "senin payın sayılır. Kartın hesabına ise ödenen tutarın tamamı yazılır.",
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { share ->
                FilterChip(
                    selected = share == selected,
                    onClick = { onPick(share) },
                    label = { Text(shareLabel(share)) },
                    enabled = enabled,
                )
            }
        }
    }
}

private fun shareLabel(share: Double): String = when (share) {
    1.0 -> "Tamamı benim"
    0.5 -> "Yarısı"
    0.0 -> "Hiç (geri geldi)"
    else -> "%${(share * 100).roundToInt()}"
}
