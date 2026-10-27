package com.cinch.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cinch.shared.coach.CoachNotification
import com.cinch.shared.coach.CoachNotificationPlanner
import com.cinch.shared.coach.NotificationType
import kotlinx.datetime.LocalDate
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit

/**
 * Zamanı gelen koç bildirimini gösterir. Mesaj kuyruğa girdiği anki veriyle değil, şimdiki veriyle
 * yeniden üretilir; kural artık geçerli değilse ya da gün geçtiyse hiçbir şey gösterilmez.
 */
class CoachNotificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // WorkManager süreci Application.onCreate bitmeden uyandırabilir; Koin hazır değilse sonra dene.
        val koin = GlobalContext.getOrNull() ?: return Result.retry()
        val type = inputData.getString(KEY_TYPE)?.let { name -> NotificationType.entries.firstOrNull { it.name == name } }
        val date = inputData.getString(KEY_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (type == null || date == null) return Result.success()

        val notification = koin.get<CoachNotificationPlanner>().resolveDue(type, date) ?: return Result.success()
        CoachNotifier.show(applicationContext, notification)
        return Result.success()
    }

    companion object {
        private const val KEY_TYPE = "type"
        private const val KEY_DATE = "date"

        fun inputFor(notification: CoachNotification): Data =
            workDataOf(KEY_TYPE to notification.type.name, KEY_DATE to notification.date.toString())
    }
}

/**
 * Günde bir planı yeniden kurar. Kullanıcı uygulamayı günlerce açmasa da 7 günlük ufuk ileri kayar
 * ve bildirimler kesilmez.
 */
class CoachReplanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val koin = GlobalContext.getOrNull() ?: return Result.retry()
        koin.get<CoachNotificationSync>().sync()
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "cinch_coach_replan"

        /** Uygulama açılışında çağrılır; iş zaten kuruluysa dokunmaz. */
        fun ensureScheduled(context: Context) {
            val request = PeriodicWorkRequestBuilder<CoachReplanWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
