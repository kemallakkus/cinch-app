package com.cinch.notifications

import com.cinch.shared.coach.CoachNotification
import com.cinch.shared.coach.NotificationType

/** Platformun yerel bildirim zamanlayıcısı (Android WorkManager, iOS UNUserNotificationCenter). */
interface NotificationScheduler {
    /** Henüz gösterilmemiş koç bildirimlerinin anahtarları ([scheduleKey]). */
    suspend fun pendingKeys(): Set<String>

    suspend fun cancel(keys: Set<String>)

    /** Bildirimler kendi tarih ve saatlerinde (cihazın yerel saatiyle) gösterilir. */
    suspend fun schedule(notifications: List<CoachNotification>)
}

const val COACH_KEY_PREFIX = "coach-"

/**
 * Bildirimin kimliği. Mesaj da dahildir: iOS'ta içerik eklendiği an sabitlenir, mesaj değişince
 * (ör. günlük pay) eski bildirim iptal edilip yenisi eklenmelidir.
 */
fun CoachNotification.scheduleKey(): String =
    "$COACH_KEY_PREFIX$date-$type-$time-${message.hashCode().toUInt().toString(16)}"

/** Bildirim başlığı; gövde [CoachNotification.message]. */
val NotificationType.title: String
    get() = when (this) {
        NotificationType.MIDDAY_REMAINING -> "Günün payı"
        NotificationType.FORGOT_LOG -> "Kayıt hatırlatması"
        NotificationType.APPROACHING_LIMIT -> "Sınıra yaklaşıyorsun"
        NotificationType.WEEKLY_CLOSING -> "Haftalık kapanış"
        NotificationType.HABIT_ALERT -> "Alışkanlık"
        NotificationType.STREAK -> "Seri"
        NotificationType.STATEMENT_DUE -> "Ekstre günü"
        NotificationType.SALARY -> "Maaş günü"
    }
