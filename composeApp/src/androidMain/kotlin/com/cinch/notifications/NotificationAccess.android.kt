package com.cinch.notifications

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.cinch.shared.prefs.KeyValueStore

/**
 * Android 13+'ta sistem penceresi ilk istekte güvenle gösterilebilir; reddedildikten sonra sistem
 * pencereyi kendiliğinden bastırabilir. Bu yüzden bir kez sorulduktan sonra ayarlara yönlendirilir.
 */
class AndroidNotificationAccess(
    private val context: Context,
    private val prefs: KeyValueStore,
) : NotificationAccessChecker {

    override suspend fun current(): NotificationAccess = when {
        canPostNotifications(context) -> NotificationAccess.GRANTED
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !prefs.getBoolean(KEY_REQUESTED) ->
            NotificationAccess.ASKABLE
        else -> NotificationAccess.DENIED
    }

    override fun markRequested() {
        prefs.putBoolean(KEY_REQUESTED, true)
    }

    private companion object {
        const val KEY_REQUESTED = "notification_permission_requested"
    }
}

@Composable
actual fun rememberNotificationPermissionRequest(onResult: () -> Unit): () -> Unit {
    val currentOnResult by rememberUpdatedState(onResult)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        // 13 öncesinde çalışma zamanı izni yok; bildirim ayarlardan kapatılmadıkça açıktır.
        return remember { { currentOnResult() } }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        currentOnResult()
    }
    return remember(launcher) { { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) } }
}

@Composable
actual fun rememberOpenNotificationSettings(): () -> Unit {
    val context = LocalContext.current
    return remember(context) { { context.startActivity(notificationSettingsIntent(context)) } }
}

private fun notificationSettingsIntent(context: Context): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    }
