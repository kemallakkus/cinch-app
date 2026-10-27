package com.cinch.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

/** iOS izni sistem kendisi tutar: "henüz sorulmadı" ile "reddedildi" ayrımı doğrudan okunur. */
class IosNotificationAccess : NotificationAccessChecker {

    override suspend fun current(): NotificationAccess = suspendCancellableCoroutine { cont ->
        UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
            val access = when (settings?.authorizationStatus) {
                UNAuthorizationStatusAuthorized,
                UNAuthorizationStatusProvisional,
                UNAuthorizationStatusEphemeral,
                -> NotificationAccess.GRANTED
                UNAuthorizationStatusNotDetermined -> NotificationAccess.ASKABLE
                else -> NotificationAccess.DENIED
            }
            cont.resume(access)
        }
    }

    override fun markRequested() = Unit
}

@Composable
actual fun rememberNotificationPermissionRequest(onResult: () -> Unit): () -> Unit {
    val currentOnResult by rememberUpdatedState(onResult)
    return remember {
        {
            UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
                UNAuthorizationOptionAlert or UNAuthorizationOptionSound,
            ) { _, _ ->
                // Sonuç arka plan kuyruğunda gelir; UI olayı ana kuyrukta işlenir.
                dispatch_async(dispatch_get_main_queue()) { currentOnResult() }
            }
        }
    }
}

@Composable
actual fun rememberOpenNotificationSettings(): () -> Unit = remember {
    {
        NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let { url ->
            UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
        }
    }
}
