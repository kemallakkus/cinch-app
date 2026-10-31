package com.cinch.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Uygulamayı dışarıdan açan bağlantılar: widget ("+ Harcama" → [QUICK_ADD]) ve bildirimler (kumbara bildirimi →
 * [SAVINGS]). Platform tarafı [open] ile bırakır; hızlı kayıt uygulamanın üstünde açılır, diğerlerini ana sekme
 * yapısı ilgili sekmeye ve ekrana çevirip [consume] eder.
 */
object DeepLinks {
    const val QUICK_ADD = "cinch://quick-add"
    const val HOME = "cinch://home"
    const val MONTH = "cinch://month"
    const val CARDS = "cinch://cards"
    const val SETTINGS = "cinch://settings"
    const val SAVINGS = "cinch://savings"
    const val ACHIEVEMENTS = "cinch://achievements"
    const val INSIGHTS = "cinch://insights"
    const val SUBSCRIPTIONS = "cinch://subscriptions"
    const val RECAP = "cinch://recap"

    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun open(url: String) {
        _pending.value = url
    }

    fun consume() {
        _pending.value = null
    }
}

/** Bağlantının açtığı sekme ve (varsa) o sekmenin kökünün üstüne açılan ekran. */
data class LinkTarget(val tab: TopTab, val route: CinchRoute? = null)

/** Bilinmeyen bağlantı için null: uygulamanın açılması yeter. Hızlı kayıt sekme değil, katman: burada yok. */
fun linkTarget(url: String): LinkTarget? = when (url) {
    DeepLinks.HOME -> LinkTarget(TopTab.TODAY)
    DeepLinks.MONTH -> LinkTarget(TopTab.MONTH)
    DeepLinks.CARDS -> LinkTarget(TopTab.CARDS)
    DeepLinks.SETTINGS -> LinkTarget(TopTab.SETTINGS)
    DeepLinks.SAVINGS -> LinkTarget(TopTab.TODAY, SavingsRoute)
    DeepLinks.ACHIEVEMENTS -> LinkTarget(TopTab.TODAY, AchievementsRoute)
    DeepLinks.INSIGHTS -> LinkTarget(TopTab.MONTH, InsightsRoute())
    DeepLinks.SUBSCRIPTIONS -> LinkTarget(TopTab.MONTH, SubscriptionsRoute)
    DeepLinks.RECAP -> LinkTarget(TopTab.MONTH, MonthRecapRoute())
    else -> null
}
