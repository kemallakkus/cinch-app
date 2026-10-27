package com.cinch.notifications

import com.cinch.shared.coach.CoachNotificationPlanner
import com.cinch.shared.domain.repository.BudgetRepository
import com.cinch.shared.domain.repository.CardRepository
import com.cinch.shared.domain.repository.ExpenseRepository
import com.cinch.shared.time.LocalClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

/**
 * Bekleyen bildirimleri koç planıyla eşit tutar. Uygulama ömrü boyunca yaşar: bütçe, kart ya da harcama
 * değişince ve uygulama öne gelince planı yeniden kurar.
 *
 * Eşitleme farka göre yapılır: plandan düşen iptal edilir, eksik eklenir, aynı kalana dokunulmaz.
 * Böylece Android'de o an çalışmakta olan bildirim işi iptal edilmez.
 */
@OptIn(FlowPreview::class) // debounce
class CoachNotificationSync(
    private val planner: CoachNotificationPlanner,
    private val scheduler: NotificationScheduler,
    private val budgets: BudgetRepository,
    private val expenses: ExpenseRepository,
    private val cards: CardRepository,
    private val clock: LocalClock,
    private val scope: CoroutineScope,
) {

    private val refreshRequests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val mutex = Mutex()

    /** Değişiklikleri izlemeye başlar. Uygulama açılışında bir kez çağrılır. */
    fun start() {
        val today = clock.today()
        val changes = merge(
            budgets.observe().map { },
            cards.observeAll().map { },
            // Aralığın önemi yok: SQLDelight tablo değişince sorguyu yeniden çalıştırıp yayar.
            expenses.observeBetween(today.minus(DatePeriod(days = 60)), today.plus(DatePeriod(days = 60))).map { },
            refreshRequests,
        )
        scope.launch {
            // Açılıştaki ilk yayınlar ve art arda kayıtlar tek eşitlemeye iner.
            changes.debounce(DEBOUNCE).collect {
                try {
                    sync()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Zamanlayıcı hatası uygulamayı düşürmesin; bir sonraki değişiklikte yeniden denenir.
                }
            }
        }
    }

    /** Uygulama öne gelince çağrılır: gün değişmiş olabilir, 7 günlük ufuk ileri kayar. */
    fun refresh() {
        refreshRequests.tryEmit(Unit)
    }

    /** Android'de periyodik iş de bunu çağırır; kilit, açılıştaki eşitlemeyle çakışmayı önler. */
    suspend fun sync() {
        mutex.withLock {
            val desired = planner.upcoming().associateBy { it.scheduleKey() }
            val pending = scheduler.pendingKeys()

            val stale = pending - desired.keys
            if (stale.isNotEmpty()) scheduler.cancel(stale)

            val missing = desired.filterKeys { it !in pending }.values.toList()
            if (missing.isNotEmpty()) scheduler.schedule(missing)
        }
    }

    private companion object {
        val DEBOUNCE = 500.milliseconds
    }
}
