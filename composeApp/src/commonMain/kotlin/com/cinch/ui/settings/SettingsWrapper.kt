package com.cinch.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cinch.notifications.NotificationAccess
import com.cinch.notifications.rememberNotificationPermissionRequest
import com.cinch.notifications.rememberOpenNotificationSettings
import com.cinch.shared.coach.CoachConfig
import com.cinch.ui.common.display
import com.cinch.ui.common.longLabel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsWrapper(
    onOpenBudget: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val requestPermission = rememberNotificationPermissionRequest { viewModel.onEvent(SettingsEvent.Refresh) }
    val openSettings = rememberOpenNotificationSettings()

    LifecycleResumeEffect(viewModel) {
        viewModel.onEvent(SettingsEvent.Refresh)
        onPauseOrDispose { }
    }
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                SettingsEffect.RequestPermission -> requestPermission()
                SettingsEffect.OpenSystemSettings -> openSettings()
            }
        }
    }

    SettingsScreen(state = state, onOpenBudget = onOpenBudget, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: SettingsState, onOpenBudget: () -> Unit, onEvent: (SettingsEvent) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Ayarlar") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            ListItem(
                modifier = Modifier.clickable(onClick = onOpenBudget),
                leadingContent = { Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null) },
                headlineContent = { Text("Haftalık bütçe") },
                supportingContent = {
                    Text(
                        state.budget?.let { "${it.weeklyLimit.display()} · hafta ${it.weekStart.longLabel.lowercase()} başlar" }
                            ?: "Henüz kurulmadı",
                    )
                },
            )
            HorizontalDivider()
            ListItem(
                modifier = Modifier.clickable { onEvent(SettingsEvent.NotificationsClicked) },
                leadingContent = { Icon(Icons.Outlined.Notifications, contentDescription = null) },
                headlineContent = { Text("Koç bildirimleri") },
                supportingContent = { Text(notificationsLabel(state.notifications)) },
            )
            HorizontalDivider()
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                headlineContent = { Text("Verilerin") },
                supportingContent = {
                    Text("Her şey yalnız bu telefonda saklanır. Hesap açman gerekmez, hiçbir sunucuya gönderilmez.")
                },
            )
        }
    }
}

private fun notificationsLabel(access: NotificationAccess?): String {
    val config = CoachConfig()
    return when (access) {
        NotificationAccess.GRANTED ->
            "Açık · günde en fazla ${config.maxPerDay}, ${config.quietHours.start}–${config.quietHours.end} arası sessiz"
        NotificationAccess.ASKABLE -> "Kapalı · açmak için dokun"
        NotificationAccess.DENIED -> "Kapalı · dokun, telefon ayarlarından aç"
        null -> ""
    }
}
