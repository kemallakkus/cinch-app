package com.cinch.ui.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cinch.notifications.rememberNotificationPermissionRequest
import com.cinch.notifications.rememberOpenNotificationSettings
import com.cinch.shared.coach.CoachConfig
import org.koin.compose.viewmodel.koinViewModel

/** Ana ekrana yerleşen bildirim izni kartı; izin varsa ya da ertelendiyse hiçbir şey çizmez. */
@Composable
fun NotificationPromptWrapper(viewModel: NotificationPromptViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val requestPermission = rememberNotificationPermissionRequest {
        viewModel.onEvent(NotificationPromptEvent.PermissionResult)
    }
    val openSettings = rememberOpenNotificationSettings()

    LifecycleResumeEffect(viewModel) {
        viewModel.onEvent(NotificationPromptEvent.Refresh)
        onPauseOrDispose { }
    }

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                NotificationPromptEffect.RequestPermission -> requestPermission()
                NotificationPromptEffect.OpenSettings -> openSettings()
            }
        }
    }

    state.prompt?.let { prompt -> NotificationPromptCard(prompt = prompt, onEvent = viewModel::onEvent) }
}

@Composable
private fun NotificationPromptCard(prompt: NotificationPrompt, onEvent: (NotificationPromptEvent) -> Unit) {
    val config = CoachConfig()
    val (title, body, action) = when (prompt) {
        NotificationPrompt.ASK -> Triple(
            "🔔 Koç seni dürtsün mü?",
            "Günlük payını, unuttuğun kayıtları ve haftalık özeti bildirimle hatırlatayım. " +
                "Günde en fazla ${config.maxPerDay} bildirim, " +
                "${config.quietHours.start}–${config.quietHours.end} arası sessiz.",
            "Bildirimlere izin ver",
        )
        NotificationPrompt.OPEN_SETTINGS -> Triple(
            "🔕 Bildirimler kapalı",
            "Koçun hatırlatmalarını almak için ayarlardan Cinch bildirimlerini açabilirsin.",
            "Ayarları aç",
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = { onEvent(NotificationPromptEvent.Snooze) }) { Text("Şimdi değil") }
                Button(onClick = { onEvent(NotificationPromptEvent.Accept) }) { Text(action) }
            }
        }
    }
}
