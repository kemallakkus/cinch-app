package com.cinch.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.cinch.shared.coach.CoachNotification
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * Her bildirim, zamanı gelince çalışan tek seferlik bir WorkManager işi. WorkManager işleri yeniden
 * başlatmadan sonra da kendisi geri kurar. Zamanlama tam dakika değildir: Doze'da birkaç dakika kayabilir.
 */
class AndroidNotificationScheduler(private val context: Context) : NotificationScheduler {

    private val workManager get() = WorkManager.getInstance(context)

    override suspend fun pendingKeys(): Set<String> =
        workManager.getWorkInfosByTagFlow(WORK_TAG).first()
            // Çalışan iş "bekleyen" sayılmaz: eşitleme onu iptal etmemeli.
            .filter { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }
            .flatMap { it.tags }
            .filter { it.startsWith(COACH_KEY_PREFIX) }
            .toSet()

    override suspend fun cancel(keys: Set<String>) {
        keys.forEach { workManager.cancelUniqueWork(it) }
    }

    override suspend fun schedule(notifications: List<CoachNotification>) {
        val now = Clock.System.now()
        val zone = TimeZone.currentSystemDefault()
        notifications.forEach { notification ->
            val at = LocalDateTime(notification.date, notification.time).toInstant(zone)
            val delay = (at - now).coerceAtLeast(Duration.ZERO)
            val key = notification.scheduleKey()
            val request = OneTimeWorkRequestBuilder<CoachNotificationWorker>()
                .setInitialDelay(delay.inWholeMilliseconds, TimeUnit.MILLISECONDS)
                .setInputData(CoachNotificationWorker.inputFor(notification))
                .addTag(WORK_TAG)
                .addTag(key)
                .build()
            workManager.enqueueUniqueWork(key, ExistingWorkPolicy.KEEP, request)
        }
    }

    private companion object {
        // COACH_KEY_PREFIX ile başlamamalı; yoksa etiketin kendisi bir bildirim anahtarı sanılır.
        const val WORK_TAG = "cinch_coach_notification"
    }
}
