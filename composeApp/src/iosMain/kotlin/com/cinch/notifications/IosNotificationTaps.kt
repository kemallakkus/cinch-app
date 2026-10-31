package com.cinch.notifications

import com.cinch.navigation.DeepLinks
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionList
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject

/**
 * Bildirime dokununca ilgili ekran (bildirimin `userInfo`sundaki bağlantı, [link]). Uygulama öndeyken gelen
 * bildirim de gösterilir; iOS varsayılanı onu sessizce yutmak.
 */
object IosNotificationTaps {

    internal const val LINK_KEY = "link"

    // UNUserNotificationCenter delegesini zayıf tutar; burada güçlü tutulur.
    private val delegate = TapDelegate()

    /** Uygulama açılışında bir kez (Swift: `iOSApp.init`). */
    fun install() {
        UNUserNotificationCenter.currentNotificationCenter().delegate = delegate
    }
}

private class TapDelegate : NSObject(), UNUserNotificationCenterDelegateProtocol {

    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        didReceiveNotificationResponse: UNNotificationResponse,
        withCompletionHandler: () -> Unit,
    ) {
        val link = didReceiveNotificationResponse.notification.request.content.userInfo[IosNotificationTaps.LINK_KEY] as? String
        link?.let(DeepLinks::open)
        withCompletionHandler()
    }

    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: UNNotification,
        withCompletionHandler: (ULong) -> Unit,
    ) {
        withCompletionHandler(
            UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionList or UNNotificationPresentationOptionSound,
        )
    }
}
