package com.cinch.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.cinch.shared.coach.NotificationType
import com.cinch.shared.time.LocalClock
import com.cinch.widget.SaveQuickEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named

/**
 * Bildirimdeki "Harcama yaz" yanıtı: uygulamayı açmadan kaydeder, sonucu aynı bildirime yazar. "Dün kayıt
 * yoktu"ya yanıtta tarih yazılmadıysa ("120 taksi") harcama düne yazılır.
 */
class QuickReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra(EXTRA_TYPE)?.let { name -> NotificationType.entries.firstOrNull { it.name == name } } ?: return
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_TEXT)?.toString()?.trim().orEmpty()
        val koin = GlobalContext.getOrNull() ?: return
        val app = context.applicationContext
        val pending = goAsync()
        koin.get<CoroutineScope>(named("appScope")).launch {
            try {
                val clock = koin.get<LocalClock>()
                val fallback = if (type == NotificationType.FORGOT_LOG) clock.today().minus(DatePeriod(days = 1)) else null
                val saved = if (text.isEmpty()) null else SaveQuickEntry(koin.get(), koin.get(), koin.get(), clock).invoke(text, fallback)
                CoachNotifier.showReplyResult(app, type, SaveQuickEntry.message(text, saved), saved = saved != null)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val KEY_TEXT = "quick_entry_text"
        const val EXTRA_TYPE = "notification_type"
    }
}
