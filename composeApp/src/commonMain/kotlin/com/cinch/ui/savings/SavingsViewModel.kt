package com.cinch.ui.savings

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinch.shared.domain.GoalProgress
import com.cinch.shared.domain.SavingsDeposit
import com.cinch.shared.domain.SavingsGoal
import com.cinch.shared.domain.repository.SavingsRepository
import com.cinch.shared.gamification.ObserveGamification
import com.cinch.shared.gamification.WeeklyLeftover
import com.cinch.shared.money.Currency
import com.cinch.shared.money.MoneyInput
import com.cinch.shared.time.LocalClock
import com.cinch.ui.common.toInputText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Açık pencere: hedef ekleme/düzenleme, para ekleme/çekme, hedefi silme. */
sealed interface SavingsDialog {
    /** [goal] null = yeni hedef. */
    data class EditGoal(val goal: SavingsGoal?) : SavingsDialog
    data class Move(val goal: SavingsGoal, val withdraw: Boolean) : SavingsDialog
    data class DeleteGoal(val goal: SavingsGoal) : SavingsDialog
}

/**
 * @param leftover geçen haftadan artan: para ekleme penceresinde "Geçen haftanın artanı" önerisi.
 * @param emoji hedef penceresinde seçili simge.
 */
@Immutable
data class SavingsState(
    val isLoading: Boolean = true,
    val goals: List<GoalProgress> = emptyList(),
    val leftover: WeeklyLeftover? = null,
    val dialog: SavingsDialog? = null,
    val name: TextFieldState = TextFieldState(),
    val target: TextFieldState = TextFieldState(),
    val emoji: String = DEFAULT_EMOJI,
    val amount: TextFieldState = TextFieldState(),
    val nameError: Boolean = false,
    val amountError: Boolean = false,
) {
    val total: Long get() = goals.sumOf { it.saved.minor }

    companion object {
        const val DEFAULT_EMOJI = "🐷"
        val EMOJIS = listOf("🐷", "🏖️", "✈️", "🚗", "🏠", "📱", "🎓", "💍", "🎁", "🛟")
    }
}

sealed interface SavingsEvent {
    data object AddGoalClicked : SavingsEvent
    data class EditGoalClicked(val goal: SavingsGoal) : SavingsEvent
    data class EmojiPicked(val emoji: String) : SavingsEvent
    data object GoalSaved : SavingsEvent
    data class DeleteGoalClicked(val goal: SavingsGoal) : SavingsEvent
    data object DeleteConfirmed : SavingsEvent
    data class AddMoneyClicked(val goal: SavingsGoal) : SavingsEvent
    data class WithdrawClicked(val goal: SavingsGoal) : SavingsEvent
    data object UseLeftover : SavingsEvent
    data object MoveConfirmed : SavingsEvent
    data class DepositDeleted(val deposit: SavingsDeposit) : SavingsEvent
    data object DialogDismissed : SavingsEvent
}

sealed interface SavingsEffect {
    /** Hedef bu eklemeyle doldu: konfeti. */
    data class GoalReached(val name: String) : SavingsEffect
}

/** Kumbara: hedefler, para ekleme/çekme. Para bankadan çıkmaz; ayrılan tutar "Bu ay elinde kalan"dan düşer. */
class SavingsViewModel(
    private val savings: SavingsRepository,
    gamification: ObserveGamification,
    private val clock: LocalClock,
) : ViewModel() {

    private data class Ui(
        val dialog: SavingsDialog? = null,
        val emoji: String = SavingsState.DEFAULT_EMOJI,
        val nameError: Boolean = false,
        val amountError: Boolean = false,
    )

    private val ui = MutableStateFlow(Ui())
    private val name = TextFieldState()
    private val target = TextFieldState()
    private val amount = TextFieldState()

    val state: StateFlow<SavingsState> = combine(gamification(clock.today()), ui) { g, ui ->
        SavingsState(
            isLoading = false,
            goals = g.goals,
            leftover = g.leftover,
            dialog = ui.dialog,
            name = name,
            target = target,
            emoji = ui.emoji,
            amount = amount,
            nameError = ui.nameError,
            amountError = ui.amountError,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SavingsState(name = name, target = target, amount = amount))

    private val _effect = Channel<SavingsEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    init {
        viewModelScope.launch { snapshotFlow { name.text.toString() }.collect { ui.update { it.copy(nameError = false) } } }
        viewModelScope.launch { snapshotFlow { amount.text.toString() + target.text }.collect { ui.update { it.copy(amountError = false) } } }
    }

    fun onEvent(event: SavingsEvent) {
        when (event) {
            SavingsEvent.AddGoalClicked -> openGoal(null)
            is SavingsEvent.EditGoalClicked -> openGoal(event.goal)
            is SavingsEvent.EmojiPicked -> ui.update { it.copy(emoji = event.emoji) }
            SavingsEvent.GoalSaved -> saveGoal()
            is SavingsEvent.DeleteGoalClicked -> ui.update { it.copy(dialog = SavingsDialog.DeleteGoal(event.goal)) }
            SavingsEvent.DeleteConfirmed -> deleteGoal()
            is SavingsEvent.AddMoneyClicked -> openMove(event.goal, withdraw = false)
            is SavingsEvent.WithdrawClicked -> openMove(event.goal, withdraw = true)
            SavingsEvent.UseLeftover -> state.value.leftover?.let { amount.setTextAndPlaceCursorAtEnd(it.amount.toInputText()) }
            SavingsEvent.MoveConfirmed -> move()
            is SavingsEvent.DepositDeleted -> viewModelScope.launch { savings.deleteDeposit(event.deposit.id) }
            SavingsEvent.DialogDismissed -> ui.update { it.copy(dialog = null) }
        }
    }

    private fun openGoal(goal: SavingsGoal?) {
        name.setTextAndPlaceCursorAtEnd(goal?.name.orEmpty())
        target.setTextAndPlaceCursorAtEnd(goal?.target?.toInputText().orEmpty())
        ui.update {
            it.copy(dialog = SavingsDialog.EditGoal(goal), emoji = goal?.emoji ?: SavingsState.DEFAULT_EMOJI, nameError = false, amountError = false)
        }
    }

    private fun openMove(goal: SavingsGoal, withdraw: Boolean) {
        amount.clearText()
        ui.update { it.copy(dialog = SavingsDialog.Move(goal, withdraw), amountError = false) }
    }

    private fun saveGoal() {
        val dialog = ui.value.dialog as? SavingsDialog.EditGoal ?: return
        val goalName = name.text.toString().trim()
        val targetMoney = MoneyInput.parse(target.text.toString(), Currency.TRY)?.takeIf { !it.isZero && !it.isNegative }
        if (goalName.isEmpty() || targetMoney == null) {
            ui.update { it.copy(nameError = goalName.isEmpty(), amountError = targetMoney == null) }
            return
        }
        ui.update { it.copy(dialog = null) }
        viewModelScope.launch {
            val existing = dialog.goal
            if (existing == null) {
                savings.addGoal(SavingsGoal(name = goalName, emoji = ui.value.emoji, target = targetMoney, createdOn = clock.today()))
            } else {
                savings.updateGoal(existing.copy(name = goalName, emoji = ui.value.emoji, target = targetMoney))
            }
        }
    }

    private fun move() {
        val dialog = ui.value.dialog as? SavingsDialog.Move ?: return
        val money = MoneyInput.parse(amount.text.toString(), dialog.goal.target.currency)?.takeIf { !it.isZero && !it.isNegative }
        val progress = state.value.goals.firstOrNull { it.goal.id == dialog.goal.id }
        // Kumbarada olandan fazlası çekilemez.
        if (money == null || (dialog.withdraw && progress != null && money.minor > progress.saved.minor)) {
            ui.update { it.copy(amountError = true) }
            return
        }
        ui.update { it.copy(dialog = null) }
        val wasReached = progress?.isReached == true
        val leftover = state.value.leftover
        viewModelScope.launch {
            savings.addDeposit(
                SavingsDeposit(
                    goalId = dialog.goal.id,
                    amount = if (dialog.withdraw) -money else money,
                    date = clock.today(),
                    note = when {
                        dialog.withdraw -> "Çekildi"
                        leftover != null && leftover.amount == money -> "Haftalık artan"
                        else -> ""
                    },
                ),
            )
            val nowSaved = (progress?.saved?.minor ?: 0) + money.minor
            if (!dialog.withdraw && !wasReached && nowSaved >= dialog.goal.target.minor) {
                _effect.send(SavingsEffect.GoalReached(dialog.goal.name))
            }
        }
    }

    private fun deleteGoal() {
        val dialog = ui.value.dialog as? SavingsDialog.DeleteGoal ?: return
        ui.update { it.copy(dialog = null) }
        viewModelScope.launch { savings.deleteGoal(dialog.goal.id) }
    }
}
