package com.cinch.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.cinch.shared.domain.Category
import com.cinch.shared.parser.ParsedEntry
import com.cinch.ui.common.display
import com.cinch.ui.common.emoji
import com.cinch.ui.common.label
import com.cinch.ui.common.relativeLabel
import kotlin.math.roundToInt

/** Ekranın altındaki kayıt alanı: klavye açılınca onunla birlikte yukarı çıkar. */
@Composable
fun QuickEntryBar(state: HomeState, onEvent: (HomeEvent) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.parsed?.let { parsed ->
                EntryPreview(
                    parsed = parsed,
                    category = state.entryCategory ?: parsed.category,
                    state = state,
                    onPick = { onEvent(HomeEvent.CategoryPicked(it)) },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    state = state.entry,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("195 market") },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        imeAction = ImeAction.Done,
                    ),
                    onKeyboardAction = { if (state.canSave) onEvent(HomeEvent.SaveEntry) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
                Button(
                    onClick = { onEvent(HomeEvent.SaveEntry) },
                    enabled = state.canSave,
                    modifier = Modifier.height(56.dp),
                ) {
                    Text("Ekle")
                }
            }
        }
    }
}

@Composable
private fun EntryPreview(
    parsed: ParsedEntry,
    category: Category,
    state: HomeState,
    onPick: (Category) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val amount = parsed.chargeableAmount
        Text(
            amount?.display() ?: "Tutar yaz",
            style = MaterialTheme.typography.titleMedium,
            color = if (amount == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        CategoryPicker(selected = category, onPick = onPick)

        val details = buildList {
            if (parsed.date != state.today) add(parsed.date.relativeLabel(state.today))
            if (parsed.chargeableFraction < 1.0) add("payın %${(parsed.chargeableFraction * 100).roundToInt()}")
        }
        if (details.isNotEmpty()) {
            Text(
                details.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Tahmin yanlışsa kullanıcı buradan düzeltir; kaydedince uygulama bu kelimeleri öğrenir. */
@Composable
private fun CategoryPicker(selected: Category, onPick: (Category) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { expanded = true },
            label = { Text("${selected.emoji} ${selected.label} ▾") },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Category.entries.forEach { category ->
                DropdownMenuItem(
                    text = { Text("${category.emoji}  ${category.label}") },
                    onClick = {
                        onPick(category)
                        expanded = false
                    },
                )
            }
        }
    }
}
