package com.cinch.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cinch.shared.coach.NotificationGroup
import com.cinch.shared.coach.NotificationPreferences
import com.cinch.ui.common.BackButton
import kotlinx.datetime.LocalTime
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun NotificationSettingsWrapper(onBack: () -> Unit, viewModel: NotificationSettingsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    NotificationSettingsScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    state: NotificationSettingsState,
    onEvent: (NotificationSettingsEvent) -> Unit,
    onBack: () -> Unit,
) {
    val prefs = state.preferences
    Scaffold(topBar = { TopAppBar(title = { Text("Bildirim tercihleri") }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SectionTitle("Ne zaman")
            ListItem(
                headlineContent = { Text("Sessiz saatler") },
                supportingContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Bu saatlere düşen bildirim, sessiz saat bitince gelir.")
                        if (state.quietEnabled) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AssistChip(
                                    onClick = { onEvent(NotificationSettingsEvent.EditTime(TimeField.QUIET_START)) },
                                    label = { Text(prefs.quietHours.start.label()) },
                                )
                                Text("–")
                                AssistChip(
                                    onClick = { onEvent(NotificationSettingsEvent.EditTime(TimeField.QUIET_END)) },
                                    label = { Text(prefs.quietHours.end.label()) },
                                )
                            }
                        }
                    }
                },
                trailingContent = {
                    Switch(checked = state.quietEnabled, onCheckedChange = { onEvent(NotificationSettingsEvent.QuietToggled(it)) })
                },
            )
            ListItem(
                headlineContent = { Text("Günün payı saati") },
                supportingContent = { Text("\"Bugün ne kadar harcayabilirsin\" bildirimi") },
                trailingContent = {
                    TextButton(onClick = { onEvent(NotificationSettingsEvent.EditTime(TimeField.MIDDAY)) }) {
                        Text(prefs.middayTime.label())
                    }
                },
            )
            ListItem(
                headlineContent = { Text("Günde en fazla") },
                supportingContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Fazlası olursa önemli olanlar kalır: ödeme günü, sınıra yaklaşma.")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NotificationPreferences.MAX_PER_DAY_RANGE.forEach { count ->
                                FilterChip(
                                    selected = prefs.maxPerDay == count,
                                    onClick = { onEvent(NotificationSettingsEvent.MaxPerDayPicked(count)) },
                                    label = { Text("$count") },
                                )
                            }
                        }
                    }
                },
            )

            HorizontalDivider(Modifier.padding(top = 8.dp))
            SectionTitle("Hangi bildirimler")
            NotificationGroup.entries.forEach { group ->
                val enabled = group !in prefs.disabled
                ListItem(
                    headlineContent = { Text(group.title) },
                    supportingContent = { Text(group.description) },
                    trailingContent = {
                        Switch(checked = enabled, onCheckedChange = { onEvent(NotificationSettingsEvent.GroupToggled(group, it)) })
                    },
                )
            }
            Text(
                "Bildirimler telefonda planlanır; değişiklik hemen geçerli olur.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }

    state.editing?.let { field ->
        TimeDialog(
            initial = state.timeOf(field),
            title = when (field) {
                TimeField.QUIET_START -> "Sessiz saat başlangıcı"
                TimeField.QUIET_END -> "Sessiz saat bitişi"
                TimeField.MIDDAY -> "Günün payı saati"
            },
            onPicked = { onEvent(NotificationSettingsEvent.TimePicked(it)) },
            onDismiss = { onEvent(NotificationSettingsEvent.TimeDismissed) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initial: LocalTime, title: String, onPicked: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val pickerState = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = pickerState) },
        confirmButton = {
            TextButton(onClick = { onPicked(LocalTime(pickerState.hour, pickerState.minute)) }) { Text("Tamam") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

private fun LocalTime.label(): String = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

val NotificationGroup.title: String
    get() = when (this) {
        NotificationGroup.DAILY_SHARE -> "Günün payı"
        NotificationGroup.LOG_REMINDER -> "Kayıt hatırlatması"
        NotificationGroup.LIMIT_WARNINGS -> "Sınır uyarıları"
        NotificationGroup.WEEKLY -> "Haftalık kapanış ve kumbara"
        NotificationGroup.HABITS -> "Seri ve alışkanlıklar"
        NotificationGroup.PAYMENTS -> "Ödeme günleri"
        NotificationGroup.SALARY -> "Maaş günü"
        NotificationGroup.BACKUP -> "Yedek hatırlatması"
    }

val NotificationGroup.description: String
    get() = when (this) {
        NotificationGroup.DAILY_SHARE -> "Her gün bugün ne kadar harcayabileceğin"
        NotificationGroup.LOG_REMINDER -> "Bir gün hiç harcama girmediysen ertesi gün; bildirimden yazıp kaydedebilirsin"
        NotificationGroup.LIMIT_WARNINGS -> "Haftalık sınıra yaklaşınca, bir kategoride geçen haftadan fazla gidince"
        NotificationGroup.WEEKLY -> "Hafta sonunda özet, yeni haftada artanı kumbaraya atma"
        NotificationGroup.HABITS -> "Serin uzayınca, sık tekrarlanan harcamalarda"
        NotificationGroup.PAYMENTS -> "Sabit giderler, kart son ödemesi, ekstre günü"
        NotificationGroup.SALARY -> "Maaş yatınca ayı gözden geçirme"
        NotificationGroup.BACKUP -> "Uzun süredir yedek almadıysan"
    }
