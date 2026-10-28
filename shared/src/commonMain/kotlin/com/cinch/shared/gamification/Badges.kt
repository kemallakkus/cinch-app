package com.cinch.shared.gamification

import com.cinch.shared.prefs.KeyValueStore
import kotlinx.datetime.LocalDate

/** Rozetler. Görünen ad ve açıklama arayüz katmanında; burada yalnız kural. */
enum class Badge {
    FIRST_ENTRY,     // ilk kayıt
    STREAK_3,        // 3 gün sınırda
    STREAK_7,        // 7 gün sınırda
    STREAK_30,       // 30 gün sınırda
    LOGGED_7,        // 7 gün üst üste kayıt
    WEEK_ON_TRACK,   // bir haftayı sınırın altında kapatmak
    FIRST_SAVING,    // kumbaraya ilk para
    GOAL_REACHED,    // bir hedefe ulaşmak
    DEBT_FINISHED,   // bir borcu bitirmek
    BACKUP_TAKEN,    // ilk yedek
}

/** Rozet hesabının girdileri; hepsi veriden türetilir. */
data class BadgeInputs(
    val expenseCount: Int,
    val streaks: Streaks,
    val depositCount: Int,
    val goalsReached: Int,
    val debtsFinished: Int,
    val hasBackup: Boolean,
)

/** @param progress kilitliyse ilerleme (ör. 4/7 gün); ilerlemesi olmayanlarda null. */
data class BadgeState(val badge: Badge, val earnedOn: LocalDate?, val progress: Pair<Int, Int>?) {
    val isEarned: Boolean get() = earnedOn != null
}

object Badges {

    fun earned(input: BadgeInputs): Set<Badge> = Badge.entries.filter { badge ->
        val (now, goal) = target(badge, input)
        now >= goal
    }.toSet()

    /** (şu anki, gereken). Seriler için en iyi değer: bir kez ulaşıldıysa kazanılmıştır. */
    fun target(badge: Badge, input: BadgeInputs): Pair<Int, Int> = when (badge) {
        Badge.FIRST_ENTRY -> input.expenseCount.coerceAtMost(1) to 1
        Badge.STREAK_3 -> input.streaks.bestBudgetStreak.coerceAtMost(3) to 3
        Badge.STREAK_7 -> input.streaks.bestBudgetStreak.coerceAtMost(7) to 7
        Badge.STREAK_30 -> input.streaks.bestBudgetStreak.coerceAtMost(30) to 30
        Badge.LOGGED_7 -> input.streaks.bestLoggingStreak.coerceAtMost(7) to 7
        Badge.WEEK_ON_TRACK -> input.streaks.weeksOnTrack.coerceAtMost(1) to 1
        Badge.FIRST_SAVING -> input.depositCount.coerceAtMost(1) to 1
        Badge.GOAL_REACHED -> input.goalsReached.coerceAtMost(1) to 1
        Badge.DEBT_FINISHED -> input.debtsFinished.coerceAtMost(1) to 1
        Badge.BACKUP_TAKEN -> (if (input.hasBackup) 1 else 0) to 1
    }
}

/**
 * Kazanılan rozetler ve kazanıldıkları gün. Bir kez kazanılan rozet kalır: 7 günlük seri bozulsa da "7 gün sınırda"
 * gitmez. Yeni kazanılanlar bir kez kutlanır.
 */
class BadgeStore(private val prefs: KeyValueStore) {

    fun earnedOn(badge: Badge): LocalDate? = prefs.getLong(key(badge))?.let { LocalDate.fromEpochDays(it) }

    /** Şu an koşulu sağlananları kaydeder; daha önce kaydedilmemiş olanları (yeni kazanılanları) döner. */
    fun record(current: Set<Badge>, today: LocalDate): List<Badge> =
        current.filter { earnedOn(it) == null }.onEach { prefs.putLong(key(it), today.toEpochDays()) }

    fun states(input: BadgeInputs): List<BadgeState> = Badge.entries.map { badge ->
        val earned = earnedOn(badge)
        val (now, goal) = Badges.target(badge, input)
        BadgeState(badge, earned, if (earned == null && goal > 1) now to goal else null)
    }

    private fun key(badge: Badge) = "badge_${badge.name}"
}
