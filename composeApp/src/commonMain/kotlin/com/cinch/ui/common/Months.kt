package com.cinch.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

/** Ayın ilk günü. */
fun LocalDate.monthStart(): LocalDate = LocalDate(year, month, 1)

/**
 * Rotadaki yıl/ay (ay 1–12) ile açılacak ayın ilk günü. Eksik ya da geçersizse ya da gelecekteyse
 * içinde bulunulan ay.
 */
fun monthFromRoute(year: Int?, month: Int?, today: LocalDate): LocalDate {
    val current = today.monthStart()
    if (year == null || month == null || month !in 1..12) return current
    return minOf(LocalDate(year, Month(month), 1), current)
}

/** "‹ Ekim 2026 ›"; ileri ok yalnız içinde bulunulan aya kadar. */
@Composable
fun MonthSwitcher(
    month: LocalDate,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Önceki ay")
        }
        Text(
            month.monthYearLabel(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext, enabled = canGoNext) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Sonraki ay")
        }
    }
}
