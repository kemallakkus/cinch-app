package com.cinch.ui.common

import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.datetime.LocalDate

private const val MILLIS_PER_DAY = 86_400_000L

/** Material tarih seçici UTC gece yarısı milisaniyesiyle çalışır; saat dilimi kayması olmaz. */
internal fun LocalDate.toUtcMillis(): Long = toEpochDays() * MILLIS_PER_DAY

internal fun utcMillisToDate(millis: Long): LocalDate = LocalDate.fromEpochDays(millis.floorDiv(MILLIS_PER_DAY))

/** Takvimden gün seçimi; [maxDate] sonrası seçilemez (harcama gelecekte olamaz). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CinchDatePickerDialog(
    selected: LocalDate,
    maxDate: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val maxMillis = maxDate.toUtcMillis()
    val selectable = remember(maxMillis) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= maxMillis
            override fun isSelectableYear(year: Int): Boolean = year <= maxDate.year
        }
    }
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = minOf(selected, maxDate).toUtcMillis(),
        yearRange = (maxDate.year - 5)..maxDate.year,
        selectableDates = selectable,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    pickerState.selectedDateMillis?.let { onPick(utcMillisToDate(it)) }
                    onDismiss()
                },
            ) { Text("Tamam") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    ) {
        DatePicker(state = pickerState)
    }
}

/** "📅 Dün ▾" çipi; dokununca takvim açılır. */
@Composable
fun DateChip(date: LocalDate, today: LocalDate, onPick: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    AssistChip(
        onClick = { open = true },
        label = { Text("📅 ${date.relativeLabel(today)} ▾") },
        modifier = modifier,
    )
    if (open) {
        CinchDatePickerDialog(selected = date, maxDate = today, onPick = onPick, onDismiss = { open = false })
    }
}
