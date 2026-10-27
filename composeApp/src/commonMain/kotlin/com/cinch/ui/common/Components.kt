package com.cinch.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun BackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
    }
}

/**
 * Yanındaki terimi sade dille açıklayan "i" düğmesi. Dokununca balon açılır; dışarı dokununca kapanır.
 * Ekranda kalıcı açıklama metni yerine kullanılır: ekranı kalabalıklaştırmadan merak edene anlatır.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoTip(title: String, text: String, modifier: Modifier = Modifier) {
    val state = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberRichTooltipPositionProvider(),
        tooltip = { RichTooltip(title = { Text(title) }) { Text(text) } },
        state = state,
        modifier = modifier,
    ) {
        IconButton(onClick = { scope.launch { state.show() } }, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = "$title nedir?",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Başlık + yanında bilgi balonu. */
@Composable
fun TitleWithInfo(title: String, infoTitle: String = title, info: String, style: TextStyle = MaterialTheme.typography.titleMedium) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = style)
        InfoTip(title = infoTitle, text = info)
    }
}

/** "Ayın 15. günü"; seçilmemişse boş. */
fun dayOfMonthLabel(day: Int?): String = day?.let { "Ayın $it. günü" }.orEmpty()

/**
 * Ayın gününü (1–31) takvim ızgarasından seçtiren alan. Klavyeyle gün yazdırmaktan daha az hata çıkarır.
 *
 * @param optional true ise "Temizle" ile seçim kaldırılabilir (gün bilinmiyor).
 */
@Composable
fun DayOfMonthField(
    label: String,
    day: Int?,
    onPick: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    optional: Boolean = true,
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = dayOfMonthLabel(day),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            placeholder = { Text("Seç") },
            trailingIcon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
            supportingText = supportingText?.let { { Text(it) } },
            singleLine = true,
        )
        // Salt okunur alan dokunuşu yutar; üstüne şeffaf bir dokunma katmanı konur.
        Box(Modifier.matchParentSize().clickable(enabled = enabled) { open = true })
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..31).chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            week.forEach { value ->
                                DayCell(value, selected = value == day) {
                                    onPick(value)
                                    open = false
                                }
                            }
                            // Son satırı sola yasla.
                            repeat(7 - week.size) { Box(Modifier.size(36.dp)) }
                        }
                    }
                    Text(
                        "29, 30 ya da 31 seçersen kısa aylarda ayın son günü sayılır.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Vazgeç") } },
            dismissButton = if (optional) {
                {
                    TextButton(onClick = {
                        onPick(null)
                        open = false
                    }) { Text("Temizle") }
                }
            } else null,
        )
    }
}

@Composable
private fun DayCell(day: Int, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (selected) colors.primary else colors.surfaceContainerHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            day.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) colors.onPrimary else colors.onSurface,
        )
    }
}
