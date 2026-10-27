package com.cinch.shared.coach

import com.cinch.shared.parser.minusDays
import com.cinch.shared.time.LocalClock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * Koç planını platform zamanlayıcısına hazırlar.
 *
 * Plan kurulduğu andaki veriyle üretilir. Android bildirimi gösterirken [resolveDue] ile mesajı o anki
 * veriyle yeniden üretir; iOS'ta içerik eklendiği anda sabitlendiği için plan her açılışta ve her veri
 * değişikliğinde yeniden kurulur.
 */
class CoachNotificationPlanner(
    private val plan: PlanCoachWeek,
    private val clock: LocalClock,
) {

    /** Zamanlanacak bildirimler: şu andan sonrakiler, zamana göre sıralı. Bütçe yoksa boş. */
    suspend fun upcoming(): List<CoachNotification> {
        val now = clock.now()
        return plan(now.date)
            .filter { it.at > now }
            .sortedBy { it.at }
    }

    /**
     * Zamanı gelen bildirimin güncel hâli. Gün geçtiyse ya da kural artık geçerli değilse
     * (ör. sınıra yaklaşma uyarısı ama harcama silindi) null: bildirim gösterilmez.
     */
    suspend fun resolveDue(type: NotificationType, date: LocalDate): CoachNotification? {
        val today = clock.today()
        if (date != today) return null
        // "Kayıt unuttun" dünü anlatır: kural dünün gözünden kurulur ("dün hiç kayıt yok mu?").
        val asOf = if (type == NotificationType.FORGOT_LOG) today.minusDays(1) else today
        return plan(asOf).firstOrNull { it.type == type && it.date == today }
    }

    private val CoachNotification.at: LocalDateTime get() = LocalDateTime(date, time)
}
