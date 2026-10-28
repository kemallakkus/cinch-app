package com.cinch.ui.progress

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinch.shared.domain.GoalProgress
import com.cinch.shared.domain.SavingsDeposit
import com.cinch.shared.domain.repository.SavingsRepository
import com.cinch.shared.gamification.Badge
import com.cinch.shared.gamification.BadgeStore
import com.cinch.shared.gamification.ObserveGamification
import com.cinch.shared.gamification.Streaks
import com.cinch.shared.gamification.WeeklyLeftover
import com.cinch.shared.prefs.KeyValueStore
import com.cinch.shared.time.LocalClock
import com.cinch.ui.common.display
import com.cinch.ui.common.shortLabel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * @param leftover geçen haftadan artan; kumbaraya atıldıysa ya da "şimdi değil" dendiyse null.
 * @param savedTotal tüm hedeflerde biriken.
 */
@Immutable
data class ProgressStripState(
    val isLoading: Boolean = true,
    val streaks: Streaks = Streaks.NONE,
    val leftover: WeeklyLeftover? = null,
    val goals: List<GoalProgress> = emptyList(),
    val savedTotal: Long = 0,
)

sealed interface ProgressStripEvent {
    data object Resumed : ProgressStripEvent
    data object LeftoverDismissed : ProgressStripEvent
    data class LeftoverToGoal(val goalId: Long) : ProgressStripEvent
}

sealed interface ProgressStripEffect {
    /** Küçük kutlama (konfeti + mesaj): yeni rozet ya da kumbaraya atılan para. */
    data class Celebrate(val message: String) : ProgressStripEffect
}

/**
 * Ana ekrandaki oyunsu şerit: sınır serisi, kumbara ve "geçen hafta artanı kumbaraya at" önerisi. Yeni kazanılan
 * rozetleri burada kaydeder ve kutlar (bir kez).
 */
@OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest
class ProgressStripViewModel(
    gamification: ObserveGamification,
    private val badgeStore: BadgeStore,
    private val savings: SavingsRepository,
    private val prefs: KeyValueStore,
    private val clock: LocalClock,
) : ViewModel() {

    private val today = MutableStateFlow(clock.today())

    /** Bu oturumda elle kapatılan/kullanılan artan haftaları (tercih kaydı da yazılır). */
    private val handled = MutableStateFlow(setOf<LocalDate>())

    private val _effect = Channel<ProgressStripEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    private val source = today.flatMapLatest { gamification(it) }

    val state: StateFlow<ProgressStripState> = combine(source, handled) { g, done ->
        ProgressStripState(
            isLoading = false,
            streaks = g.streaks,
            leftover = g.leftover?.takeIf { it.weekStart !in done && !isHandled(it.weekStart) },
            goals = g.goals,
            savedTotal = g.goals.sumOf { it.saved.minor },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressStripState())

    init {
        // Yeni rozetler kaydedilip bir kez kutlanır.
        viewModelScope.launch {
            source.collect { g ->
                val fresh = badgeStore.record(g.earnedNow, clock.today())
                if (fresh.isNotEmpty()) _effect.send(ProgressStripEffect.Celebrate(badgeMessage(fresh)))
            }
        }
    }

    fun onEvent(event: ProgressStripEvent) {
        when (event) {
            ProgressStripEvent.Resumed -> today.value = clock.today()
            ProgressStripEvent.LeftoverDismissed -> state.value.leftover?.let { markHandled(it.weekStart) }
            is ProgressStripEvent.LeftoverToGoal -> toGoal(event.goalId)
        }
    }

    private fun toGoal(goalId: Long) {
        val leftover = state.value.leftover ?: return
        val goal = state.value.goals.firstOrNull { it.goal.id == goalId }?.goal ?: return
        markHandled(leftover.weekStart)
        viewModelScope.launch {
            savings.addDeposit(
                SavingsDeposit(
                    goalId = goalId,
                    amount = leftover.amount,
                    date = clock.today(),
                    note = "Haftalık artan · ${leftover.weekStart.shortLabel()} haftası",
                ),
            )
            _effect.send(ProgressStripEffect.Celebrate("🐷 ${leftover.amount.display()} ${goal.emoji} ${goal.name} kumbarasına atıldı"))
        }
    }

    private fun markHandled(weekStart: LocalDate) {
        prefs.putBoolean(key(weekStart), true)
        handled.value = handled.value + weekStart
    }

    private fun isHandled(weekStart: LocalDate) = prefs.getBoolean(key(weekStart))

    private fun key(weekStart: LocalDate) = "leftover_done_${weekStart.toEpochDays()}"

    private fun badgeMessage(fresh: List<Badge>): String =
        if (fresh.size == 1) "${fresh.single().emoji} Yeni rozet: ${fresh.single().title}!"
        else "🏅 ${fresh.size} yeni rozet kazandın: ${fresh.joinToString { it.title }}"
}
