package com.cinch.ui.statement

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cinch.shared.money.Money
import com.cinch.shared.statement.StatementDraft
import com.cinch.ui.common.BackButton
import com.cinch.ui.common.CinchDatePickerDialog
import com.cinch.ui.common.InfoTip
import com.cinch.ui.common.display
import com.cinch.ui.common.emoji
import com.cinch.ui.common.shortLabel
import com.cinch.ui.theme.CinchTheme
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

private enum class PickingDate { DUE, STATEMENT }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StatementReviewScreen(
    state: StatementReviewState,
    onEvent: (StatementReviewEvent) -> Unit,
    onBack: () -> Unit,
) {
    var picking by remember { mutableStateOf<PickingDate?>(null) }
    val enabled = !state.isLoading && !state.isSaving
    val draft = state.draft

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Kart ekranını kontrol et") }, navigationIcon = { BackButton(onBack) })
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
            Banner(
                if (draft.isEmpty) {
                    "😕" to "Bu fotoğraflarda kart bilgisi bulamadık. Banka uygulamasında kartın ayrıntı ekranını " +
                        "(son ödeme tarihi ve ekstre borcunun yazdığı) düz ve parlamasız çekmeyi dene; aşağıyı elle de doldurabilirsin."
                } else {
                    "📄" to "Okuduklarımızı doldurduk. Kontrol et, yanlış bir şey varsa düzelt."
                },
            )

            CardPicker(state, enabled, onEvent)

            // Banka kartında ekstre yok: yalnız hareketler karşılaştırılır.
            if (state.selectedCard?.isCredit != false) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Son ödeme tarihi", style = MaterialTheme.typography.titleSmall)
                    OutlinedButton(onClick = { picking = PickingDate.DUE }, enabled = enabled) {
                        Text(state.dueDate?.let { "📅 ${it.withYear()} ▾" } ?: "📅 Seç ▾")
                    }
                }
                MoneyField(state.balance, "Kalan ekstre borcu", "Ödemen gereken; ödedikçe azalır", enabled)
                MoneyField(state.minimum, "Asgari ödeme (isteğe bağlı)", "Bankanın en az ödemeni istediği tutar", enabled)

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Ekstre kesim tarihi", style = MaterialTheme.typography.titleSmall)
                    OutlinedButton(onClick = { picking = PickingDate.STATEMENT }, enabled = enabled) {
                        Text(state.statementDate?.let { "📅 ${it.withYear()} ▾" } ?: "📅 Seç ▾")
                    }
                }

                OtherValues(draft)
            }

            if (state.transactions.isNotEmpty()) {
                TransactionsSection(state, enabled, onEvent)
            }

            Button(
                onClick = { onEvent(StatementReviewEvent.Save) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = enabled && state.cards.isNotEmpty(),
            ) {
                Text(if (state.selectedCount > 0) "Kaydet ve ${state.selectedCount} harcamayı ekle" else "Kaydet")
            }

            ReadText(draft)
        }
    }

    when (picking) {
        PickingDate.DUE -> CinchDatePickerDialog(
            selected = state.dueDate ?: state.today,
            // Son ödeme ileride olabilir.
            maxDate = state.today.plus(DatePeriod(years = 1)),
            onPick = { onEvent(StatementReviewEvent.DueDatePicked(it)) },
            onDismiss = { picking = null },
        )
        PickingDate.STATEMENT -> CinchDatePickerDialog(
            selected = state.statementDate ?: state.today,
            maxDate = state.today,
            onPick = { onEvent(StatementReviewEvent.StatementDatePicked(it)) },
            onDismiss = { picking = null },
        )
        null -> Unit
    }
}

@Composable
private fun Banner(content: Pair<String, String>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(content.first, style = MaterialTheme.typography.titleLarge)
        Text(content.second, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
private fun CardPicker(state: StatementReviewState, enabled: Boolean, onEvent: (StatementReviewEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Hangi kart?", style = MaterialTheme.typography.titleSmall)
        if (state.cards.isEmpty()) {
            Text(
                "Önce Kartlar sekmesinden kartını ekle; sonra bu ekranı yeniden okut.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            CardChoices(state, enabled, onEvent)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardChoices(state: StatementReviewState, enabled: Boolean, onEvent: (StatementReviewEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.cards.forEach { card ->
                FilterChip(
                    selected = card.id == state.cardId,
                    onClick = { onEvent(StatementReviewEvent.CardPicked(card.id)) },
                    label = { Text("💳 ${card.name}" + (card.last4?.let { " •••• $it" } ?: "")) },
                    enabled = enabled,
                )
            }
        }
        val seen = state.draft?.last4Seen.orEmpty()
        val note = when {
            state.cardError -> "Okunanların hangi karta ait olduğunu seç."
            seen.size > 1 -> "Fotoğrafta birden çok kart var (${seen.joinToString()}); doğru kartı seç."
            state.last4ToSave != null -> "•••• ${state.last4ToSave} bu karta kaydedilecek; bir dahaki sefere kendiliğinden seçilir."
            else -> null
        }
        note?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (state.cardError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MoneyField(field: TextFieldState, label: String, hint: String, enabled: Boolean) {
    OutlinedTextField(
        state = field,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(label) },
        suffix = { Text("₺") },
        supportingText = { Text(hint) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        lineLimits = TextFieldLineLimits.SingleLine,
    )
}

/** Düzeltilmesi gerekmeyen, bilgi amaçlı okunanlar (kart detayında gösterilir). */
@Composable
private fun OtherValues(draft: StatementDraft) {
    val lines = buildList {
        draft.periodSpending?.let { add("Dönem içi harcama" to it.display()) }
        draft.futureInstallments?.let { add("Gelecek dönem taksitler" to it.display()) }
        draft.totalDebt?.let { add("Toplam borç" to it.display()) }
        draft.creditLimit?.let { add("Kart limiti" to it.display()) }
        draft.availableLimit?.let { add("Kullanılabilir limit" to limitText(it)) }
        draft.nextStatementDate?.let { add("Sonraki ekstre kesimi" to "${it.shortLabel()} ${it.year}") }
    }
    if (lines.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Diğer okunanlar", style = MaterialTheme.typography.titleSmall)
        lines.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth()) {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(value, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

private fun limitText(available: Money): String =
    if (available.isNegative) "${available.absolute.display()} aşıldı" else available.display()

@Composable
private fun TransactionsSection(state: StatementReviewState, enabled: Boolean, onEvent: (StatementReviewEvent) -> Unit) {
    val counts = state.transactions.groupingBy { it.status }.eachCount()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Hareketler", style = MaterialTheme.typography.titleSmall)
            InfoTip(
                title = "Hareketleri karşılaştırma",
                text = "Bankadaki her harcamayı bu karta girdiklerinle tutar ve tarihe (en fazla 3 gün fark) bakarak " +
                    "eşleriz. Bankada olup sende olmayanlar seçili gelir; kaydedince eklenir. Taksitler seçilmeden gelir: " +
                    "alışverişi tek seferde girdiysen tekrar eklenmesin.",
            )
        }
        val matched = counts[TransactionStatus.MATCHED] ?: 0
        val spending = matched + (counts[TransactionStatus.MISSING] ?: 0)
        if (spending > 0) {
            Text(
                "Bankadaki $spending harcamanın $matched tanesini zaten girmişsin (%${matched * 100 / spending})" +
                    if (matched == spending) " 👏" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = if (matched * 10 >= spending * 9) CinchTheme.colors.good else MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            listOfNotNull(
                counts[TransactionStatus.MATCHED]?.let { "✅ $it eşleşti" },
                counts[TransactionStatus.MISSING]?.let { "➕ $it eksik" },
                counts[TransactionStatus.INSTALLMENT]?.let { "🔁 $it taksit" },
                counts[TransactionStatus.PAYMENT]?.let { "💸 $it ödeme" },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.transactions.forEachIndexed { index, row ->
            TransactionLine(row, enabled) { onEvent(StatementReviewEvent.TransactionToggled(index)) }
        }
    }
}

@Composable
private fun TransactionLine(row: TransactionRow, enabled: Boolean, onToggle: () -> Unit) {
    val selectable = row.status == TransactionStatus.MISSING || row.status == TransactionStatus.INSTALLMENT
    val tx = row.transaction
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .then(if (selectable && enabled) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (selectable) {
            Checkbox(checked = row.selected, onCheckedChange = { onToggle() }, enabled = enabled)
        } else {
            Text(if (row.status == TransactionStatus.MATCHED) "✅" else "💸", modifier = Modifier.padding(horizontal = 12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(tx.description, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val parts = buildList {
                add(tx.date.shortLabel())
                tx.installment?.let { add("taksit ${it.number}/${it.total}") }
                when (row.status) {
                    TransactionStatus.MATCHED -> add("Cinch'te var")
                    TransactionStatus.PAYMENT -> add("ödeme / iade")
                    else -> add("${row.category.emoji} eklenecek kategori")
                }
            }
            Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            tx.amount.display(),
            style = MaterialTheme.typography.titleSmall,
            color = if (tx.isPaymentOrRefund) CinchTheme.colors.good else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ReadText(draft: StatementDraft) {
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

/** Kart detayındaki son ödeme yazısı için de kullanılır: "6 Eki 2026". */
internal fun LocalDate.withYear(): String = "${shortLabel()} $year"
