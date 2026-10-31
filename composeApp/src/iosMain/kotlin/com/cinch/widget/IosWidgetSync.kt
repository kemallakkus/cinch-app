package com.cinch.widget

import com.cinch.di.StartKoin
import com.cinch.shared.budget.ObserveBudgetStatus
import com.cinch.shared.domain.repository.BudgetRepository
import com.cinch.shared.domain.repository.CardRepository
import com.cinch.shared.domain.repository.ExpenseRepository
import com.cinch.shared.gamification.ObserveGamification
import com.cinch.shared.parser.QuickEntryParser
import com.cinch.shared.time.LocalClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.koin.core.Koin
import org.koin.core.qualifier.named
import org.koin.mp.KoinPlatform
import platform.Foundation.NSUserDefaults

/**
 * iOS widget'ına veri: WidgetKit eklentisi Kotlin'i çalıştırmaz, uygulamanın App Group'a yazdığı anlık görüntüyü
 * okur. Bütçe ya da harcama değişince görüntü yazılır ve Swift'in verdiği [start] geri çağrısıyla
 * (`WidgetCenter.reloadAllTimelines`) widget yenilenir.
 */
object IosWidgetSync {

    /** Uygulama ve widget eklentisinin ortak alanı; Xcode'da iki hedefte de tanımlı. */
    const val APP_GROUP = "group.com.cinch.app"

    private const val DEBOUNCE_MS = 1_000L

    private var reload: (() -> Unit)? = null
    private var job: Job? = null

    /** Uygulama açılışında bir kez (Swift: `iOSApp.init`). */
    @OptIn(FlowPreview::class)
    fun start(reload: () -> Unit) {
        this.reload = reload
        if (job != null) return
        val koin = KoinPlatform.getKoinOrNull() ?: return
        val scope = koin.get<CoroutineScope>(named("appScope"))
        job = scope.launch {
            widgetChanges(koin.get<BudgetRepository>(), koin.get<ExpenseRepository>())
                .debounce(DEBOUNCE_MS)
                .collect { publish() }
        }
    }

    /** Güncel görüntüyü yazar ve widget'ı yeniler. */
    suspend fun publish() {
        val koin = KoinPlatform.getKoinOrNull() ?: return
        write(loader(koin).invoke())
        reload?.invoke()
    }

    private fun loader(koin: Koin) =
        LoadTodaySnapshot(koin.get<ObserveBudgetStatus>(), koin.get<ObserveGamification>(), koin.get<LocalClock>())

    private fun write(snapshot: TodaySnapshot) {
        val defaults = NSUserDefaults(suiteName = APP_GROUP)
        defaults.setBool(snapshot.hasBudget, forKey = "widget.hasBudget")
        defaults.setObject(snapshot.day?.toString(), forKey = "widget.day")
        defaults.setObject(snapshot.todayText, forKey = "widget.todayText")
        defaults.setBool(snapshot.over, forKey = "widget.over")
        defaults.setObject(snapshot.weekText, forKey = "widget.weekText")
        defaults.setInteger(snapshot.streak.toLong(), forKey = "widget.streak")
    }
}

/**
 * Siri / Kısayollar / Eylem düğmesi: "Cinch'e harcama ekle" → "195 market". Uygulama açılmadan kaydeder ve
 * söylenecek cümleyi döner. Swift'ten ana iş parçacığında çağrılır (`@MainActor`).
 */
object QuickAddBridge {

    suspend fun save(text: String): String {
        if (KoinPlatform.getKoinOrNull() == null) StartKoin.doInitKoin()
        val koin = KoinPlatform.getKoin()
        val saved = SaveQuickEntry(
            parser = koin.get<QuickEntryParser>(),
            expenses = koin.get<ExpenseRepository>(),
            cards = koin.get<CardRepository>(),
            clock = koin.get<LocalClock>(),
        ).invoke(text)
        if (saved != null) IosWidgetSync.publish()
        return SaveQuickEntry.message(text, saved)
    }
}
