package com.cinch.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.cinch.app.MainActivity
import com.cinch.app.R
import com.cinch.shared.coach.CoachNotification
import com.cinch.shared.coach.NotificationType

internal object CoachNotifier {

    private const val CHANNEL_ID = "coach"
    private const val NOTIFICATION_TAG = "coach"

    /** Altında "Harcama yaz" yanıt alanı olan bildirimler: uygulamayı açmadan kayıt. */
    private val REPLYABLE = setOf(NotificationType.FORGOT_LOG, NotificationType.MIDDAY_REMAINING)

    // İzin hemen üstte canPostNotifications ile denetleniyor; lint ayrı fonksiyonu göremiyor.
    @SuppressLint("MissingPermission")
    fun show(context: Context, notification: CoachNotification) {
        if (!canPostNotifications(context)) return
        val manager = NotificationManagerCompat.from(context)
        ensureChannel(manager)

        val builder = base(context, notification.type)
            .setContentTitle(notification.type.title)
            .setContentText(notification.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.message))
        if (notification.type in REPLYABLE) builder.addAction(replyAction(context, notification.type))

        // Tür başına tek bildirim: dünkü "Günün payı" okunmadıysa yenisiyle değişir.
        manager.notify(NOTIFICATION_TAG, notification.type.ordinal, builder.build())
    }

    /**
     * Yanıt alanından gelen kaydın sonucu aynı bildirimin yerine yazılır (Android yanıttan sonra bildirimi
     * güncellemeyi bekler, yoksa dönen çark kalır). Kaydedilmediyse yanıt alanı yeniden konur.
     */
    @SuppressLint("MissingPermission")
    fun showReplyResult(context: Context, type: NotificationType, message: String, saved: Boolean) {
        if (!canPostNotifications(context)) return
        val manager = NotificationManagerCompat.from(context)
        ensureChannel(manager)
        val builder = base(context, type)
            .setContentTitle(if (saved) "Kaydedildi ✓" else type.title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setOnlyAlertOnce(true)
        if (saved) builder.setTimeoutAfter(SAVED_VISIBLE_MS) else builder.addAction(replyAction(context, type))
        manager.notify(NOTIFICATION_TAG, type.ordinal, builder.build())
    }

    private fun ensureChannel(manager: NotificationManagerCompat) {
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Koç")
                .setDescription("Günlük pay, kayıt hatırlatmaları ve haftalık özet")
                .build(),
        )
    }

    private fun base(context: Context, type: NotificationType): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_coach)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openIntent(context, type))
            .setAutoCancel(true)

    /** Dokununca ilgili ekran: [link] (kumbara → Kumbara, kart son ödeme → Kartlar, "dün kayıt yoktu" → hızlı kayıt). */
    private fun openIntent(context: Context, type: NotificationType): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse(type.link))
            // Uygulama açıksa yeni ekran yığını kurulmaz; bağlantı mevcut ekrana onNewIntent ile gelir.
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            type.ordinal,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun replyAction(context: Context, type: NotificationType): NotificationCompat.Action {
        val input = RemoteInput.Builder(QuickReplyReceiver.KEY_TEXT)
            .setLabel("ör. 195 market")
            .build()
        val intent = Intent(context, QuickReplyReceiver::class.java).putExtra(QuickReplyReceiver.EXTRA_TYPE, type.name)
        // Yanıt alanı yazılan metni intent'e ekler: PendingIntent değiştirilebilir olmalı.
        val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val pending = PendingIntent.getBroadcast(context, type.ordinal, intent, mutable or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Action.Builder(R.drawable.ic_stat_coach, "Harcama yaz", pending)
            .addRemoteInput(input)
            .setAllowGeneratedReplies(false)
            .build()
    }

    private const val SAVED_VISIBLE_MS = 6_000L
}

/** Android 13+ çalışma zamanı izni var mı ve kullanıcı uygulamanın bildirimlerini kapatmamış mı. */
internal fun canPostNotifications(context: Context): Boolean {
    val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
}
