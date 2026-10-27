package com.cinch.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cinch.app.R
import com.cinch.shared.coach.CoachNotification

internal object CoachNotifier {

    private const val CHANNEL_ID = "coach"
    private const val NOTIFICATION_TAG = "coach"

    // İzin hemen üstte canPostNotifications ile denetleniyor; lint ayrı fonksiyonu göremiyor.
    @SuppressLint("MissingPermission")
    fun show(context: Context, notification: CoachNotification) {
        if (!canPostNotifications(context)) return
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Koç")
                .setDescription("Günlük pay, kayıt hatırlatmaları ve haftalık özet")
                .build(),
        )

        val built = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_coach)
            .setContentTitle(notification.type.title)
            .setContentText(notification.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.message))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()

        // Tür başına tek bildirim: dünkü "Günün payı" okunmadıysa yenisiyle değişir.
        manager.notify(NOTIFICATION_TAG, notification.type.ordinal, built)
    }

    private fun openAppIntent(context: Context): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(
            context,
            0,
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

/** Android 13+ çalışma zamanı izni var mı ve kullanıcı uygulamanın bildirimlerini kapatmamış mı. */
internal fun canPostNotifications(context: Context): Boolean {
    val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
}
