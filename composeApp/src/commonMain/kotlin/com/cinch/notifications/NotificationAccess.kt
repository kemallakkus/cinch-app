package com.cinch.notifications

import androidx.compose.runtime.Composable

enum class NotificationAccess {
    GRANTED,

    /** Sistem izin penceresi gösterilebilir. */
    ASKABLE,

    /** Kullanıcı reddetti ya da kapattı; yalnız sistem ayarlarından açılabilir. */
    DENIED,
}

interface NotificationAccessChecker {
    suspend fun current(): NotificationAccess

    /** Sistem penceresi açılmadan hemen önce çağrılır (Android bir sonraki seferi ayırt etmek için tutar). */
    fun markRequested()
}

/** Sistem izin penceresini açan bir fonksiyon döner; pencere kapanınca [onResult] çağrılır. */
@Composable
expect fun rememberNotificationPermissionRequest(onResult: () -> Unit): () -> Unit

/** Uygulamanın sistem bildirim ayarlarını açan bir fonksiyon döner. */
@Composable
expect fun rememberOpenNotificationSettings(): () -> Unit
