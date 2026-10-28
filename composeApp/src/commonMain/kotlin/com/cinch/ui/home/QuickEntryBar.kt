package com.cinch.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.cinch.shared.domain.Card
import com.cinch.shared.domain.Category
import com.cinch.shared.parser.ParsedEntry
import com.cinch.ui.common.DateChip
import com.cinch.ui.common.display
import com.cinch.ui.common.emoji
import com.cinch.ui.common.label
import com.cinch.ui.theme.colors
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt

/**
 * Ekranın altındaki kayıt alanı: klavye açılınca onunla birlikte yukarı çıkar.
 *
 * @param onScanReceipt fiş/fatura tarayıcı; null ise (platformda yok) kamera düğmesi gösterilmez.
 * @param autoFocus açılınca yazma alanına odaklan (widget'tan açılan hızlı kayıt penceresi).
 */
@Composable
fun QuickEntryBar(
    state: HomeState,
    onEvent: (HomeEvent) -> Unit,
    onScanReceipt: (() -> Unit)? = null,
    onPickReceipt: (() -> Unit)? = null,
    autoFocus: Boolean = false,
    header: (@Composable () -> Unit)? = null,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(autoFocus) {
        if (autoFocus) focusRequester.requestFocus()
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            header?.invoke()
            state.parsed?.let { parsed ->
                EntryPreview(
                    parsed = parsed,
                    category = state.entryCategory ?: parsed.category,
                    state = state,
                    onPick = { onEvent(HomeEvent.CategoryPicked(it)) },
                    onPickCard = { onEvent(HomeEvent.CardPicked(it)) },
                    onPickDate = { onEvent(HomeEvent.DatePicked(it)) },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    state = state.entry,
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    shape = RoundedCornerShape(28.dp),
                    placeholder = { Text("Ne harcadın? ör. 195 market") },
                    leadingIcon = if (onScanReceipt != null && onPickReceipt != null) {
                        { ReceiptButton(onScan = onScanReceipt, onPick = onPickReceipt) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        imeAction = ImeAction.Done,
                    ),
                    onKeyboardAction = { if (state.canSave) onEvent(HomeEvent.SaveEntry) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
                FilledIconButton(
                    onClick = { onEvent(HomeEvent.SaveEntry) },
                    enabled = state.canSave,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Ekle")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryPreview(
    parsed: ParsedEntry,
    category: Category,
    state: HomeState,
    onPick: (Category) -> Unit,
    onPickCard: (Long?) -> Unit,
    onPickDate: (LocalDate) -> Unit,
) {
    // Dar ekranda çipler alt satıra geçer.
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        itemVerticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val amount = parsed.chargeableAmount
        Text(
            amount?.display() ?: "Tutarı da yaz",
            style = MaterialTheme.typography.titleLarge,
            color = if (amount == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        CategoryPicker(selected = category, onPick = onPick)
        if (state.cards.isNotEmpty()) {
            CardPicker(cards = state.cards, selectedId = state.entryCardId, onPick = onPickCard)
        }
        // "dün", "cuma" yazınca kendiliğinden değişir; takvimden de seçilir.
        DateChip(date = state.entryDate ?: parsed.date, today = state.today, onPick = onPickDate)

        val details = buildList {
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

/** Fiş ya da fatura fotoğrafından kayıt: tara ya da galeriden seç. */
@Composable
private fun ReceiptButton(onScan: () -> Unit, onPick: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Rounded.PhotoCamera, contentDescription = "Fişten ekle")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("📷  Fiş ya da fatura tara") },
                onClick = {
                    expanded = false
                    onScan()
                },
            )
            DropdownMenuItem(
                text = { Text("🖼️  Galeriden seç") },
                onClick = {
                    expanded = false
                    onPick()
                },
            )
        }
    }
}

/** Tahmin yanlışsa kullanıcı buradan düzeltir; kaydedince uygulama bu kelimeleri öğrenir. */
@Composable
private fun CategoryPicker(selected: Category, onPick: (Category) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        val colors = selected.colors()
        AssistChip(
            onClick = { expanded = true },
            label = { Text("${selected.emoji} ${selected.label} ▾") },
            colors = AssistChipDefaults.assistChipColors(containerColor = colors.container, labelColor = colors.content),
            border = null,
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

/** Kart yoksa hiç gösterilmez. Metinde kart adı geçerse kendiliğinden seçilir; buradan değiştirilir. */
@Composable
private fun CardPicker(cards: List<Card>, selectedId: Long?, onPick: (Long?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = cards.firstOrNull { it.id == selectedId }
    Box {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(if (selected == null) "💵 Nakit ▾" else "💳 ${selected.name} ▾") },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("💵  Nakit") },
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
}
