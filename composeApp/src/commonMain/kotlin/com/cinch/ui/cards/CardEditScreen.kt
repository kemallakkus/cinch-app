package com.cinch.ui.cards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cinch.shared.domain.CardType
import com.cinch.ui.common.BackButton
import com.cinch.ui.common.DayOfMonthField
import com.cinch.ui.common.InfoTip
import com.cinch.ui.common.dayOfMonthLabel

/**
 * Kart ekleme ve düzenleme. Sorulan yalnız ad ve kartın nasıl ödediği; kesim günü ve faiz "Gelişmiş"te, çoğu kullanıcı
 * bunları banka ekranından okutur (kesim günü, son ödeme ve borç fotoğraftan dolar).
 */
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
            OutlinedTextField(
                state = state.name,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading,
                label = { Text("Kartın adı") },
                placeholder = { Text("ör. QNB, Axess, Maaş kartı") },
                isError = state.nameError,
                supportingText = {
                    Text(
                        if (state.nameError) "Bir ad yaz"
                        else "Harcarken bu adı yazarsan (\"195 market qnb\") harcama bu karta yazılır",
                    )
                },
                inputTransformation = InputTransformation.maxLength(40),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                lineLimits = TextFieldLineLimits.SingleLine,
            )

            Text("Bu kartla harcayınca para…", style = MaterialTheme.typography.titleSmall)
            TypeOption(
                selected = state.type == CardType.CREDIT,
                title = "Ay sonunda ekstreyle ödenir",
                subtitle = "Kredi kartı: son ödeme günü, borç, taksit",
                enabled = !state.isLoading,
                onClick = { onEvent(CardEditEvent.TypePicked(CardType.CREDIT)) },
            )
            TypeOption(
                selected = state.type == CardType.DEBIT,
                title = "Hesabımdan hemen çıkar",
                subtitle = "Banka kartı: ekstre ve borç yok",
                enabled = !state.isLoading,
                onClick = { onEvent(CardEditEvent.TypePicked(CardType.DEBIT)) },
            )

            if (state.type == CardType.CREDIT) {
                if (state.isNew) {
                    Text(
                        "📸 Kaydedince banka uygulamasındaki kart ekranının fotoğrafını seçmen yeter: kesim günü, son ödeme " +
                            "tarihi ve borç kendiliğinden dolar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Advanced(state, onEvent)
            }

            Button(
                onClick = { onEvent(CardEditEvent.Save) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !state.isLoading && !state.isSaving,
            ) {
                Text(if (state.isNew) "Kartı ekle" else "Kaydet")
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

/** Kartın nasıl ödediği: "kredi/banka kartı" terimi yerine ne olduğunu söyleyen iki seçenek. */
@Composable
private fun TypeOption(selected: Boolean, title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null)
            Column(Modifier.padding(start = 4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Kesim günü, faiz ve (eski kayıtta varsa) elle girilmiş eski borç; kapalıyken kısa bir özet gösterir. */
@Composable
private fun Advanced(state: CardEditState, onEvent: (CardEditEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            onClick = { onEvent(CardEditEvent.AdvancedToggled) },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Gelişmiş", style = MaterialTheme.typography.titleSmall)
                    Text(
                        listOfNotNull(
                            state.statementDay?.let { "kesim ${dayOfMonthLabel(it).lowercase()}" } ?: "kesim günü fotoğraftan okunur",
                            "faiz %${state.monthlyRate.text}",
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(if (state.advancedOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
            }
        }
        AnimatedVisibility(visible = state.advancedOpen) {
            CreditFields(state, onEvent)
        }
    }
}

/** Yalnız kredi kartında: hesap kesim günü, faiz, eski kayıtlarda elle girilmiş borç. */
@Composable
private fun CreditFields(state: CardEditState, onEvent: (CardEditEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DayOfMonthField(
            label = "Hesap kesim günü",
            day = state.statementDay,
            onPick = { onEvent(CardEditEvent.StatementDayPicked(it)) },
            enabled = !state.isLoading,
            supportingText = "Bankanın o ayın harcamalarını topladığı gün. Banka ekranını okutunca kendiliğinden dolar.",
            modifier = Modifier.fillMaxWidth(),
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
                        "emin değilsen olduğu gibi bırak. Yalnız \"asgariyi ödersen ne kadar faiz işler\" hesabında kullanılır.",
                )
            },
            isError = state.rateError,
            supportingText = { if (state.rateError) Text("0 ile 100 arasında bir oran yaz (ör. 4,9)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            onKeyboardAction = { onEvent(CardEditEvent.Save) },
            lineLimits = TextFieldLineLimits.SingleLine,
        )

        // Eskiden elle girilen borç: yeni kartta sorulmaz (banka ekranından okunur); varsa düzeltilebilsin ya da silinebilsin.
        if (state.hasLegacyBalance) {
            OutlinedTextField(
                state = state.carriedBalance,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading,
                label = { Text("Elle girilmiş eski borç") },
                placeholder = { Text("0") },
                suffix = { Text("₺") },
                isError = state.balanceError,
                supportingText = {
                    Text(
                        if (state.balanceError) "Geçerli bir tutar yaz (ör. 12.500)"
                        else "Banka ekranını okutunca okunan borç kullanılır; bunu silebilirsin",
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                lineLimits = TextFieldLineLimits.SingleLine,
            )
        }
    }
}
