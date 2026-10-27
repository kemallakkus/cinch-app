package com.cinch.notifications

import com.cinch.shared.coach.CoachNotification
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.number
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

/**
 * Yerel takvim tetikleyicili bildirimler. İçerik eklendiği anda sabitlenir; bu yüzden plan her açılışta
 * ve her veri değişikliğinde yeniden eşitlenir (mesaj değişince anahtar da değişir). iOS uygulama başına
 * en fazla 64 bekleyen bildirim tutar; 7 günlük plan bunun çok altında.
 */
class IosNotificationScheduler : NotificationScheduler {

    private val center get() = UNUserNotificationCenter.currentNotificationCenter()

    override suspend fun pendingKeys(): Set<String> = suspendCancellableCoroutine { cont ->
        center.getPendingNotificationRequestsWithCompletionHandler { requests ->
            val keys = requests.orEmpty()
                .filterIsInstance<UNNotificationRequest>()
                .map { it.identifier }
                .filter { it.startsWith(COACH_KEY_PREFIX) }
                .toSet()
            cont.resume(keys)
        }
    }

    override suspend fun cancel(keys: Set<String>) {
        center.removePendingNotificationRequestsWithIdentifiers(keys.toList())
    }

    override suspend fun schedule(notifications: List<CoachNotification>) {
        notifications.forEach { add(it) }
    }

    private suspend fun add(notification: CoachNotification) {
        val content = UNMutableNotificationContent().apply {
            setTitle(notification.type.title)
            setBody(notification.message)
            setSound(UNNotificationSound.defaultSound())
        }
        // Saat dilimi verilmez: tetikleyici cihazın o anki yerel saatine göre çalışır.
        val components = NSDateComponents().apply {
            year = notification.date.year.toLong()
            month = notification.date.month.number.toLong()
            day = notification.date.day.toLong()
            hour = notification.time.hour.toLong()
            minute = notification.time.minute.toLong()
        }
        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = false)
        val request = UNNotificationRequest.requestWithIdentifier(notification.scheduleKey(), content, trigger)
        suspendCancellableCoroutine { cont ->
            center.addNotificationRequest(request) { _ -> cont.resume(Unit) }
        }
    }
}
