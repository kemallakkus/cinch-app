package com.cinch.ui.receipt

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cinch.shared.domain.Category
import com.cinch.shared.receipt.ReceiptDraft
import com.cinch.ui.common.BackButton
import com.cinch.ui.common.CardSelector
import com.cinch.ui.common.CinchDatePickerDialog
import com.cinch.ui.common.InfoTip
import com.cinch.ui.common.emoji
import com.cinch.ui.common.label
import com.cinch.ui.common.relativeLabel
import com.cinch.ui.common.shortLabel
import com.cinch.ui.theme.colors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReceiptReviewScreen(
    state: ReceiptReviewState,
    onEvent: (ReceiptReviewEvent) -> Unit,
    onBack: () -> Unit,
) {
    var pickingDate by remember { mutableStateOf(false) }
    val enabled = !state.isLoading && !state.isSaving
    val draft = state.draft
    val linked = state.linkedFixed

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (draft?.isBill == true) "Faturayı kontrol et" else "Fişi kontrol et") },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        if (state.isLoading || draft == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ReadSummary(draft)

            if (state.showFixedLink) {
                FixedLinkPicker(state, enabled, onEvent)
            }

            OutlinedTextField(
                state = state.amount,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                label = { Text(if (linked != null) "Ödenen tutar" else "Tutar") },
                suffix = { Text("₺") },
                isError = state.amountError,
                supportingText = {
                    Text(
                        when {
                            state.amountError -> "Sıfırdan büyük bir tutar yaz"
                            draft.amount == null -> "Tutarı okuyamadık, fişteki toplamı yaz"
                            else -> "Fişteki \"TOPLAM\"dan okundu"
                        },
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                lineLimits = TextFieldLineLimits.SingleLine,
            )

            if (linked == null) {
                OutlinedTextField(
                    state = state.text,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    label = { Text("Açıklama") },
                    placeholder = { Text("ör. Migros") },
                    supportingText = { Text("Listede ve aramada bu yazı görünür") },
                    inputTransformation = InputTransformation.maxLength(80),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    lineLimits = TextFieldLineLimits.SingleLine,
                )

                Text("Kategori", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Category.entries.forEach { category ->
                        val colors = category.colors()
                        FilterChip(
                            selected = category == state.category,
                            onClick = { onEvent(ReceiptReviewEvent.CategoryPicked(category)) },
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
                Text(if (linked != null) "Ödeme tarihi" else "Tarih", style = MaterialTheme.typography.titleSmall)
                OutlinedButton(onClick = { pickingDate = true }, enabled = enabled) {
                    val relative = state.date.relativeLabel(state.today)
                    val text = if (relative == "Bugün" || relative == "Dün") "$relative · ${state.date.shortLabel()}" else relative
                    Text("📅 $text ▾")
                }
            }

            if (state.cards.isNotEmpty()) {
                CardSelector(cards = state.cards, selectedId = state.cardId, enabled = enabled) {
                    onEvent(ReceiptReviewEvent.CardPicked(it))
                }
            }

            Button(
                onClick = { onEvent(ReceiptReviewEvent.Save) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = enabled,
            ) {
                Text(if (linked != null) "Ödendi olarak kaydet" else "Kaydet")
            }

            ReadText(draft)
        }
    }

    if (pickingDate) {
        CinchDatePickerDialog(
            selected = state.date,
            maxDate = state.today,
            onPick = { onEvent(ReceiptReviewEvent.DatePicked(it)) },
            onDismiss = { pickingDate = false },
        )
    }
}

/** Ne okunduğunu tek cümleyle söyler; hiçbir şey okunamadıysa ne yapılacağını. */
@Composable
private fun ReadSummary(draft: ReceiptDraft) {
    val (emoji, text) = when {
        draft.isEmpty -> "😕" to "Bu fotoğraftan tutar okuyamadık. Daha net ve düz bir fotoğrafla tekrar deneyebilir " +
            "ya da aşağıyı elle doldurabilirsin."
        draft.amount == null -> "🔎" to "Tutarı okuyamadık; diğerlerini doldurduk. Fişteki toplamı yazman yeter."
        else -> "📄" to "Okuduklarımızı doldurduk. Kontrol et, yanlış bir şey varsa düzelt."
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, style = MaterialTheme.typography.titleLarge)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

/** Fatura hangi sabit gidere ait: seçilirse kaydedince "Bu ay"da ödendi işaretlenir. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FixedLinkPicker(state: ReceiptReviewState, enabled: Boolean, onEvent: (ReceiptReviewEvent) -> Unit) {
    val linked = state.linkedFixed
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Hangi sabit gider?", style = MaterialTheme.typography.titleSmall)
            InfoTip(
                title = "Faturayı sabit gidere bağla",
                text = "Seçersen bu ödeme \"Bu ay\" sekmesinde o sabit giderin ödemesi olarak işaretlenir; haftalık " +
                    "harcama payına sayılmaz. Fatura her ay farklı geliyorsa gerçek tutar böylece kaydedilir.",
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.fixedOptions.forEach { fixed ->
                FilterChip(
                    selected = fixed.id == state.linkedFixedId,
                    onClick = { onEvent(ReceiptReviewEvent.FixedLinked(fixed.id)) },
                    label = { Text("${fixed.kind.emoji} ${fixed.name}") },
                    enabled = enabled,
                )
            }
            FilterChip(
                selected = linked == null,
                onClick = { onEvent(ReceiptReviewEvent.FixedLinked(null)) },
                label = { Text("Hiçbiri, normal harcama") },
                enabled = enabled,
            )
        }
        val notes = buildList {
            if (linked != null) add("Kaydedince \"Bu ay\"da ${linked.name} ödendi işaretlenir.")
            state.draft?.dueDate?.let { add("Son ödeme: ${it.shortLabel()} ${it.year}") }
        }
        if (notes.isNotEmpty()) {
            Text(
                notes.joinToString(" "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (linked != null && linked.id in state.paidThisMonth) {
            Text(
                "${linked.name} bu ay zaten ödendi olarak işaretli. Kaydedersen ikinci bir ödeme olarak eklenir.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Okunan ham metin: tahmin yanlışsa kullanıcı neyin okunduğunu görür. */
@Composable
private fun ReadText(draft: ReceiptDraft) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = { open = !open }) {
            Text(if (open) "Okunan metni gizle" else "Okunan metni göster (${draft.rows.size} satır)")
        }
        AnimatedVisibility(visible = open) {
            Text(
                draft.rows.joinToString("\n"),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}
