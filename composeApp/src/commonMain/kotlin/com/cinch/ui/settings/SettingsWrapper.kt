package com.cinch.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cinch.notifications.NotificationAccess
import com.cinch.notifications.rememberNotificationPermissionRequest
import com.cinch.notifications.rememberOpenNotificationSettings
import com.cinch.shared.coach.CoachConfig
import com.cinch.ui.common.display
import com.cinch.files.rememberFileTransfer
import com.cinch.ui.common.longLabel
import com.cinch.ui.common.shortLabel
import com.cinch.ui.theme.CinchTheme
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsWrapper(
    onOpenBudget: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val requestPermission = rememberNotificationPermissionRequest { viewModel.onEvent(SettingsEvent.Refresh) }
    val openSettings = rememberOpenNotificationSettings()
    val snackbarHostState = remember { SnackbarHostState() }
    val files = rememberFileTransfer(
        onSaved = { viewModel.onEvent(SettingsEvent.FileSaved(it)) },
        onOpened = { viewModel.onEvent(SettingsEvent.FileOpened(it)) },
    )

    LifecycleResumeEffect(viewModel) {
        viewModel.onEvent(SettingsEvent.Refresh)
        onPauseOrDispose { }
    }
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                SettingsEffect.RequestPermission -> requestPermission()
                SettingsEffect.OpenSystemSettings -> openSettings()
                is SettingsEffect.SaveFile -> files.save(effect.name, effect.mimeType, effect.content)
                SettingsEffect.OpenFile -> files.open()
                is SettingsEffect.Message -> launch { snackbarHostState.showSnackbar(effect.text) }
            }
        }
    }

    SettingsScreen(
        state = state,
        onOpenBudget = onOpenBudget,
        onEvent = viewModel::onEvent,
        backupSupported = files.isSupported,
        snackbarHostState = snackbarHostState,
    )
}

/** @param backupSupported platformda dosya seçici var mı (iOS'ta henüz yok: yedek bölümü gizlenir). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsState,
    onOpenBudget: () -> Unit,
    onEvent: (SettingsEvent) -> Unit,
    backupSupported: Boolean = true,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Ayarlar") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
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
                    Text(
                        "Her şey yalnız bu telefonda saklanır. Hesap açman gerekmez, hiçbir sunucuya gönderilmez. " +
                            "Telefon kaybolursa gitmesin diye ara ara yedek al.",
                    )
                },
            )
            if (backupSupported) BackupSection(state, onEvent)
        }
    }

    state.pendingRestore?.let { summary ->
        AlertDialog(
            onDismissRequest = { onEvent(SettingsEvent.RestoreDismissed) },
            title = { Text("Yedek geri yüklensin mi?") },
            text = {
                Text(
                    "${backupDateLabel(summary.createdAt)} alınmış yedek: ${summary.expenses} harcama, ${summary.cards} kart, " +
                        "${summary.fixedExpenses} sabit gider, ${summary.incomes} gelir.\n\n" +
                        "Telefondaki mevcut verilerin silinip bunlarla değiştirilecek.",
                )
            },
            confirmButton = {
                TextButton(onClick = { onEvent(SettingsEvent.RestoreConfirmed) }) {
                    Text("Geri yükle", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { onEvent(SettingsEvent.RestoreDismissed) }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun BackupSection(state: SettingsState, onEvent: (SettingsEvent) -> Unit) {
    val enabled = !state.isWorking
    HorizontalDivider()
    Text(
        "Yedek ve dışa aktarma",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
    ListItem(
        modifier = Modifier.clickable(enabled = enabled) { onEvent(SettingsEvent.BackupClicked) },
        leadingContent = { Icon(Icons.Outlined.Backup, contentDescription = null) },
        headlineContent = { Text("Yedek al") },
        supportingContent = {
            val days = state.lastBackup?.let { last -> state.today?.let { last.daysUntil(it) } }
            Text(
                when {
                    state.lastBackup == null -> "Henüz yedek almadın. Tek dosya; Drive'a ya da telefona kaydedebilirsin."
                    days == 0 -> "Son yedek bugün ✓"
                    days != null && days >= BACKUP_STALE_DAYS -> "Son yedek $days gün önce, yenisini al"
                    else -> "Son yedek ${state.lastBackup.shortLabel()} ($days gün önce)"
                },
                color = if (state.lastBackup == null || (days ?: 0) >= BACKUP_STALE_DAYS) {
                    CinchTheme.colors.caution
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
    )
    ListItem(
        modifier = Modifier.clickable(enabled = enabled) { onEvent(SettingsEvent.RestoreClicked) },
        leadingContent = { Icon(Icons.Outlined.SettingsBackupRestore, contentDescription = null) },
        headlineContent = { Text("Yedekten geri yükle") },
        supportingContent = { Text("Telefon değiştirdiysen ya da uygulamayı yeniden kurduysan") },
    )
    ListItem(
        modifier = Modifier.clickable(enabled = enabled) { onEvent(SettingsEvent.ExportCsvClicked) },
        leadingContent = { Icon(Icons.Outlined.TableChart, contentDescription = null) },
        headlineContent = { Text("Harcamaları Excel'e aktar") },
        supportingContent = { Text("CSV dosyası: tarih, tutar, kategori, kart, açıklama") },
    )
}

/** Bu kadar gün yedek alınmadıysa uyarı rengiyle gösterilir (koç da hatırlatır). */
private const val BACKUP_STALE_DAYS = 30

/** "2026-10-12T14:22:05" → "12 Eki 2026". */
private fun backupDateLabel(createdAt: String): String =
    runCatching { LocalDate.parse(createdAt.take(10)) }.getOrNull()?.let { "${it.shortLabel()} ${it.year} tarihinde" }
        ?: "Daha önce"

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
